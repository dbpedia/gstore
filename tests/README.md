# Tests

## Issues

* **White space** - custom-assert cannot yet handle whitespace in curl `-H Accept: `, work around is no whitespace `-H "Accept:application/json"`. 
* usage of **"** note the " inside the "
```
code=`get_return_code  "http://localhost:3002/file/save?repo=testing&path=/test/document.jsonld \
  -H "Accept:application/json" \
  -H "Content-Type:application/ld+json" \
  -d @./data/func_basic.jsonld " `
```
## Categories

* **Docker Config** - Tests things like port and availability of docker-compose up
* **API Functionality** - Testing a series of write and then read operations on the GStore
* **API Accept/Content-Type** - Tests specifically for correct HTTP headers and formats
* **Benchmark** - High load testing
* **Failing Inputs** - Submits a series of inputs that should fail

## Other

* Saving of `dataid_01.jsonld` failing with `virtuoso.jdbc4.VirtuosoException: SQ200: No column NaN.`
