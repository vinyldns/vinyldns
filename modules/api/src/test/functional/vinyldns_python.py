import json
import logging
import time
import traceback
from json import JSONDecodeError
from typing import Iterable
from urllib.parse import urlparse, urlsplit, parse_qs, urljoin

import requests
from hamcrest import *
from requests.adapters import HTTPAdapter, Retry

from aws_request_signer import AwsSigV4RequestSigner

logger = logging.getLogger(__name__)

__all__ = ["VinylDNSClient", "MAX_RETRIES", "RETRY_WAIT"]

MAX_RETRIES = 40
RETRY_WAIT = 0.05


class VinylDNSClient(object):

    def __init__(self, url, access_key, secret_key):
        self.index_url = url
        self.headers = {
            "Accept": "application/json, text/plain",
            "Content-Type": "application/json"
        }
        self.created_zones = []
        self.generated_zones = []
        self.created_groups = []
        self.signer = AwsSigV4RequestSigner(self.index_url, access_key, secret_key)
        self.session = self.requests_retry_session()
        self.session_not_found_ok = self.requests_retry_not_found_ok_session()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.tear_down()

    def clear_groups(self):
        for group_id in self.created_groups:
            self.delete_group(group_id)

    def clear_zones(self):
        self.abandon_zones(self.created_zones)

    def clear_generate_zones(self):
        self.abandon_generated_zones(self.generated_zones)

    def tear_down(self):
        self.session.close()
        self.session_not_found_ok.close()

    def requests_retry_not_found_ok_session(self, retries=20, backoff_factor=0.1, status_forcelist=(500, 502, 504),
                                            session=None):
        session = session or requests.Session()
        retry = Retry(
            total=retries,
            read=retries,
            connect=retries,
            backoff_factor=backoff_factor,
            status_forcelist=status_forcelist,
        )
        adapter = HTTPAdapter(max_retries=retry)
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        return session

    def requests_retry_session(self, retries=20, backoff_factor=0.1, status_forcelist=(500, 502, 504), session=None):
        session = session or requests.Session()
        retry = Retry(
            total=retries,
            read=retries,
            connect=retries,
            backoff_factor=backoff_factor,
            status_forcelist=status_forcelist,
        )
        adapter = HTTPAdapter(max_retries=retry, pool_connections=100, pool_maxsize=100)
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        return session

    def make_request(self, url, method="GET", headers=None, body_string=None, sign_request=True, not_found_ok=False,
                     **kwargs):

        # pull out status or None
        status_code = kwargs.pop("status", None)

        # remove retries arg if provided
        kwargs.pop("retries", None)

        path = urlparse(url).path

        # we must parse the query string so we can provide it if it exists so that we can pass it to the
        # build_vinyldns_request so that it can be properly included in the AWS signing...
        query = parse_qs(urlsplit(url).query)

        if query:
            # the problem with parse_qs is that it will return a list for ALL params, even if they are a single value
            # we need to essentially flatten the params if a param has only one value
            query = dict((k, v if len(v) > 1 else v[0])
                         for k, v in query.items())

        if sign_request:
            signed_headers, signed_body = self.sign_request(method, path, body_string, query,
                                                            with_headers=headers or {}, **kwargs)
        else:
            signed_headers = headers or {}
            signed_body = body_string

        if not_found_ok:
            response = self.session_not_found_ok.request(method, url, data=signed_body, headers=signed_headers,
                                                         **kwargs)
        else:
            response = self.session.request(method, url, data=signed_body, headers=signed_headers, **kwargs)

        if status_code is not None:
            if isinstance(status_code, Iterable):
                assert_that(response.status_code, is_in(status_code), response.text)
            else:
                assert_that(response.status_code, is_(status_code), response.text)

        try:
            return response.status_code, response.json()
        except JSONDecodeError:
            return response.status_code, response.text
        except Exception:
            traceback.print_exc()
            raise

    def ping(self):
        """
        Simple ping request
        :return: the content of the response, which should be PONG
        """
        url = urljoin(self.index_url, "/ping")

        response, data = self.make_request(url)
        return data

    def get_status(self):
        """
        Gets processing status
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/status")

        response, data = self.make_request(url)

        return data

    def post_status(self, status):
        """
        Update processing status
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/status?processingDisabled={}".format(status))
        response, data = self.make_request(url, "POST", self.headers)

        return data

    def color(self):
        """
        Gets the current color for the application
        :return: the content of the response, which should be "blue" or "green"
        """
        url = urljoin(self.index_url, "/color")
        response, data = self.make_request(url)
        return data

    def health(self):
        """
        Checks the health of the app, asserts that a 200 should be returned, otherwise
        this will fail
        """
        url = urljoin(self.index_url, "/health")
        self.make_request(url, sign_request=False)

    def create_group(self, group, **kwargs):
        """
        Creates a new group
        :param group: A group dictionary that can be serialized to json
        :return: the content of the response, which should be a group json
        """
        url = urljoin(self.index_url, "/groups")
        response, data = self.make_request(url, "POST", self.headers, json.dumps(group), **kwargs)

        if type(data) != str and "id" in data:
            self.created_groups.append(data["id"])

        return data

    def get_group(self, group_id, **kwargs):
        """
        Gets a group
        :param group_id: Id of the group to get
        :return: the group json
        """
        url = urljoin(self.index_url, "/groups/" + group_id)
        response, data = self.make_request(url, "GET", self.headers, **kwargs)

        return data

    def delete_group(self, group_id, **kwargs):
        """
        Deletes a group
        :param group_id: Id of the group to delete
        :return: the group json
        """
        url = urljoin(self.index_url, "/groups/" + group_id)
        response, data = self.make_request(url, "DELETE", self.headers, not_found_ok=True, **kwargs)

        return data

    def update_group(self, group_id, group, **kwargs):
        """
        Update an existing group
        :param group_id: The id of the group being updated
        :param group: A group dictionary that can be serialized to json
        :return: the content of the response, which should be a group json
        """
        url = urljoin(self.index_url, "/groups/{0}".format(group_id))
        response, data = self.make_request(url, "PUT", self.headers, json.dumps(group), not_found_ok=True, **kwargs)

        return data

    def list_my_groups(self, group_name_filter=None, start_from=None, max_items=100, ignore_access=False, **kwargs):
        """
        Retrieves my groups
        :param start_from: the start key of the page
        :param max_items: the page limit
        :param group_name_filter: only returns groups whose names contain filter string
        :param ignore_access: determines if groups should be retrieved based on requester's membership
        :return: the content of the response
        """
        args = []
        if group_name_filter:
            args.append("groupNameFilter={0}".format(group_name_filter))
        if start_from:
            args.append("startFrom={0}".format(start_from))
        if max_items is not None:
            args.append("maxItems={0}".format(max_items))
        if ignore_access is not False:
            args.append("ignoreAccess={0}".format(ignore_access))

        url = urljoin(self.index_url, "/groups") + "?" + "&".join(args)
        response, data = self.make_request(url, "GET", self.headers, **kwargs)

        return data

    def list_all_my_groups(self, group_name_filter=None, **kwargs):
        """
        Retrieves all my groups
        :param group_name_filter: only returns groups whose names contain filter string
        :return: the content of the response
        """
        groups = []
        args = []
        if group_name_filter:
            args.append("groupNameFilter={0}".format(group_name_filter))

        url = urljoin(self.index_url, "/groups") + "?" + "&".join(args)
        response, data = self.make_request(url, "GET", self.headers, **kwargs)

        groups.extend(data["groups"])

        while "nextId" in data:
            args = []

            if group_name_filter:
                args.append("groupNameFilter={0}".format(group_name_filter))
            if "nextId" in data:
                args.append("startFrom={0}".format(data["nextId"]))

            response, data = self.make_request(url, "GET", self.headers, **kwargs)
            groups.extend(data["groups"])

        return groups

    def list_members_group(self, group_id, start_from=None, max_items=None, **kwargs):
        """
        List the members of an existing group
        :param group_id: the Id of an existing group
        :param start_from: the Id a member of the group
        :param max_items: the max number of items to be returned
        :return: the json of the members
        """
        if start_from is None and max_items is None:
            url = urljoin(self.index_url, "/groups/{0}/members".format(group_id))
        elif start_from is None and max_items is not None:
            url = urljoin(self.index_url, "/groups/{0}/members?maxItems={1}".format(group_id, max_items))
        elif start_from is not None and max_items is None:
            url = urljoin(self.index_url, "/groups/{0}/members?startFrom={1}".format(group_id, start_from))
        elif start_from is not None and max_items is not None:
            url = urljoin(self.index_url, "/groups/{0}/members?startFrom={1}&maxItems={2}".format(group_id,
                                                                                                  start_from,
                                                                                                  max_items))

        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def list_group_admins(self, group_id, **kwargs):
        """
        returns the group admins
        :param group_id: the Id of the group
        :return: the user info of the admins
        """
        url = urljoin(self.index_url, "/groups/{0}/admins".format(group_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_group_changes(self, group_id, start_from=None, max_items=None, **kwargs):
        """
        List the changes of an existing group
        :param group_id: the Id of an existing group
        :param start_from: the Id a group change
        :param max_items: the max number of items to be returned
        :return: the json of the members
        """
        if start_from is None and max_items is None:
            url = urljoin(self.index_url, "/groups/{0}/activity".format(group_id))
        elif start_from is None and max_items is not None:
            url = urljoin(self.index_url, "/groups/{0}/activity?maxItems={1}".format(group_id, max_items))
        elif start_from is not None and max_items is None:
            url = urljoin(self.index_url, "/groups/{0}/activity?startFrom={1}".format(group_id, start_from))
        elif start_from is not None and max_items is not None:
            url = urljoin(self.index_url, "/groups/{0}/activity?startFrom={1}&maxItems={2}".format(group_id,
                                                                                                   start_from,
                                                                                                   max_items))

        response, data = self.make_request(url, "GET", self.headers, **kwargs)

        return data

    def generate_zone(self, zone, **kwargs):
        """
        Creates a new zone with the given name and email
        :param zone: the zone to be created
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/generate")
        response, data = self.make_request(url, "POST", self.headers, json.dumps(zone), **kwargs)

        if type(data) != str and "zone" in data:
            self.created_zones.append(data["zone"]["id"])

        return data

    def delete_generated_zone(self, zone_id, **kwargs):
        """
        Deletes the zone for the given id
        :param zone_id: the id of the zone to be deleted
        :return: nothing, will fail if the status code was not expected
        """

        url = urljoin(self.index_url, "/zones/generate/{0}".format(zone_id))
        response, data = self.make_request(url, "DELETE", self.headers, not_found_ok=True, **kwargs)

        return data

    def create_zone(self, zone, **kwargs):
        """
        Creates a new zone with the given name and email
        :param zone: the zone to be created
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones")
        response, data = self.make_request(url, "POST", self.headers, json.dumps(zone), **kwargs)

        if type(data) != str and "zone" in data:
            self.created_zones.append(data["zone"]["id"])

        return data

    def update_zone(self, zone, **kwargs):
        """
        Updates a zone
        :param zone: the zone to be created
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}".format(zone["id"]))
        response, data = self.make_request(url, "PUT", self.headers, json.dumps(zone), not_found_ok=True, **kwargs)

        return data

    def update_generate_zone(self, zone, **kwargs):
        """
         Updates a zone
         :param zone: the zone to be update
         :return: the content of the response
         """
        url = urljoin(self.index_url, "/zones/generate")
        response, data = self.make_request(url, "PUT", self.headers, json.dumps(zone), not_found_ok=True, **kwargs)
        return data

    def sync_zone(self, zone_id, **kwargs):
        """
        Syncs a zone
        :param zone_id: the id of the zone to be updated
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}/sync".format(zone_id))
        response, data = self.make_request(url, "POST", self.headers, not_found_ok=True, **kwargs)
        return data

    def delete_zone(self, zone_id, **kwargs):
        """
        Deletes the zone for the given id
        :param zone_id: the id of the zone to be deleted
        :return: nothing, will fail if the status code was not expected
        """

        url = urljoin(self.index_url, "/zones/{0}".format(zone_id))
        response, data = self.make_request(url, "DELETE", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_zone(self, zone_id, **kwargs):
        """
        Gets a zone for the given zone id
        :param zone_id: the id of the zone to retrieve
        :return: the zone, or will 404 if not found
        """
        url = urljoin(self.index_url, "/zones/{0}".format(zone_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_generate_zone(self, zone_id, **kwargs):
        """
        Gets a zone for the given zone id
        :param zone_id: the id of the zone to retrieve
        :return: the zone, or will 404 if not found
        """
        url = urljoin(self.index_url, "/zones/generate/id/{0}".format(zone_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_common_zone_details(self, zone_id, **kwargs):
        """
        Gets common zone details which can be seen by all users for the given zone id
        :param zone_id: the id of the zone to retrieve
        :return: the zone, or will 404 if not found
        """
        url = urljoin(self.index_url, "/zones/{0}/details".format(zone_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_zone_by_name(self, zone_name, **kwargs):
        """
        Gets a zone for the given zone name
        :param zone_name: the name of the zone to retrieve
        :return: the zone, or will 404 if not found
        """
        url = urljoin(self.index_url, "/zones/name/{0}".format(zone_name))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_generate_zone_by_name(self, zone_name, **kwargs):
        """
        Gets a zone for the given zone name
        :param zone_name: the name of the zone to retrieve
        :return: the zone, or will 404 if not found
        """
        url = urljoin(self.index_url, "/zones/generate/name/{0}".format(zone_name))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_backend_ids(self, **kwargs):
        """
        Gets list of configured backend ids
        :return: list of strings
        """
        url = urljoin(self.index_url, "/zones/backendids")
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_nameservers(self, **kwargs):
        """
        Gets list of configured backend ids
        :return: list of strings
        """
        url = urljoin(self.index_url, "/zones/generate/nameservers")
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def get_allowed_dns_provider(self, **kwargs):
        """
        Gets list of configured backend ids
        :return: list of strings
        """
        url = urljoin(self.index_url, "/zones/generate/allowedDNSProviders")
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)

        return data

    def list_zone_changes(self, zone_id, start_from=None, max_items=None, **kwargs):
        """
        Gets the zone changes for the given zone id
        :param zone_id: the id of the zone to retrieve
        :param start_from: the start key of the page
        :param max_items: the page limit
        :return: the zone, or will 404 if not found
        """
        args = []
        if start_from:
            args.append("startFrom={0}".format(start_from))
        if max_items is not None:
            args.append("maxItems={0}".format(max_items))
        url = urljoin(self.index_url, "/zones/{0}/changes".format(zone_id)) + "?" + "&".join(args)

        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)
        return data

    def list_recordset_changes(self, zone_id, start_from=None, max_items=None, **kwargs):
        """
        Gets the recordset changes for the given zone id
        :param zone_id: the id of the zone to retrieve
        :param start_from: the start key of the page
        :param max_items: the page limit
        :return: the zone, or will 404 if not found
        """
        args = []
        if start_from:
            args.append("startFrom={0}".format(start_from))
        if max_items is not None:
            args.append("maxItems={0}".format(max_items))
        url = urljoin(self.index_url, "/zones/{0}/recordsetchanges".format(zone_id)) + "?" + "&".join(args)

        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)
        return data

    def list_recordset_change_history(self, zone_id, fqdn, record_type, start_from=None, max_items=None, **kwargs):
        """
        Gets the record's change history for the given zone, record fqdn and record type
        :param zone_id: the id of the zone to retrieve
        :param fqdn: the record's fqdn
        :param record_type: the record's type
        :param start_from: the start key of the page
        :param max_items: the page limit
        :return: the zone, or will 404 if not found
        """
        args = []
        if start_from:
            args.append("startFrom={0}".format(start_from))
        if max_items is not None:
            args.append("maxItems={0}".format(max_items))
        args.append("zoneId={0}".format(zone_id))
        args.append("fqdn={0}".format(fqdn))
        args.append("recordType={0}".format(record_type))
        url = urljoin(self.index_url, "recordsetchange/history") + "?" + "&".join(args)

        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, **kwargs)
        return data

    def list_zones(self, name_filter=None, start_from=None, max_items=None, search_by_admin_group=False,
                   ignore_access=False, **kwargs):
        """
        Gets a list of zones that currently exist
        :return: a list of zones
        """
        url = urljoin(self.index_url, "/zones")

        query = []
        if name_filter:
            query.append("nameFilter=" + name_filter)

        if start_from:
            query.append("startFrom=" + str(start_from))

        if max_items:
            query.append("maxItems=" + str(max_items))

        if search_by_admin_group:
            query.append("searchByAdminGroup=" + str(search_by_admin_group))

        if ignore_access:
            query.append("ignoreAccess=" + str(ignore_access))

        if query:
            url = url + "?" + "&".join(query)

        response, data = self.make_request(url, "GET", self.headers, **kwargs)
        return data

    def list_generated_zones(self, name_filter=None, start_from=None, max_items=None, search_by_admin_group=False,
                   ignore_access=False, **kwargs):
        """
        Gets a list of zones that currently exist
        :return: a list of zones
        """
        url = urljoin(self.index_url, "/zones/generate/info")

        query = []
        if name_filter:
            query.append("nameFilter=" + name_filter)

        if start_from:
            query.append("startFrom=" + str(start_from))

        if max_items:
            query.append("maxItems=" + str(max_items))

        if search_by_admin_group:
            query.append("searchByAdminGroup=" + str(search_by_admin_group))

        if ignore_access:
            query.append("ignoreAccess=" + str(ignore_access))

        if query:
            url = url + "?" + "&".join(query)

        response, data = self.make_request(url, "GET", self.headers, **kwargs)
        return data

    def create_recordset(self, recordset, **kwargs):
        """
        Creates a new recordset
        :param recordset: the recordset to be created
        :return: the content of the response
        """
        if recordset and "name" in recordset:
            recordset["name"] = recordset["name"].replace("_", "-")

        url = urljoin(self.index_url, "/zones/{0}/recordsets".format(recordset["zoneId"]))
        response, data = self.make_request(url, "POST", self.headers, json.dumps(recordset), **kwargs)
        return data

    def delete_recordset(self, zone_id, rs_id, **kwargs):
        """
        Deletes an existing recordset
        :param zone_id: the zone id the recordset belongs to
        :param rs_id: the id of the recordset to be deleted
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}/recordsets/{1}".format(zone_id, rs_id))

        response, data = self.make_request(url, "DELETE", self.headers, not_found_ok=True, **kwargs)
        return data

    def update_recordset(self, recordset, **kwargs):
        """
        Deletes an existing recordset
        :param recordset: the recordset to be updated
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}/recordsets/{1}".format(recordset["zoneId"], recordset["id"]))

        response, data = self.make_request(url, "PUT", self.headers, json.dumps(recordset), not_found_ok=True, **kwargs)
        return data

    def get_recordset(self, zone_id, rs_id, **kwargs):
        """
        Gets an existing recordset
        :param zone_id: the zone id the recordset belongs to
        :param rs_id: the id of the recordset to be retrieved
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}/recordsets/{1}".format(zone_id, rs_id))

        response, data = self.make_request(url, "GET", self.headers, None, not_found_ok=True, **kwargs)
        return data

    def get_recordset_count(self, zone_id,**kwargs):
        """
        Get count of record set in managed records tab
        :param zone_id: the zone id the recordset belongs to
        :return: the value of count
        """
        url = urljoin(self.index_url, "/zones/{0}/recordsetcount".format(zone_id))

        response, data = self.make_request(url, "GET", self.headers, None, not_found_ok=True, **kwargs)
        return data

    def get_recordset_change(self, zone_id, rs_id, change_id, **kwargs):
        """
        Gets an existing recordset change
        :param zone_id: the zone id the recordset belongs to
        :param rs_id: the id of the recordset to be retrieved
        :param change_id: the id of the change to be retrieved
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}/recordsets/{1}/changes/{2}".format(zone_id, rs_id, change_id))

        response, data = self.make_request(url, "GET", self.headers, None, not_found_ok=True, **kwargs)
        return data

    def list_recordsets_by_zone(self, zone_id, start_from=None, max_items=None, record_name_filter=None,
                                record_type_filter=None, name_sort=None, **kwargs):
        """
        Retrieves all recordsets in a zone
        :param zone_id: the zone to retrieve
        :param start_from: the start key of the page
        :param max_items: the page limit
        :param record_name_filter: only returns recordsets whose names contain filter string
        :param record_type_filter: only returns recordsets whose type is included in the filter string
        :param name_sort: sort order by recordset name
        :return: the content of the response
        """
        args = []
        if start_from:
            args.append("startFrom={0}".format(start_from))
        if max_items is not None:
            args.append("maxItems={0}".format(max_items))
        if record_name_filter:
            args.append("recordNameFilter={0}".format(record_name_filter))
        if record_type_filter:
            args.append("recordTypeFilter={0}".format(record_type_filter))
        if name_sort:
            args.append("nameSort={0}".format(name_sort))

        url = urljoin(self.index_url, "/zones/{0}/recordsets".format(zone_id)) + "?" + "&".join(args)

        response, data = self.make_request(url, "GET", self.headers, **kwargs)
        return data

    def create_batch_change(self, batch_change_input, allow_manual_review=True, **kwargs):
        """
        Creates a new batch change
        :param batch_change_input: the batchchange to be created
        :param allow_manual_review: if true and manual review is enabled soft failures are treated as hard failures
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/batchrecordchanges")
        if allow_manual_review is not None:
            url = url + ("?" + "allowManualReview={0}".format(allow_manual_review))
        response, data = self.make_request(url, "POST", self.headers, json.dumps(batch_change_input), **kwargs)
        return data

    def get_batch_change(self, batch_change_id, **kwargs):
        """
        Gets an existing batch change
        :param batch_change_id: the unique identifier of the batchchange
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/batchrecordchanges/{0}".format(batch_change_id))
        response, data = self.make_request(url, "GET", self.headers, None, not_found_ok=True, **kwargs)
        return data

    def reject_batch_change(self, batch_change_id, reject_batch_change_input=None, **kwargs):
        """
        Rejects an existing batch change pending manual review
        :param batch_change_id: ID of the batch change to reject
        :param reject_batch_change_input: optional body for reject batch change request
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/batchrecordchanges/{0}/reject".format(batch_change_id))
        _, data = self.make_request(url, "POST", self.headers, json.dumps(reject_batch_change_input), **kwargs)
        return data

    def approve_batch_change(self, batch_change_id, approve_batch_change_input=None, **kwargs):
        """
        Approves an existing batch change pending manual review
        :param batch_change_id: ID of the batch change to approve
        :param approve_batch_change_input: optional body for approve batch change request
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/batchrecordchanges/{0}/approve".format(batch_change_id))
        _, data = self.make_request(url, "POST", self.headers, json.dumps(approve_batch_change_input), **kwargs)
        return data

    def cancel_batch_change(self, batch_change_id, **kwargs):
        """
        Cancels an existing batch change pending manual review
        :param batch_change_id: ID of the batch change to cancel
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/batchrecordchanges/{0}/cancel".format(batch_change_id))
        _, data = self.make_request(url, "POST", self.headers, **kwargs)
        return data

    def list_batch_change_summaries(self, start_from=None, max_items=None, ignore_access=False, approval_status=None,
                                    **kwargs):
        """
        Gets list of user's batch change summaries
        :return: the content of the response
        """
        args = []
        if start_from:
            args.append("startFrom={0}".format(start_from))
        if max_items is not None:
            args.append("maxItems={0}".format(max_items))
        if ignore_access:
            args.append("ignoreAccess={0}".format(ignore_access))
        if approval_status:
            args.append("approvalStatus={0}".format(approval_status))

        url = urljoin(self.index_url, "/zones/batchrecordchanges") + "?" + "&".join(args)

        response, data = self.make_request(url, "GET", self.headers, **kwargs)
        return data

    def add_zone_acl_rule_with_wait(self, zone_id, acl_rule, sign_request=True, **kwargs):
        """
        Puts an acl rule on the zone and waits for success
        :param zone_id: The id of the zone to attach the acl rule to
        :param acl_rule: The acl rule contents
        :param sign_request: An indicator if we should sign the request; useful for testing auth
        :return: the content of the response
        """
        rule = self.add_zone_acl_rule(zone_id, acl_rule, sign_request, **kwargs)
        self.wait_until_zone_change_status_synced(rule)

        return rule

    def add_zone_acl_rule(self, zone_id, acl_rule, sign_request=True, **kwargs):
        """
        Puts an acl rule on the zone
        :param zone_id: The id of the zone to attach the acl rule to
        :param acl_rule: The acl rule contents
        :param sign_request: An indicator if we should sign the request; useful for testing auth
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}/acl/rules".format(zone_id))
        response, data = self.make_request(url, "PUT", self.headers, json.dumps(acl_rule), sign_request=sign_request,
                                           **kwargs)

        return data

    def delete_zone_acl_rule_with_wait(self, zone_id, acl_rule, sign_request=True, **kwargs):
        """
        Deletes an acl rule from the zone and waits for success
        :param zone_id: The id of the zone to remove the acl from
        :param acl_rule: The acl rule to remove
        :param sign_request:  An indicator if we should sign the request; useful for testing auth
        :return: the content of the response
        """
        rule = self.delete_zone_acl_rule(zone_id, acl_rule, sign_request, **kwargs)
        self.wait_until_zone_change_status_synced(rule)

        return rule

    def delete_zone_acl_rule(self, zone_id, acl_rule, sign_request=True, **kwargs):
        """
        Deletes an acl rule from the zone
        :param zone_id: The id of the zone to remove the acl from
        :param acl_rule: The acl rule to remove
        :param sign_request:  An indicator if we should sign the request; useful for testing auth
        :return: the content of the response
        """
        url = urljoin(self.index_url, "/zones/{0}/acl/rules".format(zone_id))
        response, data = self.make_request(url, "DELETE", self.headers, json.dumps(acl_rule), sign_request=sign_request,
                                           **kwargs)

        return data

    def wait_until_recordset_deleted(self, zone_id, record_set_id, **kwargs):
        retries = MAX_RETRIES
        url = urljoin(self.index_url, "/zones/{0}/recordsets/{1}".format(zone_id, record_set_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)
        while response != 404 and retries > 0:
            url = urljoin(self.index_url, "/zones/{0}/recordsets/{1}".format(zone_id, record_set_id))
            response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)
            retries -= 1
            time.sleep(RETRY_WAIT)

        return response == 404

    def wait_until_zone_change_status_synced(self, zone_change):
        """
        Waits until the zone change status is Synced
        """
        # We can get a zone_change parameter from a 404 where the change is not a dict
        if type(zone_change) == str:
            return

        latest_change = zone_change
        retries = MAX_RETRIES

        while latest_change["status"] != "Synced" and latest_change["status"] != "Failed" and retries > 0:
            changes = self.list_zone_changes(zone_change["zone"]["id"])
            if "zoneChanges" in changes:
                matching_changes = [change for change in changes["zoneChanges"] if change["id"] == zone_change["id"]]
                if len(matching_changes) > 0:
                    latest_change = matching_changes[0]
            time.sleep(RETRY_WAIT)
            retries -= 1

        assert_that(latest_change["status"], is_("Synced"))

    def wait_until_zone_deleted(self, zone_id, **kwargs):
        """
        Waits a period of time for the zone deletion to complete.

        :param zone_id: the id of the zone that has been deleted.
        :param kw: Additional parameters for the http request
        :return: True when the zone deletion is complete False if the timeout expires
        """
        retries = MAX_RETRIES
        url = urljoin(self.index_url, "/zones/{0}".format(zone_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)
        while response != 404 and retries > 0:
            url = urljoin(self.index_url, "/zones/{0}".format(zone_id))
            response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)
            retries -= 1
            time.sleep(RETRY_WAIT)

        assert_that(response, is_(404))

    def wait_until_generated_zone_deleted(self, zone_id, **kwargs):
        """
        Waits a period of time for the zone deletion to complete.

        :param zone_id: the id of the zone that has been deleted.
        :param kw: Additional parameters for the http request
        :return: True when the zone deletion is complete False if the timeout expires
        """
        retries = MAX_RETRIES

        url = urljoin(self.index_url, "/zones/generate/id/{0}".format(zone_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)
        while response != 400 and retries > 0:
            url = urljoin(self.index_url, "/zones/generate/id/{0}".format(zone_id))
            response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)
            retries -= 1
            time.sleep(RETRY_WAIT)

        assert_that(response, is_(404))

    def wait_until_zone_active(self, zone_id):
        """
        Waits a period of time for the zone sync to complete.

        :param zone_id: the ID for the zone.
        """
        retries = MAX_RETRIES
        zone_request = self.get_zone(zone_id)

        while ("zone" not in zone_request or zone_request["zone"]["status"] != "Active") and retries > 0:
            time.sleep(RETRY_WAIT)
            retries -= 1
            zone_request = self.get_zone(zone_id)

        assert_that(zone_request["zone"]["status"], is_("Active"))

    def wait_until_generate_zone_active(self, zone_id):
        """
        Waits a period of time for the zone sync to complete.

        :param zone_id: the ID for the zone.
        """
        retries = MAX_RETRIES
        zone_request = self.get_generate_zone(zone_id)

        while ("zoneName" not in zone_request or zone_request["status"] != "Active") and retries > 0:
            time.sleep(RETRY_WAIT)
            retries -= 1
            zone_request = self.get_generate_zone(zone_id)

        assert_that(zone_request["status"], is_("Active"))

    def wait_until_recordset_exists(self, zone_id, record_set_id, **kwargs):
        """
        Waits a period of time for the record set creation to complete.

        :param zone_id: the id of the zone the record set lives in
        :param record_set_id: the id of the recordset that has been created.
        :param kw: Additional parameters for the http request
        :return: True when the recordset creation is complete False if the timeout expires
        """
        retries = MAX_RETRIES
        url = urljoin(self.index_url, "/zones/{0}/recordsets/{1}".format(zone_id, record_set_id))
        response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)
        while response != 200 and retries > 0:
            retries -= 1
            time.sleep(RETRY_WAIT)
            response, data = self.make_request(url, "GET", self.headers, not_found_ok=True, status=(200, 404), **kwargs)

        assert_that(response, equal_to(200), data)
        if response == 200:
            return data

        return response == 200

    def abandon_zones(self, zone_ids, **kwargs):
        # delete each zone
        for zone_id in zone_ids:
            self.delete_zone(zone_id, status=(202, 404))

        # Wait until each zone is gone
        for zone_id in zone_ids:
            self.wait_until_zone_deleted(zone_id)

    def abandon_generated_zones(self, zone_ids, **kwargs):
        # delete each zone
        for zone_id in zone_ids:
            self.delete_generated_zone(zone_id, status=(202, 404))

        # Wait until each zone is gone
        for zone_id in zone_ids:
            self.wait_until_generated_zone_deleted(zone_id)

    def wait_until_recordset_change_status(self, rs_change, expected_status):
        """
        Waits a period of time for a recordset to be active by repeatedly fetching the recordset and testing
        the recordset status
        :param rs_change: The recordset change being evaluated, must include the id and the zone id
        :return: The recordset change that is active, or it could still be pending if the number of retries was exhausted
        """
        change = rs_change
        retries = MAX_RETRIES
        while change["status"] != expected_status and retries > 0:
            time.sleep(RETRY_WAIT)
            retries -= 1
            latest_change = self.get_recordset_change(change["recordSet"]["zoneId"], change["recordSet"]["id"],
                                                      change["id"], status=(200, 404))
            if type(latest_change) != str:
                change = latest_change

        assert_that(change["status"], is_(expected_status))
        return change

    def batch_is_completed(self, batch_change):
        return batch_change["status"] in ["Complete", "Failed", "PartialFailure"]

    def wait_until_batch_change_completed(self, batch_change):
        """
        Waits a period of time for a batch change to be complete (or failed) by repeatedly fetching the change and testing
        the status
        :param batch_change: The batch change being evaluated
        :return: The batch change that is active, or it could still be pending if the number of retries was exhausted
        """
        change = batch_change
        retries = MAX_RETRIES
        while not self.batch_is_completed(change) and retries > 0:
            time.sleep(RETRY_WAIT)
            retries -= 1
            latest_change = self.get_batch_change(change["id"], status=(200, 404))
            if "cannot be found" in latest_change:
                change = change
            else:
                change = latest_change

        assert_that(self.batch_is_completed(change), is_(True))
        return change

    def sign_request(self, method, path, body_data, params=None, **kwargs):
        if isinstance(body_data, str):
            body_string = body_data
        else:
            body_string = json.dumps(body_data)

        # We need to add the X-Amz-Date header so that we get a date in a format expected by the API
        from datetime import datetime
        request_headers = {
            "X-Amz-Date": datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
        }
        request_headers.update(kwargs.get("with_headers", dict()))

        headers = self.signer.sign_request_headers(method, path, request_headers, body_string, params)

        return headers, body_string
