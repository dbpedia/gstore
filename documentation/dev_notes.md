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
