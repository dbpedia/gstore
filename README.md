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

- go to the directory of the project
- [optional] generate certificate and jks (if not yet generated) with 
`keytool -genkeypair -alias <keystore alias> -storepass <some_pass> -keypass <some_other_pass> -keyalg RSA -keystore databus_keystore.jks -storetype pkcs12`
- set the right configuration parameters in `src/main/webapp/WEB-INF/web.xml`
- run `sbt clean package`
- run `docker build -t databus-upload-<version> .`
- run `docker run -p <out_port>:8080 -d databus-upload-<version>`

