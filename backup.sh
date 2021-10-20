#!/bin/bash

echo "Databus URI: " $1
echo "Account name: " $2
echo "Api key: " $3
echo "DAV URI: " $4
echo "DAV user: " $5
echo "DAV password: " $6

DAV_URI=$4
DAV_P=$6
DAV_U=$5

VERSION=`date '+%Y.%m.%d'`
GIT_ROOT=${GIT_ROOT:-'/databus/git_root/'}

echo "GIT_ROOT: " $GIT_ROOT
echo "VERSION: " $VERSION

cd ${GIT_ROOT}

FN=bckp_${VERSION}.tar.gz

tar -czf $FN $(ls -d */)

SIZE=$(ls -l $FN | awk '{print $5}')
HASH=$(sha256sum $FN | awk '{print $1}')
GROUP=databus
ARTIFACT=backup

/generate-backup-meta.sh $1 $2 $SIZE $HASH $VERSION $GROUP $ARTIFACT
/deploy.sh $1 backup.jsonld $3 $GROUP $ARTIFACT $VERSION $DAV_URI $DAV_U $DAV_P $FN
