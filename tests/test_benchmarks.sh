source ./custom-assert.sh

echo 'Running benchmark tests...'

# 100 request with concurreny level 10

# SPARQL endpoint
ab -n 100 -c 10 "http://localhost:3002/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml"

# Write
ab -p ./data/api-functionality/basic.jsonld -T application/json+ld -n 100 -c 10 "http://localhost:3002/file/save?repo=testing&path=/test/document.jsonld"

# Read
ab -n 100 -c 10 "http://localhost:3002/file/read?repo=testing&path=/test/document.jsonld"
# git log | grep "commit " -c

# Delete


echo 'Done running benchmark tests.'
