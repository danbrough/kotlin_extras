#!/bin/bash

cd $(dirname $0) && cd ..


./gradlew -Pbuild.snapshot=true publishAllPublicationsToMavenRepository || exit 1

./scripts/syncmaven.sh
