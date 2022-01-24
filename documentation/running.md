## Running with docker-compose 
Compatible down to docker-compose version 1.25.0, [see here](https://docs.docker.com/compose/environment-variables/) for other ways to configure docker-compose >1.25.0

```
# clone repo
git clone https://github.com/dbpedia/gstore.git
cd gstore
# create folder (writable by all services)
mkdir databus
# run docker-compose (builds new image from the sources)
docker-compose up --build
```