source ./custom-assert.sh
source ./functions.sh

HOST="${HOST:-localhost}"

#get_post_return_code() {
#  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s"
#  echo $(curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s)
#}

code=$(post_return_code_contenttype_applicationldjson  "http://${HOST}:3002/graph/save?repo=janni&path=testing/empty_post.jsonld" @./data/none )
assert_eq "$code" "400" "empty post"

# Writing to the gstore for failing input (22.11.2021): virtuoso.jdbc4.VirtuosoException: SQ200: No column NaN.
code=$(post_return_code_contenttype_applicationldjson  "http://${HOST}:3002/graph/save?repo=janni&path=testing/no-column-nan.jsonld" @./data/fail_datatype_no-column-nan.jsonld )
assert_eq "$code" "400" "Writing No column NaN to GStore"

code=$(post_return_code_contenttype_applicationldjson  "http://${HOST}:3002/graph/save?repo=janni&path=testing/syntax_error.jsonld" @./data/fail_syntax_group.jsonld )
assert_eq "$code" "400" "code: $code, upload json file with syntax error"

