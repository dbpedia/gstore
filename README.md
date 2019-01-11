# Digital Factory Platform (Server-Side Services) #

This repositories bundles three source code trees for services for the Digital
Factory Platform:

* a Scalatra-based REST-service for uploading and managing DataIds to appear in
 the DFP repo (the `scala` subdirectory)
* an internal daemon service that inspects a directory for files to be loaded 
 into a Virtuoso Open Source instance (the `scala/vos-load` subdirectory)
* a deprecated early PHP project for uploading DataIds (the `upload` folder)

Please refer for details to the `README.md` files in the corresponding 
subdirectories.
# Digital Factory Platform DataID Repo #

This Scalatra-based REST-service allows to submit DataIDs to be loaded into the central
DFP repos. Other management and reporting features pertaining these repos and contained 
DataIDs are planned.

## Building

This project uses [SBT](https://www.scala-sbt.org/documentation.html). 
Use the following command to create a WAR archive:

```sbt package```

It will be generated under `scala/target/scala-2.12`.

## Deployment

This service is developed to be deployed as Servlet in an Apache Tomcat application container.
Certain possible operations require a client certificate to be presented with the HTTPS request
for authentication and authorization purpose (WebID). The setup tested and currently in use employs
Apache HTTPD as reverse proxy that transfers the client certificate details to Tomcat via
the AJP protocol (`mod_proxy_ajp`). This allow the Scalatra App to extract client certificate 
information via the regular Servlet 3.x API.

As currently this way to receive client certificate information in specific to AJP, usage of 
other application containers (e.g. Jetty) or other HTTP server solutions in front of the 
application container (e.g. nginx) is not supported and might require non-trivial changes.
 
The application requires configuration specified in a 
[HOCON](https://github.com/lightbend/config/blob/master/HOCON.md) file. The service will retrieve
the filesystem path to read the config from a 
[Servlet init parameter](https://docs.oracle.com/javaee/7/api/javax/servlet/ServletConfig.html#getInitParameter-java.lang.String-)
with the key `org.dbpedia.databus.dataidrepo.config`. Here is an example snippet for a Tomcat 
`<Context/>` configuration element specifying the configuration path:

```
<?xml version="1.0" encoding="UTF-8"?>
<Context path="/dataid-repo" docBase="/var/lib/tomcat8/webapps/dataid-repo.war">

  <Parameter name="org.dbpedia.databus.dataidrepo.config" value="/data/dataid-repo/dataid-repo.conf"/>
</Context>

```
(A typical place to put this could be for example be `/etc/tomcat8/Catalina/localhost/dataid-repo.xml`
 in an Ubuntu installation.)

`src/main/resources/dataid-repo.conf` can be used as a template for a HOCON configuration file. 
  There are the following configuration keys:

`persistence.strategy`: Choice of strategy to persist submitted DataIDs. Currently only the option 
  `Filesystem` is operational. (There exists also a `TDB` to store the date in with an embedded Jena 
   TDB instance into a native file-based store, but there are unresolved file-locking issues when
   using that in a multi-threaded Servlet setting.)
   
`persistence.fileSystemStorageLocation`: A filesystem path to the directory where submitted DataIDs
  are to be stored. NB: The deployed service will need to be able to write into that directory and 
  be able to crate sub-directories in it. This will usually mean that the user/group associated with
  the application container in usage will need write permissions (e.g. `tomcat8`).


#### Continuous Deployment during development

The SBT task `packageAndDeployToTomcat` can be used to have a packaged WAR copied to the place specified
as `docBase` for a Tomcat context. Just specify the target location via the `warTargetLocation` setting key.
Since Tomcat by default will re-deploy a context once it notices a new version of the WAR, combining this with
the continuous building feature of the SBT shell enables one to test the the effect of code changes a few
seconds after they were saved/`rsync`ed to the projects `src` directory:

`sbt:databus-dataid-repo> ~packageAndDeployToTomcat`
