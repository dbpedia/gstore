# G(raph) Store

A web service for storing and retrieving rdf data (jsonld), has SPARQL endpoint and git-enabled storage.  

## Deployment
### docker-compose

- go to the directory of the project
- run `docker-compose up --build`

After the containers are up, the g-store is available on: http://localhost:8088/;
virtuoso is on: http://localhost:8088/sparql. You can also view file structure at http://localhost:8088/git.

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

### External virtuoso and gitlab
- go to the directory of the project
- set the right configuration parameters in `src/main/webapp/WEB-INF/web.xml`
- run `docker build -t g-store-<version> .`
- run `docker run -p <out_port>:8080 [-d] g-store-<version>`

### Backup
To do a backup just run backup script in the container
```
docker exec <gstore container id> /backup.sh <URI of databus> <username>

#for example:
docker exec <gstore container id> /backup.sh http://localhost:3000 databusadmin
```
- \<username\> is the name of the user who will publish generated jsonld file
- running the `backup.sh` file will generate `bckp_${VERSION}.tar.gz` 
file and `backup.jsonld` metainformation file about the backup to publish 
in databus in the `$GIT_ROOT` directory

