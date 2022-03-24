## Using

After the containers are up, the following services will be available:

* GIT File Browser http://localhost:3002/file
* SPARQL endpoint http://localhost:3002/sparql
* GSTORE http://localhost:3002/ with swagger documentation
    * GET /graph/read
    * DELETE /graph/delete
    * POST /graph/save
    * POST /dataid/tractate
    * POST /shacl/validate

### Example: Saving a file
```
curl -X 'POST'   'http://localhost:3002/file/save?repo=kurzum&path=example.jsonld'   -H 'accept: application/json'   -H 'Content-Type: application/ld+json'   -d '{
  "@context": "http://schema.org/",
  "@type": "Person",
  "name": "Jane Doe",
  "jobTitle": "Professor",
  "telephone": "(425) 123-4567",
  "url": "http://www.janedoe.com"
}'
```
### Example: Query over saved file
```
curl --data-urlencode query="SELECT * {GRAPH </kurzum/example.jsonld> {?s ?p ?o }}" http://localhost:3003/sparql
```

### Backup
To do a backup just run backup script in the container
```
./backup.sh <data directory> <URI of databus> <username> <api-token for the user> <DAV uri> <DAV user> <DAV password>

#for example:
./backup.sh ~/databus/data_root/ http://localhost:3000 databusadmin 8d0eb95f-e3ab-4916-bd5a-fb6599a841f6 http://localhost:3003/DAV dav dav
```
- \<username\> is the name of the user who will publish generated jsonld file
- running the `backup.sh` file will generate `bckp_${VERSION}.tar.gz` 
file and `backup.jsonld` metainformation file about the backup to publish 
in databus in the `$GIT_ROOT` directory


### External virtuoso and gitlab
- go to the directory of the project
- set the right configuration parameters in `src/main/webapp/WEB-INF/web.xml`
- run `docker build -t g-store-<version> .`
- run `docker run -p <out_port>:8080 [-d] g-store-<version>`

