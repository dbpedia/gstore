#!/bin/bash

echo "Databus URI: " $1
echo "Account name: " $2

VERSION=`date '+%Y.%m.%d'`
GIT_ROOT=${GIT_ROOT:-'/databus/git_root/'}

echo "GIT_ROOT: " $GIT_ROOT
echo "VERSION: " $VERSION

cd ${GIT_ROOT}

FN=bckp_${VERSION}.tar.gz

tar -czf $FN $(ls -d */)

SIZE=$(ls -l $FN | awk '{print $5}')
HASH=$(sha256sum $FN | awk '{print $1}')

/generate-backup-meta.sh $1 $2 $SIZE $HASH $VERSION