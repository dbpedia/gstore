#!/bin/bash

CURRENT_DIR=$(pwd)

echo "Data root: " $1
echo "Databus URI: " $2
echo "Account name: " $3
echo "Api key: " $4
echo "DAV URI: " $5
echo "DAV user: " $6
echo "DAV password: " $7

DATA_ROOT=$1
ACCOUNT=$3
KEY=$4
DATABUS_URI=$2
DAV_URI=$5
DAV_P=$6
DAV_U=$7

VERSION=`date '+%Y.%m.%d'`
DATA_ROOT=${DATA_ROOT:-'/databus/git_root/'}

echo "DATA_ROOT: " $DATA_ROOT
echo "VERSION: " $VERSION

cd ${DATA_ROOT}

FN=bckp_${VERSION}.tar.gz

tar -czf $FN $(ls -d */)

SIZE=$(ls -l $FN | awk '{print $5}')

if [ "$(uname)" == "Darwin" ]
then
    function sha256sum() { shasum -a 256 "$@" ; } && export -f sha256sum
fi
HASH=$(sha256sum $FN | awk '{print $1}')

GROUP=databus
ARTIFACT=backup

$CURRENT_DIR/generate-backup-meta.sh $DATABUS_URI $ACCOUNT $SIZE $HASH $VERSION $GROUP $ARTIFACT
$CURRENT_DIR/deploy.sh $DATABUS_URI backup.jsonld $KEY $GROUP $ARTIFACT $VERSION $DAV_URI $DAV_U $DAV_P $FN
