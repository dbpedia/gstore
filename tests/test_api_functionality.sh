#!/bin/bash
source ./custom-assert.sh
source ./functions.sh

echo "#########
 Writing
#########"
## Group

code=$(post_return_code_contenttype_applicationldjson "http://localhost:3002/file/save?repo=janni&path=testing/group" @./data/func_group.jsonld)
assert_eq "$code" "200" " http code: $code, ct=jsonld upload ./data/func_group.jsonld to janni/testing/group"

code=$(post_return_code_contenttype_textturtle "http://localhost:3002/file/save?repo=janni&path=testing/group2" @./data/func_group.ttl)
assert_eq "$code" "200" " http code: $code, ct=turtle upload ./data/func_group.ttl to janni/testing/group2"



code=$(post_return_code_contenttype_textturtle "http://localhost:3002/file/save?repo=janni&path=testing/group" @./data/func_group.jsonld)
assert_eq "$code" "400" " http code: $code, upload wrong content-type ./data/func_group.jsonld to janni/testing/group"
code=$(post_return_code_contenttype_applicationldjson "http://localhost:3002/file/save?repo=janni&path=testing/group2" @./data/func_group.ttl)
assert_eq "$code" "400" " http code: $code, uploading wrong content-type ./data/func_group.ttl to janni/testing/group2 "


echo "#########
Reading from /file/read
#########"

echo "## HTTP CODE"

code=$(get_return_code_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group")
assert_eq "$code" "200" " http code: $code, read  janni/testing/group"

code=$(get_return_code_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group2")
assert_eq "$code" "200" " http code: $code, read  janni/testing/group"

code=$(get_return_code_accept_applicationldjson "http://localhost:3002/file/read?repo=XXX&path=XXX/XXX.jsonld")
assert_eq "$code" "404" " http code: $code, read file that does not exist"


echo "
## Syntax of body"
body=$(get_body "http://localhost:3002/file/read?repo=janni&path=testing/group")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json for group? default no accept"

body=$(get_body "http://localhost:3002/file/read?repo=janni&path=testing/group2")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json for group2? default no accept"


body=$(get_body "http://localhost:3002/file/read?repo=janni&path=testing/group")
check=$(echo "$body" | wc -l)
assert_not_eq "$check" "1" "group number of lines: $check, 1 indicates that json is minified"

body=$(get_body "http://localhost:3002/file/read?repo=janni&path=testing/group2")
check=$(echo "$body" | wc -l)
assert_not_eq "$check" "1" "group number of lines: $check, 1 indicates that json is minified"

body=$(get_body_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json?"

body=$(get_body_accept_textturtle "http://localhost:3002/file/read?repo=janni&path=testing/group")
#rapper -i turtle -c  -O - - file <<< "$body"
#check="$?"
check=$(check_valid_turtle "$body")
assert_eq "$check" "valid" "rapper: $check, valid turtle? "



echo "
## Content-Type"
code=$(get_contenttype "http://localhost:3002/file/read?repo=janni&path=testing/group")
assert_eq "$code" "application/ld+json;charset=utf-8"  "Content-Type: $code, testing content type of no accept (default)"

code=$(get_contenttype_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group")
assert_eq "$code" "application/ld+json;charset=utf-8"  "Content-Type: $code, testing content type of accept application/ld+json"

code=$(get_contenttype_accept_textturtle "http://localhost:3002/file/read?repo=janni&path=testing/group")
assert_eq "$code" "text/turtle;charset=utf-8"  "Content-Type: $code, testing content type of accept text/turtle"


echo "
## Virtuoso saving in DB"
body=$(get_body_accept_textturtle "http://localhost:3002/file/read?repo=janni&path=testing/group")
enc=$(rawurlencode "ASK { $body }")
#echo $enc
code=$(get_body "localhost:3002/sparql?default-graph-uri=&query=$enc" )
assert_eq "$code" "true" "Virtuoso SPARQL ASK for content of janni/testing/group"

enc=$(rawurlencode "ASK { GRAPH <http://localhost3002/graph	/janni/testing/group> {?s ?p ?o} }")
#echo $enc
code=$(get_body "localhost:3002/sparql?default-graph-uri=&query=$enc" )
assert_eq "$code" "true" "Virtuoso SPARQL ASK for GRAPH <http://localhost3002/graph	/janni/testing/group>"


echo "
## GIT"
DIFF=$(diff <(cat ../databus/git/janni/testing/group | jq) <(cat ./data/func_group.jsonld | jq) )
>&2 printf "Test ${BLUE}%s${NORMAL}\n" "diff <(cat ../databus/git/janni/testing/group | jq) <(cat ./data/func_group.jsonld | jq) "
VAR=$(if [ "$DIFF" == "" ]; then echo "valid" ; else echo "invalid"; fi)
assert_eq "$VAR" "valid" "equivalence (jq normalization) of initial group.jsonld file to file saved in git"

echo "
# DELETE"

code=$(get_return_code "http://localhost:3002/file/delete?repo=janni&path=testing/group")
assert_eq "$code" "405" "delete GET not allowed"

code=$(post_return_code "http://localhost:3002/file/delete?repo=janni&path=testing/group")
assert_eq "$code" "200" "delete"

code=$(post_return_code "http://localhost:3002/file/delete?repo=janni&path=testing/group")
assert_eq "$code" "200" "delete again"

code=$(get_return_code_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group")
assert_eq "$code" "404" " http code: $code, read file that was deleted"

code=$(post_return_code "http://localhost:3002/file/delete?repo=janni&path=testing/group2")
assert_eq "$code" "200" "delete group.ttl"


