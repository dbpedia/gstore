#!/bin/bash
source ./custom-assert.sh
#source ./functions.sh

SHACLFILE="../src/test/resources/test.shacl"

# SHACL

code=`curl -X 'POST' 'localhost:3002/shacl/validate'   -H 'accept: application/json'   -H 'Content-Type: multipart/form-data'   -F 'graph=@data/invalid_testfile.jsonld' -F 'shacl=@../src/test/resources/test.shacl' -o /dev/null -w '%{http_code}\n' -s `
assert_not_eq "$code" "200" " http code $code SHACL against invalid file"

code=`curl -X 'POST' 'localhost:3002/shacl/validate'   -H 'accept: application/json'   -H 'Content-Type: multipart/form-data'   -F 'graph=@data/invalid_testfile.jsonld' -F 'shacl=@../src/test/resources/test.shacl' -s `
assert_not_eq "$code" "{\"code\":200}" "response body, $code SHACL against invalid file"

code=`curl -X 'POST' 'localhost:3002/shacl/validate'   -H 'accept: application/json'   -H 'Content-Type: multipart/form-data'   -F 'graph=@data/valid_dataid.jsonld' -F 'shacl=@../src/test/resources/test.shacl' -o /dev/null -w '%{http_code}\n' -s `
assert_eq "$code" "200" "http code, $code SHACL against valid file"

code=`curl -X 'POST' 'localhost:3002/shacl/validate'   -H 'accept: application/json'   -H 'Content-Type: multipart/form-data'   -F 'graph=@data/valid_dataid.jsonld' -F 'shacl=@../src/test/resources/test.shacl' -s `
assert_eq "$code" "{\"code\":200}" "response body, $code SHACL against invalid file"


exit

# Check general availability
code=$(get_return_code localhost:3002)
assert_eq "$code" "200" "$code G-Store reachable"
code=$(get_return_code localhost:3002/)
assert_eq "$code" "200" "$code G-Store reachable"
code=$(get_return_code localhost:3002/shacl)
assert_eq "$code" "200" "$code G-Store reachable"

# GIT File Viewer
code=$(get_return_code localhost:3002/git)
assert_eq "$code" "200" "/git/ viewer reachable"
code=$(get_return_code localhost:3002/git/)
assert_eq "$code" "200" "/git/ viewer reachable"


# Check virtuoso availability
code=$(get_return_code "localhost:3002/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml" )
assert_eq "$code" "200" "Virtuoso reachable"

#result=$(curl https://databus.dbpedia.org/system/api/search?query=data -s)
#assert_not_eq "$result" "{\"docs\":[]}" "Nothing found for query 'data'"


