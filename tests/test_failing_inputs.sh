source ./custom-assert.sh

#get_post_return_code() {
#  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s"
#  echo $(curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s)
#}

# Writing to the gstore for failing input (22.11.2021): virtuoso.jdbc4.VirtuosoException: SQ200: No column NaN.
code=$(get_return_code "-X POST http://localhost:3002/file/save?repo=testing&path=test/no-column-nan.jsonld -d '@data/failing/no-column-nan.jsonld' -H 'Content-Type:application/json+ld' " )
assert_eq "$code" "200" "Writing No column NaN to GStore"


code=`get_return_code \
"http://localhost:3002/file/save?repo=testing&path=/test/document.jsonld -H 'Accept:application/json'   -H 'Content-Type:application/ld+json' -d '@./data/api-functionality/basic_w_syntax_errors.jsonld'"  `
assert_eq "$code" "400" " http code: $code, upload file with syntax error"
exit

code=`curl 'http://localhost:3002/file/save?repo=testing&path=/test/document.jsonld' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/ld+json' \
  -d @./data/api-functionality/basic_w_syntax_errors.jsonld \
  -o /dev/null -w '%{http_code}\n' -s `
code=`get_return_code "http://localhost:3002/file/save?repo=testing&path=/test/document.jsonld'   -H 'accept: application/json' -H 'Content-Type: application/ld+json' -d @./data/api-functionality/basic_w_syntax_errors.jsonld" `
assert_eq "$code" "400" " http code: $code, upload file with syntax error"


echo ; echo 

code=`curl -f -Li 'http://localhost:3002/file/save?repo=testing&path=/test/document.jsonld' -H 'accept: application/json' -H 'Content-Type: application/ld+json' -d '@./data/api-functionality/basic_w_syntax_errors.jsonld' -o /dev/null -w %{http_code} -s`
assert_eq "$code" "400" " http code: $code, upload file with syntax error"
