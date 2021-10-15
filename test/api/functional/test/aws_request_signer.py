from urllib.parse import urljoin

import boto3
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest
from botocore.compat import HTTPHeaders

REGION_NAME = "us-east-1"
SERVICE_NAME = "VinylDNS"


class AwsSigV4RequestSigner(object):
    def __init__(self, index_url: str, access_key: str, secret_access_key: str):
        self.url = index_url
        self.boto_session = boto3.Session(
            region_name=REGION_NAME,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_access_key)

    def sign_request_headers(self, method: str, path: str, headers: dict, body: str, params: object = None) -> HTTPHeaders:
        """
        Construct the request headers, including the signature

        :param method: The HTTP method
        :param path:  The URL path
        :param headers: The request headers
        :param body: The request body
        :param params: The query parameters
        :return:
        """
        request = AWSRequest(method=method, url=urljoin(self.url, path), auth_path=path, data=body, params=params, headers=headers)
        SigV4Auth(self.boto_session.get_credentials(), SERVICE_NAME, REGION_NAME).add_auth(request)

        return request.headers
