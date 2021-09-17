import logging

from datetime import datetime
from hashlib import sha256


import requests.compat as urlparse
from boto.dynamodb2.layer1 import DynamoDBConnection

logger = logging.getLogger(__name__)

__all__ = [u'BotoRequestSigner']


class BotoRequestSigner(object):

    def __init__(self, index_url, access_key, secret_access_key):
        url = urlparse.urlparse(index_url)
        self.boto_connection = DynamoDBConnection(
            host = url.hostname,
            port = url.port,
            aws_access_key_id = access_key,
            aws_secret_access_key = secret_access_key,
            is_secure = False)

    @staticmethod
    def canonical_date(headers):
        """Derive canonical date (ISO 8601 string) from headers if possible,
           or synthesize it if no usable header exists."""
        iso_format = u'%Y%m%dT%H%M%SZ'
        http_format = u'%a, %d %b %Y %H:%M:%S GMT'

        def try_parse(date_string, format):
            if date_string is None:
                return None
            try:
                return datetime.strptime(date_string, format)
            except ValueError:
                return None

        amz_date = try_parse(headers.get(u'X-Amz-Date'), iso_format)
        http_date = try_parse(headers.get(u'Date'), http_format)
        fallback_date = datetime.utcnow()

        date = next(d for d in [amz_date, http_date, fallback_date] if d is not None)
        return date.strftime(iso_format)

    def build_auth_header(self, method, path, headers, body, params=None):
        """Construct an Authorization header, using boto."""

        request = self.boto_connection.build_base_http_request(
            method=method,
            path=path,
            auth_path=path,
            headers=headers,
            data=body,
            params=params or {})

        auth_handler = self.boto_connection._auth_handler

        timestamp = BotoRequestSigner.canonical_date(headers)
        request.timestamp = timestamp[0:8]

        request.region_name = u'us-east-1'
        request.service_name = u'VinylDNS'

        credential_scope = u'/'.join([request.timestamp, request.region_name, request.service_name, u'aws4_request'])

        canonical_request = auth_handler.canonical_request(request)
        split_request = canonical_request.split('\n')

        if params != {} and split_request[2] == '':
            split_request[2] = self.generate_canonical_query_string(params)
            canonical_request = '\n'.join(split_request)
        hashed_request = sha256(canonical_request.encode(u'utf-8')).hexdigest()

        string_to_sign = u'\n'.join([u'AWS4-HMAC-SHA256', timestamp, credential_scope, hashed_request])
        signature = auth_handler.signature(request, string_to_sign)
        headers_to_sign = auth_handler.headers_to_sign(request)

        auth_header = u','.join([
            u'AWS4-HMAC-SHA256 Credential=%s' % auth_handler.scope(request),
            u'SignedHeaders=%s' % auth_handler.signed_headers(headers_to_sign),
            u'Signature=%s' % signature])

        return auth_header

    @staticmethod
    def generate_canonical_query_string(params):
        """
        Using in place of canonical_query_string from boto/auth.py to support POST requests with query parameters
        """
        post_params = []
        for param in sorted(params):
            value = params[param].encode('utf-8')
            import urllib
            try:
                post_params.append('%s=%s' % (urllib.parse.quote(param, safe='-_.~'),
                                              urllib.parse.quote(value, safe='-_.~')))
            except:
                post_params.append('%s=%s' % (urllib.quote(param, safe='-_.~'),
                                              urllib.quote(value, safe='-_.~')))
        return '&'.join(post_params)
