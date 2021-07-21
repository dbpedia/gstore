# Digital Factory Platform (Server-Side Services) #

Contents:

* (main) a Scalatra-based REST-service for uploading and managing DataIds to appear in
 the DFP repo 
* `vos-loader` see `vos-loader/README.md` an internal daemon service that inspects a directory for files to be loaded 
 into a Virtuoso Open Source instance
* `old-upload-prototype` a deprecated early PHP project for uploading DataIds 


# REST Service for DataID Upload to DFP #

This Scalatra-based REST-service allows to submit DataIDs to be loaded into the central
DFP repos. Other management and reporting features pertaining these repos and contained 
DataIDs are planned.

## Deployment
### docker-compose

- go to the directory of the project
- run `docker-compose up --build`

After the containers are up, the databus is available on: http://localhost:8088/;
virtuoso is on: http://localhost:8890/sparql-auth

Current version supports two configurations:
- with local git (default)
- with remote git based on gitlab. 
To enable remote git you need to go to `src/main/webapp/WEB-INF/web.xml`, 
comment out `localGitRoot` configuration parameter, and  
run docker-compose with merged `remote_git` config: `docker-compose -f docker-compose.yml -f docker-compose.remote_git.yml up --build`. 
Gitlab is then available on http://localhost:8880 

You can set configuration parameters passing `JAVA_PRMS` environment variable to databus' docker container. You need to specify `JAVA_PRMS` variable with values in java vm arguments style (parameter names must be the same as in the `web.xml`), for example:
```
JAVA_PRMS=-DgitHost=bestgitlabhost -DgitApiUser=toor
```

### External virtuoso and gitlab
- go to the directory of the project
- set the right configuration parameters in `src/main/webapp/WEB-INF/web.xml`
- run `docker build -t databus-upload-<version> .`
- run `docker run -p <out_port>:8080 [-d] databus-upload-<version>`

