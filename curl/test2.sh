#!/bin/bash

cd `dirname $0`


rm -rf build/src/7.84.0/androidX64/ 2>&1
../gradlew srcAutoconfAndroidX64

openSSLDir=/home/dan/workspace/kotlin/kotlin_extras/openssl/lib/androidX64/
host=x86_64-linux-android
cd build/src/7.84.0/androidX64/

export MAKE="make -j5"
if [ ! -f Makefile ]; then
./configure --with-openssl=${openSSLDir} --prefix=/tmp/curl   --host=${host} \
--with-pic --enable-shared --enable-static  \
--disable-ftp --disable-gopher --disable-file --disable-imap --disable-ldap --disable-ldaps   \
--disable-pop3 --disable-proxy --disable-rtsp --disable-smb --disable-smtp --disable-telnet --disable-tftp \
--without-gnutls || exit 1
fi

make install