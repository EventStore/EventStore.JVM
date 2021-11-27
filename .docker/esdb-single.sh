#!/usr/bin/env bash
set -e
SCRIPT_DIR=$(dirname $BASH_SOURCE)
docker-compose -p esdb-single -f $SCRIPT_DIR/esdb-single.yml "$@"