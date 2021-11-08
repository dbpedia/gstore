# G(it|raph) Store

A web service for retrieving, validating and storing rdf data (jsonld) in 
1. a SPARQL endpoint using filepath as graph 
2. a git-enabled storage.  

The G is an ORcronym for (git|graph).

## Overview
TODO architecture diagram showing the three components, maybe include a centralized and a de-centralized archticture view

## Running
### docker-compose 
Compatible down to docker-compose version 1.25.0, [see here](https://docs.docker.com/compose/environment-variables/) for other ways to configure docker-compose >1.25.0

```
git clone https://github.com/dbpedia/gstore.git
cd gstore

echo "
VIRT_HTTPSERVER_SERVERPORT=3003
VIRT_URI=http://gstore-virtuoso:${VIRT_HTTPSERVER_SERVERPORT}
VIRT_PASS='everyoneknows'
LOCAL_GIT_PATH='`pwd`/databus' # root folder for git
"> .myenv

docker-compose --env-file .myenv up --build

```
### dev build and run
```
git clone https://github.com/dbpedia/gstore.git
cd gstore

VIRT_HTTPSERVER_SERVERPORT=3003
VIRT_PASS='everyoneknows'
LOCAL_GIT_PATH='' # root folder for git
VIRT_URI=http://localhost:${VIRT_HTTPSERVER_SERVERPORT}
GSTOREPATH=`pwd`"/databus"

# Virtuoso
docker run \
    --interactive \
    --tty \
    --env DBA_PASSWORD=$VIRT_PASS \
    --env VIRT_HTTPSERVER_SERVERPORT=$VIRT_HTTPSERVER_SERVERPORT \
    --publish 1111:1111 \
    --publish  $VIRT_HTTPSERVER_SERVERPORT:$VIRT_HTTPSERVER_SERVERPORT \
    --volume $GSTOREPATH/virtuoso:/database \
    openlink/virtuoso-opensource-7:latest
    
# sbt build and run
sbt clean assembly
java -DvirtuosoUri=$VIRT_URI/sparql-auth -DvirtuosoPass=$VIRT_PASS -DlocalGitRoot=$GSTOREPATH/git -jar target/scala-2.12/databus-dataid-repo-assembly-0.2.0-SNAPSHOT.jar
```
## Using

After the containers are up, the g-store is available on: http://localhost:8088/;
virtuoso is on: http://localhost:8088/sparql. You can also view file structure at http://localhost:8088/git.



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

