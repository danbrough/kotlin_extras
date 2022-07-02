#!/bin/bash

cd $(dirname $0) && cd ..

rsync -avHSx --delete openssl/lib/ mac:~/workspace/kotlin/kotlin_extras/openssl/lib/
