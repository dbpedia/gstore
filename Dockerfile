FROM sbtscala/scala-sbt:graalvm-ce-22.3.3-b1-java17_1.9.8_2.13.12 AS build

COPY . /gstore
WORKDIR /gstore
RUN sbt 'set test in assembly := {}' clean assembly

FROM eclipse-temurin:21-jre-alpine

ENV STORAGE_SPARQL_ENDPOINT_URI=http://gstore-virtuoso:8890/sparql
ENV STORAGE_USER=""
ENV STORAGE_PASS=""
ENV STORAGE_DB_NAME=""
ENV STORAGE_JDBC_PORT=1111
ENV RESTRICT_EDITS_TO_LOCALHOST=false
# other options: org.dbpedia.databus.FusekiJDBCClient, org.dbpedia.databus.HttpVirtClient
ENV STORAGE_CLIENT_CLASS=org.dbpedia.databus.VirtuosoJDBCClient

ENV GIT_LOCAL_DIR=""
ENV LOGS_FOLDER=/gstore/logs/
ENV GSTORE_LOG_LEVEL=INFO

RUN apk update
RUN apk upgrade
RUN apk add bash

COPY --from=build /gstore/target/scala-2.12/gstore-assembly-0.2.0-SNAPSHOT.jar /app/app.jar

SHELL ["/bin/bash", "-c"]
CMD java -DrestrictEditsToLocalhost=$RESTRICT_EDITS_TO_LOCALHOST -Dgstore.log.level=$GSTORE_LOG_LEVEL -DstorageDbName=$STORAGE_DB_NAME -DstorageClass=$STORAGE_CLIENT_CLASS -DstorageSparqlEndpointUri=$STORAGE_SPARQL_ENDPOINT_URI -DstorageJdbcPort=$STORAGE_JDBC_PORT -DstorageUser=$STORAGE_USER -DstoragePass=$STORAGE_PASS -DgitLocalDir=$GIT_LOCAL_DIR -DlogsFolder=$LOGS_FOLDER -jar /app/app.jar
