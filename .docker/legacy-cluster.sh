#!/usr/bin/env bash
set -e
SCRIPT_DIR=$(dirname $BASH_SOURCE)
docker-compose -p legacy-cluster -f $SCRIPT_DIR/legacy-cluster.yml "$@"