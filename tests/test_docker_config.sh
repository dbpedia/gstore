#!/bin/bash
source ./custom-assert.sh
#source ./functions.sh


# Check general availability
code=$(get_return_code localhost:3002)
assert_eq "$code" "200" "$code G-Store reachable"
code=$(get_return_code localhost:3002/)
assert_eq "$code" "200" "$code G-Store reachable"
code=$(get_return_code localhost:3002/shacl)
assert_eq "$code" "200" "$code G-Store reachable"

# GIT File Viewer
code=$(get_return_code localhost:3002/git)
assert_eq "$code" "200" "/git/ viewer reachable"
code=$(get_return_code localhost:3002/git/)
assert_eq "$code" "200" "/git/ viewer reachable"


# Check virtuoso availability
code=$(get_return_code "localhost:3002/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml" )
assert_eq "$code" "200" "Virtuoso SPARQL reachable via Jetty proxy"

code=$(get_return_code "localhost:3003/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml" )
assert_eq "$code" "200" "Virtuoso SPARQL reachable via port 3003"




# TODO: Check virtuoso POST request



#result=$(curl https://databus.dbpedia.org/system/api/search?query=data -s)
#assert_not_eq "$result" "{\"docs\":[]}" "Nothing found for query 'data'"


