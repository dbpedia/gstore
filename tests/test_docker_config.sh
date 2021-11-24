#!/bin/bash
source ./custom-assert.sh
#source ./functions.sh


# Check general availability
code=$(get_return_code localhost:3002)
assert_eq "$code" "200" "G-Store reachable"

# Check virtuoso availability
code=$(get_return_code "localhost:3002/sparql?default-graph-uri=&query=ASK+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml" )
assert_eq "$code" "200" "Virtuoso reachable"

# TODO: Check virtuoso POST request



#result=$(curl https://databus.dbpedia.org/system/api/search?query=data -s)
#assert_not_eq "$result" "{\"docs\":[]}" "Nothing found for query 'data'"


