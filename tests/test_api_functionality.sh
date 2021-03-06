#!/bin/bash
source ./custom-assert.sh
source ./functions.sh

HOST="${HOST:-localhost}"

clear 

echo "#########
 Writing
#########"
## Group

code=$(post_return_code_no_contenttype "http://${HOST}:3002/graph/save?repo=janni&path=testing/group" @./data/func_group.jsonld)
assert_eq "$code" "200" " http code: $code, ct=none upload ./data/func_group.jsonld to janni/testing/group"
code=$(post_return_code_contenttype_applicationldjson "http://${HOST}:3002/graph/save?repo=janni&path=testing/group" @./data/func_group.jsonld)
assert_eq "$code" "200" " http code: $code, ct=jsonld upload ./data/func_group.jsonld to janni/testing/group"
code=$(post_return_code_contenttype_applicationldjson "http://${HOST}:3002/graph/save?repo=janni&path=testing/groupprefix&prefix=http://example.org/" @./data/func_group.jsonld)
assert_eq "$code" "200" " TODO #7 '/' http code: $code, ct=jsonld upload ./data/func_group.jsonld to janni/testing/groupprefix prefix=http://example.org/"
code=$(post_return_code_contenttype_applicationldjson "http://${HOST}:3002/graph/save?repo=janni&path=testing/groupcirillic&prefix=http://ехампле.org/" @./data/func_group_cirillic.jsonld)
assert_eq "$code" "200" " TODO #7 '/' http code: $code, ct=jsonld upload ./data/func_group_cirillic.jsonld to janni/testing/groupcirillic prefix=http://ехампле.org/"
code=$(post_return_code_contenttype_textturtle "http://${HOST}:3002/graph/save?repo=janni&path=testing/groupturtle" @./data/func_group.ttl)
assert_eq "$code" "200" " http code: $code, ct=turtle upload ./data/func_group.ttl to janni/testing/groupturtle"

code=$(post_return_code_no_contenttype "http://${HOST}:3002/graph/save?repo=janni&path=testing/group" @./data/func_group.ttl)
assert_eq "$code" "400" " http code: $code, ct=none upload ./data/func_group.ttl to janni/testing/group"
code=$(post_return_code_contenttype_textturtle "http://${HOST}:3002/graph/save?repo=janni&path=testing/group" @./data/func_group.jsonld)
assert_eq "$code" "400" " http code: $code, upload wrong content-type ./data/func_group.jsonld to janni/testing/group"
code=$(post_return_code_contenttype_applicationldjson "http://${HOST}:3002/graph/save?repo=janni&path=testing/groupturtle" @./data/func_group.ttl)
assert_eq "$code" "400" " http code: $code, uploading wrong content-type ./data/func_group.ttl to janni/testing/groupturtle "



echo "#########
Reading from /graph/read
#########"

echo "
## HTTP CODE"

