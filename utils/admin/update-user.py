#!/usr/bin/env python3
import os
import sys
import base64
import argparse

import VinylDNSProto_pb2
import mysql.connector

db_user = os.getenv('DB_USER')
db_pass = os.getenv('DB_PASS')
db_host = os.getenv('DB_HOST')
db_name = os.getenv('DB_NAME')
db_port = os.getenv('DB_PORT')

conn = mysql.connector.connect(
    user=db_user, password=db_pass, host=db_host, database=db_name,port=db_port)
cursor = conn.cursor(dictionary=True)


def check_user(username):
    try:
        cursor.execute("SELECT data FROM user where user_name = %(u)s", {"u": username})
    except Exception as err:
        print(f"Error checking user {username}: {err}")
        return None

    user = VinylDNSProto_pb2.User()

    for row in cursor:
        user_raw = row['data']

        if isinstance(user_raw, str):
            print(f"Type of user data is string {user_raw}")
            user.ParseFromString(base64.encodebytes(user_raw.encode()))
        else:
            print("Type of user data is bytes")
            user.ParseFromString(user_raw)

    return user


def run(args):
    username = args.username
    user = check_user(username)

    if user is None:
        return False, f"Check user error."

    if user.userName is None or len(user.userName) == 0:
        return False, f"User {username} not found."

    updated = (args.superuser or args.support or args.test or args.locked)

    if not updated and not args.info:
        return True, f"User {username} found."

    if args.superuser:
        user.isSuper = True if args.superuser == "yes" else False

    if args.support:
        user.isSupport = True if args.support == "yes" else False

    if args.test:
        user.isTest = True if args.test == "yes" else False

    if args.locked:
        user.lockStatus = "Locked" if args.locked == "yes" else "Unlocked"

    if args.info:
        return True, f"{user}\r"

    try:
        cursor.execute("UPDATE user SET data = %(d)s WHERE user_name = %(u)s",
            {"d": user.SerializeToString(), "u": username})
        conn.commit()
    except Exception as err:
        return False, f"Error updating user {username}: {err}"
    finally:
        cursor.close()
        conn.close()

    if updated:
        return True, f"User {username} updated!"

    return True, None


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Update a VinylDNS User.')
    parser.add_argument("username", help="The VinylDNS username")
    parser.add_argument("--superuser", help="Changes user isSuper flag", choices=["yes", "no"])
    parser.add_argument("--support", help="Changes user isSupport flag", choices=["yes", "no"])
    parser.add_argument("--test", help="Changes user isTest flag", choices=["yes", "no"])
    parser.add_argument("--locked", help="Changes user lockStatus flag", choices=["yes", "no"])
    parser.add_argument("--info", help="Show user info", action="store_true")

    args = parser.parse_args()
    ok, msg = run(args)
    print(msg)
    sys.exit(0 if ok else 1)
