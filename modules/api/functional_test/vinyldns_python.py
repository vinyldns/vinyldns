import json
import time
import logging
import collections

import requests
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.retry import Retry
from hamcrest import *

# TODO: Didn't like this boto request signer, fix when moving back
from boto_request_signer import BotoRequestSigner

# Python 2/3 compatibility
from requests.compat import urljoin, urlparse, urlsplit
from builtins import str
from future.utils import iteritems
from future.moves.urllib.parse import parse_qs

try:
    basestring
except NameError:
    basestring = str

logger = logging.getLogger(__name__)

__all__ = [u'VinylDNSClient', u'MAX_RETRIES', u'RETRY_WAIT']

MAX_RETRIES = 30
RETRY_WAIT = 0.05

class VinylDNSClient(object):

    def __init__(self, url, access_key, secret_key):
        self.index_url = url
        self.headers = {
            u'Accept': u'application/json, text/plain',
            u'Content-Type': u'application/json'
        }

        self.signer = BotoRequestSigner(self.index_url,
                                        access_key, secret_key)

        self.session = self.requests_retry_session()
        self.session_not_found_ok = self.requests_retry_not_found_ok_session()

    def requests_retry_not_found_ok_session(self,
                                            retries=5,
                                            backoff_factor=0.4,
                                            status_forcelist=(500, 502, 504),
                                            session=None,
                                            ):
        session = session or requests.Session()
        retry = Retry(
            total=retries,
            read=retries,
            connect=retries,
            backoff_factor=backoff_factor,
            status_forcelist=status_forcelist,
        )
        adapter = HTTPAdapter(max_retries=retry)
        session.mount(u'http://', adapter)
        session.mount(u'https://', adapter)
        return session

    def requests_retry_session(self,
                               retries=5,
                               backoff_factor=0.4,
                               status_forcelist=(500, 502, 504),
                               session=None,
                               ):
        session = session or requests.Session()
        retry = Retry(
            total=retries,
            read=retries,
            connect=retries,
            backoff_factor=backoff_factor,
            status_forcelist=status_forcelist,
        )
        adapter = HTTPAdapter(max_retries=retry)
        session.mount(u'http://', adapter)
        session.mount(u'https://', adapter)
        return session

    def make_request(self, url, method=u'GET', headers=None, body_string=None, sign_request=True, not_found_ok=False, **kwargs):

        # pull out status or None
        status_code = kwargs.pop(u'status', None)

        # remove retries arg if provided
        kwargs.pop(u'retries', None)

        path = urlparse(url).path

        # we must parse the query string so we can provide it if it exists so that we can pass it to the
        # build_vinyldns_request so that it can be properly included in the AWS signing...
        query = parse_qs(urlsplit(url).query)

        if query:
            # the problem with parse_qs is that it will return a list for ALL params, even if they are a single value
            # we need to essentially flatten the params if a param has only one value
            query = dict((k, v if len(v)>1 else v[0])
                         for k, v in iteritems(query))

        if sign_request:
            signed_headers, signed_body = self.build_vinyldns_request(method, path, body_string, query,
                                                                   with_headers=headers or {}, **kwargs)
        else:
            signed_headers = headers or {}
            signed_body = body_string

        if not_found_ok:
            response = self.session_not_found_ok.request(method, url, data=signed_body, headers=signed_headers, **kwargs)
        else:
            response = self.session.request(method, url, data=signed_body, headers=signed_headers, **kwargs)

        if status_code is not None:
            if isinstance(status_code, collections.Iterable):
                if not response.status_code in status_code:
                    print response.text
                assert_that(response.status_code, is_in(status_code))
            else:
                if response.status_code != status_code:
                    print response.text
                assert_that(response.status_code, is_(status_code))

        try:
            return response.status_code, response.json()
        except:
            return response.status_code, response.text

    def ping(self):
        """
        Simple ping request
        :return: the content of the response, which should be PONG
        """
        url = urljoin(self.index_url, '/ping')

        response, data = self.make_request(url)
        return data

    def get_status(self):
        """
        Gets processing status
        :return: the content of the response
        """
        url = urljoin(self.index_url, '/status')

        response, data = self.make_request(url)

        return data

    def post_status(self, status):
        """
        Update processing status
        :return: the content of the response
        """
        url = urljoin(self.index_url, '/status?processingDisabled={}'.format(status))
        response, data = self.make_request(url, 'POST', self.headers)

        return data

    def color(self):
        """
        Gets the current color for the application
        :return: the content of the response, which should be "blue" or "green"
        """
        url = urljoin(self.index_url, '/color')
        response, data = self.make_request(url)
        return data

    def health(self):
        """
        Checks the health of the app, asserts that a 200 should be returned, otherwise
        this will fail
        """
        url = urljoin(self.index_url, '/health')
        self.make_request(url, sign_request=False)

    def create_group(self, group, **kwargs):
        """
        Creates a new group
        :param group: A group dictionary that can be serialized to json
        :return: the content of the response, which should be a group json
        """

        url = urljoin(self.index_url, u'/groups')
        response, data = self.make_request(url, u'POST', self.headers, json.dumps(group), **kwargs)

        return data

    def get_group(self, group_id, **kwargs):
        """
        Gets a group
        :param group_id: Id of the group to get
        :return: the group json
        """

        url = urljoin(self.index_url, u'/groups/' + group_id)
        response, data = self.make_request(url, u'GET', self.headers, **kwargs)

        return data

    def delete_group(self, group_id, **kwargs):
        """
        Deletes a group
        :param group_id: Id of the group to delete
        :return: the group json
        """

        url = urljoin(self.index_url, u'/groups/' + group_id)
        response, data = self.make_request(url, u'DELETE', self.headers, not_found_ok=True, **kwargs)

        return data

    def update_group(self, group_id, group, **kwargs):
        """
        Update an existing group
        :param group_id: The id of the group being updated
        :param group: A group dictionary that can be serialized to json
        :return: the content of the response, which should be a group json
        """

        url = urljoin(self.index_url, u'/groups/{0}'.format(group_id))
        response, data = self.make_request(url, u'PUT', self.headers, json.dumps(group), not_found_ok=True, **kwargs)

        return data

    def list_my_groups(self, group_name_filter=None, start_from=None, max_items=None, **kwargs):
        """
        Retrieves my groups
        :param start_from: the start key of the page
        :param max_items: the page limit
        :param group_name_filter: only returns groups whose names contain filter string
        :return: the content of the response
        """

        args = []
        if group_name_filter:
            args.append(u'groupNameFilter={0}'.format(group_name_filter))
        if start_from:
            args.append(u'startFrom={0}'.format(start_from))
        if max_items is not None:
            args.append(u'maxItems={0}'.format(max_items))

        url = urljoin(self.index_url, u'/groups') + u'?' + u'&'.join(args)
        response, data = self.make_request(url, u'GET', self.headers, **kwargs)

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
            args.append(u'groupNameFilter={0}'.format(group_name_filter))

        url = urljoin(self.index_url, u'/groups') + u'?' + u'&'.join(args)
        response, data = self.make_request(url, u'GET', self.headers, **kwargs)

        groups.extend(data[u'groups'])

        while u'nextId' in data:
            args = []

            if group_name_filter:
                args.append(u'groupNameFilter={0}'.format(group_name_filter))
            if u'nextId' in data:
                args.append(u'startFrom={0}'.format(data[u'nextId']))

            response, data = self.make_request(url, u'GET', self.headers, **kwargs)
            groups.extend(data[u'groups'])

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
            url = urljoin(self.index_url, u'/groups/{0}/members'.format(group_id))
        elif start_from is None and max_items is not None:
            url = urljoin(self.index_url, u'/groups/{0}/members?maxItems={1}'.format(group_id, max_items))
        elif start_from is not None and max_items is None:
            url = urljoin(self.index_url, u'/groups/{0}/members?startFrom={1}'.format(group_id, start_from))
        elif start_from is not None and max_items is not None:
            url = urljoin(self.index_url, u'/groups/{0}/members?startFrom={1}&maxItems={2}'.format(group_id,
                                                                                                   start_from,
                                                                                                   max_items))

        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, **kwargs)

        return data

    def list_group_admins(self, group_id, **kwargs):
        """
        returns the group admins
        :param group_id: the Id of the group
        :return: the user info of the admins
        """
        url = urljoin(self.index_url, u'/groups/{0}/admins'.format(group_id))
        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, **kwargs)

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
            url = urljoin(self.index_url, u'/groups/{0}/activity'.format(group_id))
        elif start_from is None and max_items is not None:
            url = urljoin(self.index_url, u'/groups/{0}/activity?maxItems={1}'.format(group_id, max_items))
        elif start_from is not None and max_items is None:
            url = urljoin(self.index_url, u'/groups/{0}/activity?startFrom={1}'.format(group_id, start_from))
        elif start_from is not None and max_items is not None:
            url = urljoin(self.index_url, u'/groups/{0}/activity?startFrom={1}&maxItems={2}'.format(group_id,
                                                                                                    start_from,
                                                                                                    max_items))

        response, data = self.make_request(url, u'GET', self.headers, **kwargs)

        return data

    def create_zone(self, zone, **kwargs):
        """
        Creates a new zone with the given name and email
        :param zone: the zone to be created
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones')
        response, data = self.make_request(url, u'POST', self.headers, json.dumps(zone), **kwargs)
        return data

    def update_zone(self, zone, **kwargs):
        """
        Updates a zone
        :param zone: the zone to be created
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/{0}'.format(zone[u'id']))
        response, data = self.make_request(url, u'PUT', self.headers, json.dumps(zone), not_found_ok=True, **kwargs)
        return data

    def sync_zone(self, zone_id, **kwargs):
        """
        Syncs a zone
        :param zone: the zone to be updated
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/{0}/sync'.format(zone_id))
        response, data = self.make_request(url, u'POST', self.headers, not_found_ok=True, **kwargs)
        return data

    def delete_zone(self, zone_id, **kwargs):
        """
        Deletes the zone for the given id
        :param zone_id: the id of the zone to be deleted
        :return: nothing, will fail if the status code was not expected
        """
        url = urljoin(self.index_url, u'/zones/{0}'.format(zone_id))
        response, data = self.make_request(url, u'DELETE', self.headers, not_found_ok=True, **kwargs)

        return data

    def get_zone(self, zone_id, **kwargs):
        """
        Gets a zone for the given zone id
        :param zone_id: the id of the zone to retrieve
        :return: the zone, or will 404 if not found
        """
        url = urljoin(self.index_url, u'/zones/{0}'.format(zone_id))
        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, **kwargs)

        return data

    def get_zone_by_name(self, zone_name, **kwargs):
        """
        Gets a zone for the given zone name
        :param zone_name: the name of the zone to retrieve
        :return: the zone, or will 404 if not found
        """
        url = urljoin(self.index_url, u'/zones/name/{0}'.format(zone_name))
        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, **kwargs)

        return data

    def get_backend_ids(self, **kwargs):
        """
        Gets list of configured backend ids
        :return: list of strings
        """
        url = urljoin(self.index_url, u'/zones/backendids')
        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, **kwargs)

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
            args.append(u'startFrom={0}'.format(start_from))
        if max_items is not None:
            args.append(u'maxItems={0}'.format(max_items))
        url = urljoin(self.index_url, u'/zones/{0}/changes'.format(zone_id)) + u'?' + u'&'.join(args)

        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, **kwargs)
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
            args.append(u'startFrom={0}'.format(start_from))
        if max_items is not None:
            args.append(u'maxItems={0}'.format(max_items))
        url = urljoin(self.index_url, u'/zones/{0}/recordsetchanges'.format(zone_id)) + u'?' + u'&'.join(args)

        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, **kwargs)
        return data

    def list_zones(self, name_filter=None, start_from=None, max_items=None, **kwargs):
        """
        Gets a list of zones that currently exist
        :return: a list of zones
        """
        url = urljoin(self.index_url, u'/zones')

        query = []
        if name_filter:
            query.append(u'nameFilter=' + name_filter)

        if start_from:
            query.append(u'startFrom=' + str(start_from))

        if max_items:
            query.append(u'maxItems=' + str(max_items))

        if query:
            url = url + u'?' + u'&'.join(query)

        response, data = self.make_request(url, u'GET', self.headers, **kwargs)
        return data

    def create_recordset(self, recordset, **kwargs):
        """
        Creates a new recordset
        :param recordset: the recordset to be created
        :return: the content of the response
        """
        if recordset and u'name' in recordset:
            recordset[u'name'] = recordset[u'name'].replace(u'_', u'-')

        url = urljoin(self.index_url, u'/zones/{0}/recordsets'.format(recordset[u'zoneId']))
        response, data = self.make_request(url, u'POST', self.headers, json.dumps(recordset), **kwargs)
        return data

    def delete_recordset(self, zone_id, rs_id, **kwargs):
        """
        Deletes an existing recordset
        :param zone_id: the zone id the recordset belongs to
        :param rs_id: the id of the recordset to be deleted
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/{0}/recordsets/{1}'.format(zone_id, rs_id))

        response, data = self.make_request(url, u'DELETE', self.headers, not_found_ok=True, **kwargs)
        return data

    def update_recordset(self, recordset, **kwargs):
        """
        Deletes an existing recordset
        :param recordset: the recordset to be updated
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/{0}/recordsets/{1}'.format(recordset[u'zoneId'], recordset[u'id']))

        response, data = self.make_request(url, u'PUT', self.headers, json.dumps(recordset), not_found_ok=True, **kwargs)
        return data

    def get_recordset(self, zone_id, rs_id, **kwargs):
        """
        Gets an existing recordset
        :param zone_id: the zone id the recordset belongs to
        :param rs_id: the id of the recordset to be retrieved
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/{0}/recordsets/{1}'.format(zone_id, rs_id))

        response, data = self.make_request(url, u'GET', self.headers, None, not_found_ok=True, **kwargs)
        return data

    def get_recordset_change(self, zone_id, rs_id, change_id, **kwargs):
        """
        Gets an existing recordset change
        :param zone_id: the zone id the recordset belongs to
        :param rs_id: the id of the recordset to be retrieved
        :param change_id: the id of the change to be retrieved
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/{0}/recordsets/{1}/changes/{2}'.format(zone_id, rs_id, change_id))

        response, data = self.make_request(url, u'GET', self.headers, None, not_found_ok=True, **kwargs)
        return data

    def list_recordsets(self, zone_id, start_from=None, max_items=None, record_name_filter=None, **kwargs):
        """
        Retrieves all recordsets in a zone
        :param zone_id: the zone to retrieve
        :param start_from: the start key of the page
        :param max_items: the page limit
        :param record_name_filter: only returns recordsets whose names contain filter string
        :return: the content of the response
        """
        args = []
        if start_from:
            args.append(u'startFrom={0}'.format(start_from))
        if max_items is not None:
            args.append(u'maxItems={0}'.format(max_items))
        if record_name_filter:
            args.append(u'recordNameFilter={0}'.format(record_name_filter))

        url = urljoin(self.index_url, u'/zones/{0}/recordsets'.format(zone_id)) + u'?' + u'&'.join(args)

        response, data = self.make_request(url, u'GET', self.headers, **kwargs)
        return data

    def create_batch_change(self, batch_change_input, **kwargs):
        """
        Creates a new batch change
        :param batch_change_input: the batchchange to be created
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/batchrecordchanges')
        response, data = self.make_request(url, u'POST', self.headers, json.dumps(batch_change_input), **kwargs)
        return data

    def get_batch_change(self, batch_change_id, **kwargs):
        """
        Gets an existing batch change
        :param batch_change_id: the unique identifier of the batchchange
        :return: the content of the response
        """
        url = urljoin(self.index_url, u'/zones/batchrecordchanges/{0}'.format(batch_change_id))
        response, data = self.make_request(url, u'GET', self.headers, None, not_found_ok=True, **kwargs)
        return data

    def list_batch_change_summaries(self, start_from=None, max_items=None, **kwargs):
        """
        Gets list of user's batch change summaries
        :return: the content of the response
        """
        args = []
        if start_from:
            args.append(u'startFrom={0}'.format(start_from))
        if max_items is not None:
            args.append(u'maxItems={0}'.format(max_items))

        url = urljoin(self.index_url, u'/zones/batchrecordchanges') + u'?' + u'&'.join(args)

        response, data = self.make_request(url, u'GET', self.headers, **kwargs)
        return data

    def build_vinyldns_request(self, method, path, body_data, params=None, **kwargs):

        if isinstance(body_data, basestring):
            body_string = body_data
        else:
            body_string = json.dumps(body_data)

        new_headers = {u'X-Amz-Target': u'VinylDNS'}
        new_headers.update(kwargs.get(u'with_headers', dict()))

        suppress_headers = kwargs.get(u'suppress_headers', list())

        headers = self.build_headers(new_headers, suppress_headers)

        auth_header = self.signer.build_auth_header(method, path, headers, body_string, params)
        headers[u'Authorization'] = auth_header

        return headers, body_string

    @staticmethod
    def build_headers(new_headers, suppressed_keys):
        """Construct HTTP headers for a request."""

        def canonical_header_name(field_name):
            return u'-'.join(word.capitalize() for word in field_name.split(u'-'))

        import datetime
        now = datetime.datetime.utcnow()
        headers = {u'Content-Type': u'application/x-amz-json-1.0',
                   u'Date': now.strftime(u'%a, %d %b %Y %H:%M:%S GMT'),
                   u'X-Amz-Date': now.strftime(u'%Y%m%dT%H%M%SZ')}

        for k, v in iteritems(new_headers):
            headers[canonical_header_name(k)] = v

        for k in map(canonical_header_name, suppressed_keys):
            if k in headers:
                del headers[k]

        return headers

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
        url = urljoin(self.index_url, '/zones/{0}/acl/rules'.format(zone_id))
        response, data = self.make_request(url, 'PUT', self.headers, json.dumps(acl_rule), sign_request=sign_request, **kwargs)

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
        url = urljoin(self.index_url, '/zones/{0}/acl/rules'.format(zone_id))
        response, data = self.make_request(url, 'DELETE', self.headers, json.dumps(acl_rule), sign_request=sign_request, **kwargs)

        return data

    def wait_until_recordset_deleted(self, zone_id, record_set_id, **kwargs):
        retries = MAX_RETRIES
        url = urljoin(self.index_url, u'/zones/{0}/recordsets/{1}'.format(zone_id, record_set_id))
        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, status=(200, 404),  **kwargs)
        while response != 404 and retries > 0:
            url = urljoin(self.index_url, u'/zones/{0}/recordsets/{1}'.format(zone_id, record_set_id))
            response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, status=(200, 404), **kwargs)
            retries -= 1
            time.sleep(RETRY_WAIT)

        return response == 404

    def wait_until_zone_change_status_synced(self, zone_change):
        """
        Waits until the zone change status is Synced
        """
        latest_change = zone_change
        retries = MAX_RETRIES

        while latest_change[u'status'] != 'Synced' and latest_change[u'status'] != 'Failed' and retries > 0:
            changes = self.list_zone_changes(zone_change['zone']['id'])
            if u'zoneChanges' in changes:
                matching_changes = filter(lambda change: change[u'id'] == zone_change[u'id'], changes[u'zoneChanges'])
                if len(matching_changes) > 0:
                    latest_change = matching_changes[0]
            time.sleep(RETRY_WAIT)
            retries -= 1

        assert_that(latest_change[u'status'], is_('Synced'))

    def wait_until_zone_deleted(self, zone_id, **kwargs):
        """
        Waits a period of time for the zone deletion to complete.

        :param zone_id: the id of the zone that has been deleted.
        :param kw: Additional parameters for the http request
        :return: True when the zone deletion is complete False if the timeout expires
        """
        retries = MAX_RETRIES
        url = urljoin(self.index_url, u'/zones/{0}'.format(zone_id))
        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, status=(200, 404), **kwargs)
        while response != 404 and retries > 0:
            url = urljoin(self.index_url, u'/zones/{0}'.format(zone_id))
            response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, status=(200, 404), **kwargs)
            retries -= 1
            time.sleep(RETRY_WAIT)

        assert_that(response, is_(404))

    #TODO Replace calls to this method with wait_until_zone_active and remove
    def wait_until_zone_exists(self, zone_change, **kwargs):
        """
        Shim method to invoke wait_until_zone_active
        """
        self.wait_until_zone_active(zone_change[u'zone'][u'id'])

    def wait_until_zone_active(self, zone_id):
        """
        Waits a period of time for the zone sync to complete.

        :param zone_id: the ID for the zone.
        """
        retries = MAX_RETRIES
        zone_request = self.get_zone(zone_id)

        while (u'zone' not in zone_request or zone_request[u'zone'][u'status'] != 'Active') and retries > 0:
            zone_request = self.get_zone(zone_id)
            time.sleep(RETRY_WAIT)
            retries -= 1

        assert_that(zone_request[u'zone'][u'status'], is_('Active'))

    def wait_until_recordset_exists(self, zone_id, record_set_id, **kwargs):
        """
        Waits a period of time for the record set creation to complete.

        :param zone_id: the id of the zone the record set lives in
        :param record_set_id: the id of the recprdset that has been created.
        :param kw: Additional parameters for the http request
        :return: True when the recordset creation is complete False if the timeout expires
        """
        retries = MAX_RETRIES
        url = urljoin(self.index_url, u'/zones/{0}/recordsets/{1}'.format(zone_id, record_set_id))
        response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, status=(200, 404), **kwargs)
        while response != 200 and retries > 0:
            response, data = self.make_request(url, u'GET', self.headers, not_found_ok=True, status=(200, 404), **kwargs)
            retries -= 1
            time.sleep(RETRY_WAIT)

        if response == 200:
            return data

        return response == 200

    def abandon_zones(self, zone_ids, **kwargs):
        #delete each zone
        for zone_id in zone_ids:
            self.delete_zone(zone_id, status=(202, 404))

        # Wait until each zone is gone
        for zone_id in zone_ids:
            self.wait_until_zone_deleted(zone_id)

    def wait_until_recordset_change_status(self, rs_change, expected_status):
        """
        Waits a period of time for a recordset to be active by repeatedly fetching the recordset and testing
        the recordset status
        :param rs_change: The recordset change being evaluated, must include the id and the zone id
        :return: The recordset change that is active, or it could still be pending if the number of retries was exhausted
        """
        change = rs_change
        retries = MAX_RETRIES
        while change['status'] != expected_status and retries > 0:
            latest_change = self.get_recordset_change(change['recordSet']['zoneId'], change['recordSet']['id'],
                                                      change['id'], status=(200,404))
            print "\r\n --- latest change is " + str(latest_change)
            if "Unable to find record set change" in latest_change:
                change = change
            else:
                change = latest_change

            time.sleep(RETRY_WAIT)
            retries -= 1

        if change['status'] != expected_status:
            print 'Failed waiting for record change status'
            if 'systemMessage' in change:
                print 'systemMessage is ' + change['systemMessage']

        assert_that(change['status'], is_(expected_status))
        return change

    def batch_is_completed(self, batch_change):
        return batch_change['status'] in ['Complete', 'Failed', 'PartialFailure']

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
            latest_change = self.get_batch_change(change['id'], status=(200,404))
            print "\r\n --- latest change is " + str(latest_change)
            if "cannot be found" in latest_change:
                change = change
            else:
                change = latest_change

            time.sleep(RETRY_WAIT)
            retries -= 1

        if not self.batch_is_completed(change):
            print 'Failed waiting for record change status'
            print change

        assert_that(self.batch_is_completed(change), is_(True))
        return change
