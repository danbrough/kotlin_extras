#!/bin/bash

cd $(dirname $0) && cd ..


rsync -avHSx build/maven/org/danbrough/ h1:/srv/https/maven/org/danbrough/
