source ./custom-assert.sh

echo 'Running benchmark tests...'

# 100 request with concurreny level 10

# SPARQL endpoint
ab -n 100 -c 10 "http://localhost:3002/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml"

# Write
ab -p ./data/func_group.jsonld -T application/ld+json -n 100 -c 10 "http://localhost:3002/file/save?repo=janni&path=/testing/group.jsonld"

# Read
ab -n 100 -c 10 "http://localhost:3002/file/read?repo=janni&path=/testing/group.jsonld"
# git log | grep "commit " -c

# Delete


echo 'Done running benchmark tests.'
