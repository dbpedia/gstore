source ./custom-assert.sh

# Writing to the gstore for failing input (22.11.2021): virtuoso.jdbc4.VirtuosoException: SQ200: No column NaN.
code=$(get_return_code "-d @data/no-column-nan.jsonld -H \"Content-Type:application/json+ld\" \"http://localhost:3002/file/save?repo=testing&path=test/no-column-nan.jsonld\"")
assert_eq "$code" "200" "Writing to G-Store successful"
