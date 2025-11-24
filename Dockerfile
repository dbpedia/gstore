FROM sbtscala/scala-sbt:graalvm-ce-22.3.3-b1-java17_1.9.8_2.13.12 AS build

COPY . /gstore
WORKDIR /gstore
RUN sbt 'set test in assembly := {}' clean assembly

FROM eclipse-temurin:21-jre-alpine

ENV STORAGE_SPARQL_ENDPOINT_URI=http://gstore-virtuoso:8890/sparql
ENV STORAGE_USER=""
ENV STORAGE_PASS=""
ENV STORAGE_DB_NAME=""
ENV EXTRA_ROOT_CERT_PATH=""
ENV STORAGE_JDBC_PORT=1111
ENV RESTRICT_EDITS_TO_LOCALHOST=false
# other options: org.dbpedia.databus.FusekiJDBCClient, org.dbpedia.databus.HttpVirtClient
ENV STORAGE_CLIENT_CLASS=org.dbpedia.databus.VirtuosoJDBCClient

ENV GIT_LOCAL_DIR=""
ENV LOGS_FOLDER=/gstore/logs/
ENV GSTORE_LOG_LEVEL=INFO

ENV DEFAULT_JSONLD_LOCALHOST_CONTEXT=http://localhost:3000/res/context.jsonld
ENV DEFAULT_JSONLD_LOCALHOST_CONTEXT_LOCATION=https://databus.dbpedia.org/res/context.jsonld

RUN apk update
RUN apk upgrade
RUN apk add bash

COPY --from=build /gstore/target/scala-2.12/gstore-assembly-0.2.0-SNAPSHOT.jar /app/app.jar

SHELL ["/bin/bash", "-c"]
CMD if [[ -n "$EXTRA_ROOT_CERT_PATH" ]]; then \
      if [[ -f "$EXTRA_ROOT_CERT_PATH" ]]; then \
        echo "Checking if certificate is already installed..."; \
        if ! keytool -list -keystore "$JAVA_HOME/lib/security/cacerts" \
                     -storepass changeit -alias extra-root-cert > /dev/null 2>&1; then \
          echo "Installing extra root certificate from $EXTRA_ROOT_CERT_PATH"; \
          keytool -import -trustcacerts -keystore "$JAVA_HOME/lib/security/cacerts" \
                  -storepass changeit -noprompt -alias extra-root-cert -file "$EXTRA_ROOT_CERT_PATH"; \
        else \
          echo "Certificate with alias 'extra-root-cert' already installed, skipping."; \
        fi; \
      else \
        echo "WARNING: EXTRA_ROOT_CERT_PATH is set to '$EXTRA_ROOT_CERT_PATH' but file was not found."; \
      fi; \
    fi && \
    java \
      -Djavax.net.debug=ssl,handshake \
      -Dhttp.nonProxyHosts=$(echo $NO_PROXY | sed 's/,/|/g') \
      -Dhttps.nonProxyHosts=$(echo $NO_PROXY | sed 's/,/|/g') \
      -DdefaultJsonldLocalhostContext=$DEFAULT_JSONLD_LOCALHOST_CONTEXT \
      -DdefaultJsonldLocalhostContextLocation=$DEFAULT_JSONLD_LOCALHOST_CONTEXT_LOCATION \
      -DrestrictEditsToLocalhost=$RESTRICT_EDITS_TO_LOCALHOST \
      -Dgstore.log.level=$GSTORE_LOG_LEVEL \
      -DstorageDbName=$STORAGE_DB_NAME \
      -DstorageClass=$STORAGE_CLIENT_CLASS \
      -DstorageSparqlEndpointUri=$STORAGE_SPARQL_ENDPOINT_URI \
      -DstorageJdbcPort=$STORAGE_JDBC_PORT \
      -DstorageUser=$STORAGE_USER \
      -DstoragePass=$STORAGE_PASS \
      -DgitLocalDir=$GIT_LOCAL_DIR \
      -DlogsFolder=$LOGS_FOLDER \
      -jar /app/app.jar
