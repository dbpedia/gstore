#!/bin/bash
source ./custom-assert.sh
source ./functions.sh

SHACLFILE="../src/test/resources/test.shacl"

shacl_body () {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s -X POST \"localhost:3002/shacl/validate\" -H \"Content-Type: multipart/form-data\" -F \"shacl=@$SHACLFILE\" -F $1"
  echo $(curl -s -X POST localhost:3002/shacl/validate -H "Content-Type: multipart/form-data" -F "shacl=@$SHACLFILE" -F $1  )
}

shacl_code () {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s -X POST \"localhost:3002/shacl/validate\" -H \"Content-Type: multipart/form-data\" -F \"shacl=@$SHACLFILE\" -F $1 -o /dev/null -w %{http_code}"
  echo $(curl -s -X POST localhost:3002/shacl/validate -H "Content-Type: multipart/form-data" -F "shacl=@$SHACLFILE" -F $1 -o /dev/null -w %{http_code} )
}

shacl_contenttype () {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s -X POST \"localhost:3002/shacl/validate\" -H \"Content-Type: multipart/form-data\" -F \"shacl=@$SHACLFILE\" -F $1 -o /dev/null -w %{content_type}"
  echo $(curl -s -X POST localhost:3002/shacl/validate -H "Content-Type: multipart/form-data" -F "shacl=@$SHACLFILE" -F $1 -o /dev/null -w %{content_type} )
}

#

# SHACL
echo "
## input correct & valid"
code=$(shacl_code "graph=@data/func_group.jsonld")
assert_eq "$code" "200" "http code, $code SHACL against valid group file."

code=$(shacl_body "graph=@data/func_group.jsonld")
assert_eq "$code" "{\"code\":200}" "response body, $code SHACL against valid file"

echo "
## input correct & invalid"
code=$(shacl_code "graph=@data/shacl_invalid.jsonld")
assert_eq "$code" "200" "http code, $code SHACL against invalid file"

code=$(shacl_body "graph=@data/shacl_invalid.jsonld")
assert_eq "$code" "???" "http code, $code SHACL against invalid file. TODO what does the output look like?"

echo "
## incorrect & ??" 

code=$(shacl_code "graph=@data/fail_syntax_group.jsonld")
assert_eq "$code" "400" "http code, $code SHACL against syntax error file"





