#!/bin/bash

# This will fail to build the installers, but as this is a hacky patch to remove the
# problematic log4j versions, we only need the services uber jar right now (the rest
# will be manually patched in the installers config setup).
#
# We will patch the newest version of the service properly for future releases.

docker-compose build
docker-compose up
