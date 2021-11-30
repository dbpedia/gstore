#!/bin/bash
source ./custom-assert.sh
source ./functions.sh

# Writing
## Group
code=$(post_return_code_contenttype_applicationldjson "http://localhost:3002/file/save?repo=janni&path=testing/group.jsonld" @./data/func_group.jsonld)
assert_eq "$code" "200" " http code: $code, upload ./data/func_group.jsonld to janni/testing/group.jsonld"

# Reading from /file/read
## HTTP CODE
code=$(get_return_code_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "200" " http code: $code, read  janni/testing/group.jsonld"

code=$(get_return_code_accept_applicationldjson "http://localhost:3002/file/read?repo=XXX&path=XXX/XXX.jsonld")
assert_eq "$code" "404" " http code: $code, read file that does not exist"

## Syntax of body
body=$(get_body "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
check=$(check_valid_turtle "$body")
assert_eq "$check" "valid" "rapper: $check, valid turtle? default no accept"

body=$(get_body_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json?"

body=$(get_body_accept_textturle "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
#rapper -i turtle -c  -O - - file <<< "$body"
#check="$?"
check=$(check_valid_turtle "$body")
assert_eq "$check" "valid" "rapper: $check, valid turtle?"

## Content-Type
code=$(get_contenttype "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "text/turtle;charset=utf-8"  "Content-Type: $code, testing content type of no accept (default) TODO this might also be ld+json"

code=$(get_contenttype_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "application/ld+json;charset=utf-8"  "Content-Type: $code, testing content type of accept application/ld+json"

code=$(get_contenttype_accept_textturtle "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "text/turtle;charset=utf-8"  "Content-Type: $code, testing content type of accept text/turtle"

## Virtuoso saving in DB
body=$(get_body_accept_textturtle "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
enc=$(rawurlencode "ASK { $body }")
#echo $enc
code=$(get_body "localhost:3002/sparql?default-graph-uri=&query=$enc" )
assert_eq "$code" "true" "Virtuoso SPARQL ASK for content of janni/testing/group.jsonld"

## GIT
DIFF=$(diff <(cat ../databus/git/janni/testing/group.jsonld | jq) <(cat ./data/func_group.jsonld | jq) )
>&2 printf "Test ${BLUE}%s${NORMAL}\n" "diff <(cat ../databus/git/janni/testing/group.jsonld | jq) <(cat ./data/func_group.jsonld | jq) "
VAR=$(if [ "$DIFF" == "" ]; then echo "valid" ; else echo "invalid"; fi)
assert_eq "$VAR" "valid" "equivalence of initial group.jsonld file to file save in git"

# DELETE

code=$(get_return_code "http://localhost:3002/file/delete?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "405" "delete GET not allowed"

code=$(post_return_code "http://localhost:3002/file/delete?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "200" "delete"

code=$(post_return_code "http://localhost:3002/file/delete?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "200" "delete again"

code=$(get_return_code_accept_applicationldjson "http://localhost:3002/file/read?repo=janni&path=testing/group.jsonld")
assert_eq "$code" "404" " http code: $code, read file that was deleted"


exit
#code=`get_post_return_code  "http://localhost:3002/file/save?repo=testing&path=/test/document.jsonld \
#  -H "Accept:application/json" \
#  -H "Content-Type:application/ld+json" \
#  -d @./data/func_basic_group.jsonld" `

# TODO diff /home/kurzum/IdeaProjects/databus/server/app/common/shacl

