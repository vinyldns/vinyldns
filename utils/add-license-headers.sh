#!/usr/bin/env bash

TARGET_DIR=""
FIND_COMMAND_OPTS=""
SUPPORTED_FILE_TYPE="*" # Default is to choose all file types

EXIT_CODE=0

# Global flags
CHECK_ONLY_FLAG=0
HELP_FLAG=0
VERBOSE_FLAG=0

LICENSE_TEXT="/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the \"License\");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an \"AS IS\" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */"

print_usage () {
    echo -e "Description: Prepends license/header text by recursively traversing for files.\n"
    echo -e "Usage: add-license-headers.sh [-d=<target_dir> | --directory=<target_dir>] [-f=<file_type> | --file-type=<file_type>] [-h | --help] [-v | --verbose]\n"
    echo -e "\t-c|--check-only \tEnables check-only mode. Does not actually modify files, but will return an error (exit code 1) if there are files that are missing headers."
    echo -e "\t-d|--directory  \tSet target directory to recursively search for files to prepend headers."
    echo -e "\t-f|--file-type  \tAdd case-insensitive, supported file type (eg. txt). Supports single call. If no file types are specified, all will be included by default."
    echo -e "\t-v|--verbose    \tEnable verbose mode."
    echo -e "\t-h|--help       \tPrint out usage information."
}

# Process command line arguments

for i in "$@"
do
    case "$i" in
        -c|--check-only)
        CHECK_ONLY_FLAG=1
        shift
        ;;

        -d=*|--directory=*)
        TARGET_DIR="${i#*=}"
        shift
        ;;

        -f=*|--file-types=*)
        SUPPORTED_FILE_TYPE="*.${i#*=}"
        shift
        ;;

        -v|--verbose)
        VERBOSE_FLAG=1
        shift
        ;;

        -h|--help)
        HELP_FLAG=1
        shift
        ;;
    esac
done

# Perform preliminary checks

if [ "$HELP_FLAG" = 1 ]; then
    print_usage
    exit "$EXIT_CODE"
fi

if [ -z "$TARGET_DIR" ]; then
    echo "Target directory is required but not specified."
    EXIT_CODE=1
elif [ ! -d "$TARGET_DIR" ]; then
    echo "Specified target directory \"$TARGET_DIR\" does not exist."
    EXIT_CODE=1
fi

if [ "$EXIT_CODE" = 1 ]; then
    echo ""
    print_usage
    echo "Aborting program due to errors."
    exit "$EXIT_CODE"
fi

LICENSE_TEXT_LINES=$(echo "$LICENSE_TEXT" | awk '{print NR}' | tail -1)

for file in $(find "$TARGET_DIR" -type f -iname "$SUPPORTED_FILE_TYPE"); do
    if [ ! -d "$file" ]; then
        STARTING_TEXT=$(head -n "$LICENSE_TEXT_LINES" "$file")
        if [[ "$STARTING_TEXT" != "$LICENSE_TEXT" ]]; then
            if [ "$CHECK_ONLY_FLAG" = 1 ]; then
                EXIT_CODE=1
                echo "$file"
            else
                if [ "$VERBOSE_FLAG" = 1 ]; then
                    echo "$file"
                fi
                $(printf '0i\n'"$LICENSE_TEXT"'\n\n.\nwq\n' | ed -s "$file")
            fi
        fi
    fi
done

exit "$EXIT_CODE"
