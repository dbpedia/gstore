# G(it|raph) Store

A web service for retrieving, validating and storing rdf data (jsonld) in 
1. a SPARQL endpoint using filepath as graph 
2. a git-enabled storage.  

The G is an ORcronym for (git|graph).

## Overview
TODO architecture diagram showing the three components, maybe include a centralized and a de-centralized archticture view

## Running with docker-compose 
Compatible down to docker-compose version 1.25.0, [see here](https://docs.docker.com/compose/environment-variables/) for other ways to configure docker-compose >1.25.0

```
# clone repo
git clone https://github.com/dbpedia/gstore.git
cd gstore
# create folder (writable by all services)
mkdir databus
# run docker-compose 
docker-compose up --build

```

## Using

After the containers are up, the following services will be available:

* GIT File Browser http://localhost:3002/git
* Virtuoso SPARQL http://localhost:3003/sparql
* GSTORE http://localhost:3002/ with 
    * GET /file/read
    * POST /file/delete
    * POST /file/save
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



### Example 1 Saving a fil
### Example 1 Saving a fil
### Example 1 Saving a fil



## Dev build (running Virtuoso and Gstore scala code separately)
```
# clone repo
git clone https://github.com/dbpedia/gstore.git
cd gstore
# create folder (writable by all services)
mkdir databus

VIRT_PASS='everyoneknows'


# Virtuoso
docker run \
    -d
    --interactive \
    --tty \
    --env DBA_PASSWORD=$VIRT_PASS \
    --env VIRT_HTTPSERVER_SERVERPORT=3003 \
    --publish 1111:1111 \
    --publish 3003:3003 \
    --volume `pwd`/databus/virtuoso:/database \
    openlink/virtuoso-opensource-7:latest
    
# sbt build and run
sbt clean assembly
java -jar -DvirtuosoPass=$VIRT_PASS target/scala-2.12/gstore-assembly-0.2.0-SNAPSHOT.jar
```

## TODO explain local/remote virtuoso
Current version supports two configurations:
- with local git (default)
- with remote git based on gitlab. 
To enable remote git you need to go to `src/main/webapp/WEB-INF/web.xml`, 
comment out `localGitRoot` configuration parameter, and  
run docker-compose with merged `remote_git` config: `docker-compose -f docker-compose.yml -f docker-compose.remote_git.yml up --build`. 
Gitlab is then available on http://localhost:8880 

You can set following config paraters as env variables in docker for g-store container:
```
VIRT_URI=http://virtuoso:8890 #default, virtuoso base uri
VIRT_USER="" # virtuoso user
VIRT_PASS="" # virtuoso password
GIT_ROOT="" # root folder for git inside g-store container (recommended not to change)
```

## TODO explain local/remote virtuoso
### External virtuoso and gitlab
- go to the directory of the project
- set the right configuration parameters in `src/main/webapp/WEB-INF/web.xml`
- run `docker build -t g-store-<version> .`
- run `docker run -p <out_port>:8080 [-d] g-store-<version>`

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

