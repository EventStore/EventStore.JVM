#!/usr/bin/env bash
set -e
SCRIPT_DIR=$(dirname $BASH_SOURCE)
docker-compose -p esdb-cluster -f $SCRIPT_DIR/esdb-cluster.yml "$@"