code=$(get_return_code_accept_applicationldjson "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
assert_eq "$code" "200" " http code: $code, read  janni/testing/group"

code=$(get_return_code_accept_applicationldjson "http://${HOST}:3002/graph/read?repo=janni&path=testing/groupturtle")
assert_eq "$code" "200" " http code: $code, read  janni/testing/groupturtle"

code=$(get_return_code_accept_applicationldjson "http://${HOST}:3002/graph/read?repo=XXX&path=XXX/XXX.jsonld")
assert_eq "$code" "404" " http code: $code, read file that does not exist"


echo "
##Reading from /g/"
code=$(get_return_code_accept_applicationldjson "http://${HOST}:3002/g/janni/testing/group")
assert_eq "$code" "200" " http code: $code, read  graph/janni/testing/group"

code=$(get_return_code_accept_textturtle "http://${HOST}:3002/g/janni/testing/group")
assert_eq "$code" "200" " http code: $code, read  graph/janni/testing/group"

body=$(get_body "http://${HOST}:3002/g/janni/testing/group")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json for group? default no accept"

body=$(get_body_accept_textturtle "http://${HOST}:3002/g/janni/testing/group")
check=$(check_valid_turtle "$body")
assert_eq "$check" "valid" "rapper: $check, valid turtle? "


echo "
## Syntax of body"
body=$(get_body "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json for group? default no accept"

body=$(get_body "http://${HOST}:3002/graph/read?repo=janni&path=testing/groupturtle")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json for groupturtle? default no accept"

body=$(get_body "http://${HOST}:3002/graph/read?repo=janni&path=testing/groupcirillic")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "cirillic jq: $check, valid json for group? default no accept"


body=$(get_body "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
check=$(echo "$body" | wc -l)
assert_not_eq "$check" "1" "group number of lines: $check, 1 indicates that json is minified"

body=$(get_body "http://${HOST}:3002/graph/read?repo=janni&path=testing/groupturtle")
check=$(echo "$body" | wc -l)
assert_not_eq "$check" "1" "groupturtle number of lines: $check, 1 indicates that json is minified"

body=$(get_body_accept_applicationldjson "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
check=$(check_valid_json "$body")
assert_eq "$check" "valid" "jq: $check, valid json?"

body=$(get_body_accept_textturtle "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
#rapper -i turtle -c  -O - - file <<< "$body"
#check="$?"
check=$(check_valid_turtle "$body")
assert_eq "$check" "valid" "rapper: $check, valid turtle? "


echo "
## Content-Type"
code=$(get_contenttype "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
assert_eq "$code" "application/ld+json;charset=utf-8"  "Content-Type: $code, testing content type of no accept (default)"

code=$(get_contenttype_accept_applicationldjson "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
assert_eq "$code" "application/ld+json;charset=utf-8"  "Content-Type: $code, testing content type of accept application/ld+json"

code=$(get_contenttype_accept_textturtle "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
assert_eq "$code" "text/turtle;charset=utf-8"  "Content-Type: $code, testing content type of accept text/turtle"


echo "
## Virtuoso saving in DB"
body=$(get_body_accept_textturtle "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
enc=$(rawurlencode "ASK { $body }")
#echo $enc
code=$(get_body "${HOST}:3002/sparql?default-graph-uri=&query=$enc" )
assert_eq "$code" "true" "Virtuoso SPARQL ASK for content of janni/testing/group"


enc=$(rawurlencode "ASK { GRAPH <http://${HOST}:3002/g/janni/testing/group> {?s ?p ?o} }")
code=$(get_body "${HOST}:3002/sparql?default-graph-uri=&query=$enc" )
assert_eq "$code" "true" "Virtuoso SPARQL ASK { GRAPH <http://${HOST}:3002/g/janni/testing/group> {?s ?p ?o} }"

enc=$(rawurlencode "ASK { GRAPH <http://example.org/janni/testing/groupprefix> {?s ?p ?o} }")
#echo $enc
code=$(get_body "${HOST}:3002/sparql?default-graph-uri=&query=$enc" )
assert_eq "$code" "true" "Virtuoso SPARQL ASK { GRAPH <http://example.org/janni/testing/groupprefix> {?s ?p ?o} }"

query="ASK { GRAPH <http://ехампле.org/janni/testing/groupcirillic> {?s ?p ?o} }"
code=`curl -s "${HOST}:3002/sparql?default-graph-uri=" --data-urlencode query="$query"`
assert_eq "$code" "true" "Virtuoso SPARQL $query"


body=$(get_body_accept_textturtle "http://${HOST}:3002/graph/read?repo=janni&path=testing/groupcirillic")
query="ASK { GRAPH <http://ехампле.org/janni/testing/groupcirillic> { $body } }"
code=`curl -s "${HOST}:3002/sparql?default-graph-uri=" --data-urlencode query="$query"`
assert_eq "$code" "true" "Virtuoso SPARQL ASK for content of janni/testing/groupcirillic"



echo "
## GIT"
DIFF=$(diff <(cat ../databus/git/janni/testing/group | jq) <(cat ./data/func_group.jsonld | jq) )
>&2 printf "Test ${BLUE}%s${NORMAL}\n" "diff <(cat ../databus/git/janni/testing/group | jq) <(cat ./data/func_group.jsonld | jq) "
VAR=$(if [ "$DIFF" == "" ]; then echo "valid" ; else echo "invalid"; fi)
assert_eq "$VAR" "valid" "IGNORE FOR NOW, SEE https://github.com/dbpedia/gstore/issues/8 equivalence (jq normalization) of initial group.jsonld file to file saved in git"



echo "
# DELETE"

code=$(get_return_code "http://${HOST}:3002/graph/delete?repo=janni&path=testing/group")
assert_eq "$code" "405" "delete GET not allowed"

code=$(delete_return_code "http://${HOST}:3002/graph/delete?repo=janni&path=testing/group")
assert_eq "$code" "200" "delete"

code=$(delete_return_code "http://${HOST}:3002/graph/delete?repo=janni&path=testing/group")
assert_eq "$code" "400" "delete again"

code=$(delete_return_code "http://${HOST}:3002/graph/delete?repo=janni&path=testing/groupprefix&prefix=http://example.org/")
assert_eq "$code" "200" "delete with prefix"

code=$(get_return_code_accept_applicationldjson "http://${HOST}:3002/graph/read?repo=janni&path=testing/group")
assert_eq "$code" "404" " http code: $code, read file that was deleted"

enc=$(rawurlencode "ASK { GRAPH <http://example.org/janni/testing/groupprefix> {?s ?p ?o} }")
#echo $enc
code=$(get_body "${HOST}:3002/sparql?default-graph-uri=&query=$enc" )
assert_eq "$code" "false" "Virtuoso SPARQL ASK { GRAPH <http://example.org/janni/testing/groupprefix> {?s ?p ?o} }"

code=$(delete_return_code "http://${HOST}:3002/graph/delete?repo=janni&path=testing/groupcirillic&prefix=http://ехампле.org/")
assert_eq "$code" "200" "cirillic delete with prefix"


code=$(delete_return_code "http://${HOST}:3002/graph/delete?repo=janni&path=testing/groupturtle")
assert_eq "$code" "200" "delete groupturtle"


