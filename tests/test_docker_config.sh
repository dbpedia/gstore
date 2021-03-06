#!/bin/bash -i
source ./custom-assert.sh
source ./functions.sh

HOST="${HOST:-localhost}"

# Check general availability
code=$(get_return_code ${HOST}:3002)
assert_eq "$code" "200" "$code G-Store reachable / TODO might need -L"
code=$(get_return_code ${HOST}:3002/)
assert_eq "$code" "200" "$code G-Store reachable"

# GRAPH Viewer
code=$(get_return_code "-L ${HOST}:3002/file")
assert_eq "$code" "200" "/file viewer reachable"
code=$(get_return_code ${HOST}:3002/file/)
assert_eq "$code" "200" "/file/ viewer reachable"

# Check SPARQL virtuoso availability
code=$(get_return_code "${HOST}:3002/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml" )
assert_eq "$code" "200" "Virtuoso SPARQL reachable via GET Jetty proxy"
code=$(get_return_code "-X POST ${HOST}:3002/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml")
assert_eq "$code" "200" "Virtuoso SPARQL reachable via POST Jetty proxy"
code=$(get_return_code "${HOST}:3003/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml" )
assert_eq "$code" "200" "Virtuoso SPARQL reachable via GET port 3003"
code=$(get_return_code "-X POST ${HOST}:3003/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml" )
assert_eq "$code" "200" "Virtuoso SPARQL reachable via GET port 3003"

# Check dav
code=$(get_return_code "${HOST}:3003/DAV" )
assert_eq "$code" "200" "Virtuoso DAV reachable via GET port 3003 / TODO untested since port not working"


#code=$(get_return_code ${HOST}:3002/shacl)
#assert_eq "$code" "200" "$code G-Store reachable"
