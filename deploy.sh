echo "Deploying to: " $1
echo "Metadata file: " $2
echo "API key:" $3

GROUP=$4
ARTIFACT=$5
VERSION=$6
DAV_URI=$7
DAV_U=$8
DAV_P=$9
DFN=${10}

echo "================================="
echo "Deploying..."
echo "curl -X POST -H \"x-api-key: ${3}\" -H \"Content-Type: application/json\" -d "@${2}" \"${1}/system/publish\""
curl -X POST -H "x-api-key: ${3}" -H "Content-Type: application/json" -d "@${2}" "${1}/system/publish"

echo "curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/"
curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/
echo "curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/"
curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/
echo "curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/$ARTIFACT/"
curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/$ARTIFACT/
echo "curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/$ARTIFACT/$VERSION/"
curl -X MKCOL -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/$ARTIFACT/$VERSION/


echo "curl -T $DFN -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/$ARTIFACT/$VERSION/$DFN"
curl -T $DFN -u ${DAV_U}:${DAV_P} ${DAV_URI}/repo/$GROUP/$ARTIFACT/$VERSION/$DFN

echo "Done."