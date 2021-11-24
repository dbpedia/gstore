source ./custom-assert.sh

# Writing to the gstore
code=$(get_return_code '-d @data/basic.jsonld -H "Content-Type: application/json+ld" "http://localhost:3002/file/save?repo=testing&path=test/basic.jsonld"')
assert_eq "$code" "200" "Writing to G-Store successful"
