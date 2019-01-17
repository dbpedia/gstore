# Digital Factory Platform VOS Loading #

Daemon service, which inspects a directory for files submitted to the Databus repo to be loaded 
into a Virtuoso Open Source instance.

## Workflow

The service monitors the `persistence.fileSystemStorageLocation` directory. 
When it finds new files there, it processes them and 
puts them first into `{loading.vosQueuesParentDir}/loading` and then either in `failed` or `loaded`.

## Setup Docker for Virtuoso

This service has been developed and tested against VOS 7.2.4, specifically, the Docker container
`tenforce/virtuoso:1.3.1-virtuoso7.2.4`. It packages and uses the Jena connector libraries from
OpenLink as packaged in that Docker image (`virtjdbc4_2ssl.jar`, `lib/virt_jena3.jar`). The 
connector might be upwards compatible to following minor revisions of VOS, but your mileage may 
vary. If the service is to operate with newer VOS versions, it is recommendable to replace the 
connector jars in the `lib/` folder to the corresponding new connectors as well. 

The quick and reliable way to provide a suitable VOS instance is to copy and adjust 
`docker-compose.yml-TEMPLATE` to a `docker-compose.yml` file and then to spin up a docker container
with `docker-compose up -d`.

`sudo apt-get install docker.io`
`sudo usermod -aG docker shellmann`
`newgrp docker`
`newgrp shellmann`
`docker exec -ti $NAME bash`
`isql-v $PORT`



#### Explanations of the VOS Virt Jena Drivers as Unmanaged Dependencies

All `jar`-files in the `lib/` project-subdirectory are treated by SBT by default as 
[unmanaged dependencies](https://www.scala-sbt.org/1.x/docs/Library-Dependencies.html#Unmanaged+dependencies).
Thus they will be added directly (and without resolution of potential transitive dependencies) 
to the project classpath.

This service requires the `virtjdbc4_2ssl.jar` and `virt_jena3.jar` from the same VOS 
distribution or installation available in the `lib/` directory. Within the VOS Docker 
images from tenforce, these files are located at:
```
/usr/local/virtuoso-opensource/lib/jdbc-4.2/virtjdbc4_2ssl.jar
/usr/local/virtuoso-opensource/lib/jena3/virt_jena3.jar 
```

The VOS JDBC drivers use the same ODBC interface as the ISQL command line tool `isql-v` does.
Thus, the access to VOS for this service should work with any VOS instance built with typical
compile flags, as long as the versions of the VOS server and the driver files match sufficiently.

## Building and Packaging

This project uses [SBT](https://www.scala-sbt.org/documentation.html). Use `sbt compile` to build
the bytecode.

[SBT Native Packager](https://www.scala-sbt.org/sbt-native-packager/index.html) can be used to create
a `zip` or `tgz` archive-distributions of this project that can be deployed and run on environments without
SBT: `sbt universal:packageBin` (for `zip`-archives) or `sbt universal:packageZipTarball` 
(for `tgz-archives`). 


## Running and Stopping

#### Running in SBT

`sbt run [path-to-config-file]`

JVM-arguments for the loading process need to be adjusted in `build.sbt` (`run / javaOptions`).

#### Running the distribution archives by Native Packager

Unpack the archive and `cd` into the folder with its contents, then:
```./bin/databus-dataid-vos-loader [path-to-config-file]```

The start scripts hands over `-D` switches to `java` to adjust system properties, e.g.:
```./bin/databus-dataid-vos-loader -Dlog4j.configurationFile=/tmp/log4j2.xml [path-to-config-file]```

#### Stopping the Loading Process

The service creates on startup the file `{loading.vosQueuesParentDir}/loading-active.flag` 
and monitors continuously whether it still exists. Once the service notices the absence of this 
file, it will start a graceful shutdown. Thus, deleting this flag file is the method to preferred 
over `kill`-signals to end the loading process.

## Config Options
An instance of this service requires a [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md)
file specifying where to look for files to load, where to move them when loading succeeded (or failed)
and how to connect to the VOS instance to load the data into.

`src/main/resources/vosloader.conf-TEMPLATE` gives an example/template for such a configuration file
to be adjusted to the actual environment.

There are the following configuration keys:

`loading.vosQueuesParentDir`: The parent dir for the working directories of the service. Subdirectories for
loads in progress, successfully loaded documents and for failures at loading attempts will be created beneath.

`loading.loadingInterval`: The interval determining how frequent the service will look in 
  `persistence.fileSystemStorageLocation` to collect new documents to load by moving them to its working 
   directories
  
`loading.minUnmodifiedDurationInStorage`: The service will not collect documents for loading that have been
   too recently to avoid spurious fluctuations in case of rapid re-enqueueing of the same documents. This 
   setting determines the minimum amount of time that a document has to remain unmodified in the queue before  
   it can be collected for loading
   
`persistence.fileSystemStorageLocation`: The directory that will be searched for additional/updated RDF documents
to load.

`virtuoso.{host,user,password}`: Connection details for the VOS instance to load into

`virtuoso.{concurrentConnectionsToProcessorsRatio,maxConcurrentConnections}`: These to settings determine how
many concurrent VOS sessions can be used to load documents. The ratio will be multiplied with the number of
(virtual) processor cores detected by the JVM, the resulting number is then capped by `maxConcurrentConnections`



