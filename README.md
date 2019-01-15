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

### Create the .war file

[Install SBT on Linux](https://www.scala-sbt.org/1.0/docs/Installing-sbt-on-Linux.html)

This project uses [SBT](https://www.scala-sbt.org/documentation.html). 

Use the following command to create a WAR archive:

```sbt package```

It will be generated under `target/scala-2.12/databus-dataid-repo_2.12-$VERSION.war`.


### Setup Tomcat and AJP

#### setup writable directories for storing the dataids
```
sudo mkdir /opt/dataid-repo/
sudo mkdir /opt/dataid-repo/file-storage
sudo chown tomcat8:tomcat8 /opt/dataid-repo/
sudo chown tomcat8:tomcat8 /opt/dataid-repo/file-storage
```

#### copy the config file
`cp ./src/main/resources/dataid-repo.conf /opt/dataid-repo/dataid-repo.conf`
NOTE: needs to be readable by tomcat

#### Install tomcat8 and declare context

1. `sudo apt-get install tomcat8`
2. create a file in `sudo nano /etc/tomcat8/Catalina/localhost/dataid-repo.xml` ```
<?xml version="1.0" encoding="UTF-8"?>
<Context path="/dataid-repo" 
	 docBase="/var/lib/tomcat8/webapps/dataid-repo.war">
  <Parameter name="org.dbpedia.databus.dataidrepo.config" value="/opt/dataid-repo/dataid-repo.conf"/>
</Context>
```
#### Deploy the .war file
The `dataid-repo.xml`  specifies, that you can copy the .war file to `/var/lib/tomcat8/webapps/dataid-repo.war`
`sudo cp -v target/scala-2.12/databus-dataid-repo_2.12-*.war /var/lib/tomcat8/webapps/dataid-repo.war`
Tomcat will then autoload it, check at `http://localhost:8080/manager/html/`

#### Enable AJP on tomcat
https://en.wikipedia.org/wiki/Apache_JServ_Protocol
Make sure the following line is uncommented:
```
    <!-- Define an AJP 1.3 Connector on port 8009 -->

    <Connector port="8009" protocol="AJP/1.3" redirectPort="8443" />
```

### Install and configure Apache HTTP with proxy, headers, ssl, proxy_ajp
```
sudo apt-get install apache2
sudo a2enmod headers ssl proxy proxy_ajp	
```

#### Configure
To be added to sites-available/sites-enabled
above `/dataid-repo` was given as context for the war on tomcat, apache2 needs to proy it like this:
```
ProxyPassMatch "^/PUBLICREPONAME/(.*)$" "ajp://localhost:8009/dataid-repo/$1"
```
Furthermore the following lines help to set up ssl and other things:
```
	Listen 443
	<VirtualHost _default_:443>
		ServerName localhost
   		ErrorLog /var/log/apache2/ajp.error.log
   		CustomLog /var/log/apache2/ajp.log combined
		# for debugging LogLevel trace5
		ServerAdmin webmaster@localhost
		SSLEngine on
		SSLVerifyClient optional_no_ca
   		SSLVerifyDepth 1
   		SSLOptions +StdEnvVars +ExportCertData

		# request headers give better info later
   		RequestHeader unset X-SSL-Cipher
   		RequestHeader unset X-Client-Cert
    
		<LocationMatch "^/repo">
       		RequestHeader set X-SSL-Cipher "%{SSL_CIPHER}s"
       		RequestHeader set X-Client-Cert "%{SSL_CLIENT_CERT}s"
   		</LocationMatch>

		# NOTE repo is the public name here
		ProxyPassMatch "^/repo/(.*)$" "ajp://localhost:8009/dataid-repo/$1"

		# this is the fake cert, use the real one later
		SSLCertificateFile	/etc/ssl/certs/ssl-cert-snakeoil.pem
		SSLCertificateKeyFile /etc/ssl/private/ssl-cert-snakeoil.key
```

## Debug help

### verifying the AJP proxy from Apache HTTPD to Tomcat:
```
curl -v -k --cert ~/.ssh/webid_cert/certificate.pem --cert-type PEM --key  ~/.ssh/webid_cert/private_key_webid.pem --key-type PEM   https://localhost/repo/client-cert-info
curl -v -k --cert ~/.ssh/webid_cert/certificate.pem --cert-type PE-key  ~/.ssh/webid_cert/private_key_webid.pem --key-type PEM   https://databus.dbpedia.org/repo/client-cert-info
```

### the tomcat Log
to check whether the war file is deployed

```
DATE=`date +%Y-%m-%d`
tail -f  /var/log/tomcat8/catalina.$DATE.log
```

to check the log messages from the service
```
DATE=`date +%Y-%m-%d`
tail -f  /var/log/tomcat8/localhost.$DATE.log
```

### Apache logs
```
tail -f  /var/log/apache2/ajp.error.log
tail -f  /var/log/apache2/ajp.log
```

## Continuous Deployment during development

The SBT task `packageAndDeployToTomcat` can be used to have a packaged WAR copied to the place specified
as `docBase` for a Tomcat context. Just specify the target location via the `warTargetLocation` setting key.
Since Tomcat by default will re-deploy a context once it notices a new version of the WAR, combining this with
the continuous building feature of the SBT shell enables one to test the effect of code changes a few
seconds after they were saved/`rsync`ed to the projects `src` directory:

TODO this command does not work....
`sbt:databus-dataid-repo> ~packageAndDeployToTomcat`
~ -> continousbuilding
`sbt ~packageAndDeployToTomcat`

## Configuration

The application requires configuration specified in a 
[HOCON](https://github.com/lightbend/config/blob/master/HOCON.md) file. The service will retrieve
the filesystem path to read the config from a 
[Servlet init parameter](https://docs.oracle.com/javaee/7/api/javax/servlet/ServletConfig.html#getInitParameter-java.lang.String-)
with the key `org.dbpedia.databus.dataidrepo.config`.  

`src/main/resources/dataid-repo.conf` can be used as a template for a HOCON configuration file. 
  There are the following configuration keys:

* `persistence.strategy`: Choice of strategy to persist submitted DataIDs. Currently only the option 
  `Filesystem` is operational. (There exists also a `TDB` to store the date in with an embedded Jena 
   TDB instance into a native file-based store, but there are unresolved file-locking issues when
   using that in a multi-threaded Servlet setting.)
   
* `persistence.fileSystemStorageLocation`: A filesystem path to the directory where submitted DataIDs
  are to be stored. NB: The deployed service will need to be able to write into that directory and 
  be able to crate sub-directories in it. This will usually mean that the user/group associated with
  the application container in usage will need write permissions (e.g. `tomcat8`) or `drwxr-xrwx  2 tomcat8 tomcat8  4096 Jan 11 12:38 file-storage`.
  
  
### Reload config
Touch the war file

## Authentication with WebID / Client Certificate

This service is developed to be deployed as Servlet in an Apache Tomcat application container.
Certain possible operations require a client certificate to be presented with the HTTPS request
for authentication and authorization purpose (WebID). The setup tested and currently in use employs
Apache HTTPD as reverse proxy that transfers the client certificate details to Tomcat via
the AJP protocol (`mod_proxy_ajp`). This allow the Scalatra App to extract client certificate 
information via the regular Servlet 3.x API.

As currently this way to receive client certificate information is specific to AJP, usage of 
other application containers (e.g. Jetty) or other HTTP server solutions in front of the 
application container (e.g. nginx) is not supported and might require non-trivial changes.
 
## 
* java.lang.StringIndexOutOfBoundsException: String index out of range: -1
* mvn deploy -> 
** enablePlugins(SbtTwirl)
** enablePlugins(ScalatraPlugin) 
* config file? 



