#!/usr/bin/env python
import os
import sys

import VinylDNSProto_pb2
import mysql.connector

# arguments
if len(sys.argv) != 3:
    print("USAGE: update-support-user.py <username> <True|False>")
    exit(1)

user_name = sys.argv[1]
make_support = sys.argv[2][0].upper() == 'T'

# environment variables to connect to database
db_user = os.environ.get('DB_USER')
db_pass = os.environ.get('DB_PASS')
db_host = os.environ.get('DB_HOST')
db_name = os.environ.get('DB_NAME')
db_port = os.environ.get('DB_PORT')

cnx = mysql.connector.connect(user=db_user, password=db_pass, host=db_host, database=db_name, port=db_port)

query = "SELECT data FROM user where user_name = %(user_name)s"
update = "UPDATE user SET data = %(pb)s WHERE user_name = %(user_name)s"

cursor = cnx.cursor(dictionary=True)

try:
    cursor.execute(query, {'user_name': user_name})
    user = VinylDNSProto_pb2.User()
    for row in cursor:
        user_raw = row['data']
        user = VinylDNSProto_pb2.User()
        if isinstance(user_raw, str):
            print("Type of user data is string {}".format(user_raw))
            user.ParseFromString(base64.encodebytes(user_raw.encode()))
        else:
            print("Type of user data is bytes")
            user.ParseFromString(user_raw)
        print("FOUND USER NAME {}, IS SUPPORT = {}".format(user.userName, user.isSupport))

    if user.userName is None or len(user.userName) == 0:
        sys.exit("User {0} not found; cannot continue.".format(user_name))

    if make_support is not None:
        print("Updating {}, Support = {}".format(user_name, make_support))
        user.isSupport = make_support
        cursor.execute(update, {'pb': user.SerializeToString(), 'user_name': user_name})
        cnx.commit()
    else:
        print("Skipping making support as no make support value provided")

finally:
    cursor.close()
    cnx.close()
