#!/bin/sh

get_return_code() {
  >&2 echo "Testing: $1" 	
  echo $(curl -Li $1 -o /dev/null -w '%{http_code}\n' -s)
}

