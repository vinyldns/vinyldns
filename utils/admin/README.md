## Running update-user.py locally

#### You will need protoc installed:

    # On Ubuntu:
    sudo apt install protobuf-compiler

    # On macOS:
    brew install protobuf

for any other OS, see [github protobuf releases page](https://github.com/protocolbuffers/protobuf/releases).

#### Compile the proto file:

    make proto

#### Set env vars:

    export DB_USER=
    export DB_PASS=
    export DB_HOST=
    export DB_NAME=
    export DB_PORT=

#### Create a virtualenv:

    python3 -m venv .venv
    source .venv/bin/activate

#### Install dependencies:

    pip install -r requirements.txt

#### Run:

    ./update-user.py --help
