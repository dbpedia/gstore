if [ "$(uname)" == "Darwin" ]
then
  NOW=$(date -u +%FT%TZ)
else
  NOW=$(date --utc +%FT%TZ)
fi

VERSION=$5
GROUP=$6
ARTIFACT=$7

echo "Databus URI: " $1
echo "Account name: " $2
echo "SIZE: " $3
echo "HASH: " $4
echo "Version: " $VERSION
echo "Artifact: " $ARTIFACT
echo "Group: " $GROUP
echo "Issued: " $NOW

read -r -d '' DATAID_DATA << _EOT_
{
  "@context" : "%DATABUS_URI%/system/context.jsonld",
  "@graph" : [
    {
      "@id": "%DATABUS_URI%/%ACCOUNT%/%GROUP%",
      "@type": "dataid:Group",
      "title": { "@value" : "Databus group", "@language" : "en" },
      "abstract": { "@value" : "This is a group for databus backup.", "@language" : "en" },
      "description": { "@value" : "This is a group for databus backup.", "@language" : "en" }
    },
    {
      "@id": "%DATABUS_URI%/%ACCOUNT%/%GROUP%/%ARTIFACT%",
      "@type": "dataid:Artifact"
    },
    {
      "@id": "%DATABUS_URI%/%ACCOUNT%/%GROUP%/%ARTIFACT%/%VERSION%",
      "@type": "dataid:Version"
    },
    {
      "@id": "%DATABUS_URI%/%ACCOUNT%/%GROUP%/%ARTIFACT%/%VERSION%#Dataset",
      "@type": "dataid:Dataset",
      "title": { "@value" : "Databus backup", "@language" : "en" },
      "abstract": { "@value" : "This is a databus backup.", "@language" : "en" },
      "description": { "@value" : "This is a databus backup.", "@language" : "en" },
      "publisher": "%DATABUS_URI%/%ACCOUNT%#this",
      "group": "%DATABUS_URI%/%ACCOUNT%/%GROUP%",
      "artifact": "%DATABUS_URI%/%ACCOUNT%/%GROUP%/%ARTIFACT%",
      "version": "%DATABUS_URI%/%ACCOUNT%/%GROUP%/%ARTIFACT%/%VERSION%",
      "hasVersion": "%VERSION%",
      "issued": "%NOW%",
      "license": "http://creativecommons.org/licenses/by/4.0/",
      "distribution": [
        {
          "@id": "%DATABUS_URI%/%ACCOUNT%/%GROUP%/%ARTIFACT%/%VERSION%#ontology--DEV_type=backup.tar.gz",
          "@type": "dataid:SingleFile",
          "issued": "%NOW%",
          "file": "%DATABUS_URI%/%ACCOUNT%/%GROUP%/%ARTIFACT%/%VERSION%/#ontology--DEV_type=backup.tar.gz",
          "format": "tar",
          "compression": "gz",
          "downloadURL": "%DATABUS_URI%/DAV/repo/%GROUP%/%ARTIFACT%/%VERSION%/bckp_%VERSION%.tar.gz",
          "byteSize": "%SIZE%",
          "sha256sum": "%HASH%",
          "hasVersion": "%VERSION%"
        }
      ]
    }
  ]
}
_EOT_

DATAID_DATA=${DATAID_DATA//%DATABUS_URI%/$1}
DATAID_DATA=${DATAID_DATA//%ACCOUNT%/$2}
DATAID_DATA=${DATAID_DATA//%SIZE%/$3}
DATAID_DATA=${DATAID_DATA//%HASH%/$4}
DATAID_DATA=${DATAID_DATA//%GROUP%/$GROUP}
DATAID_DATA=${DATAID_DATA//%ARTIFACT%/$ARTIFACT}
DATAID_DATA=${DATAID_DATA//%VERSION%/$VERSION}
DATAID_DATA=${DATAID_DATA//%NOW%/$NOW}

echo "$DATAID_DATA" > ./backup.jsonld
echo "Generated backup metadata."
echo "-- ./backup.jsonld"