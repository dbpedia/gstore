source ./custom-assert.sh

get_post_return_code() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s"
  echo $(curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s)
}

# Writing to the gstore for failing input (22.11.2021): virtuoso.jdbc4.VirtuosoException: SQ200: No column NaN.
code=$(get_post_return_code "http://localhost:3002/file/save?repo=testing&path=test/no-column-nan.jsonld" @data/no-column-nan.jsonld)
assert_eq "$code" "200" "Writing to G-Store successful"
