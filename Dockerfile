FROM hseeberger/scala-sbt:graalvm-ce-21.1.0-java8_1.5.1_2.12.13 AS build

COPY . /gstore
WORKDIR /gstore
RUN sbt 'set test in assembly := {}' clean assembly

FROM openjdk:8-alpine

ENV VIRT_URI=http://gstore-virtuoso:8890
ENV VIRT_USER=""
ENV VIRT_PASS=""
ENV GIT_LOCAL_DIR=""
ENV LOGS_FOLDER=/gstore/logs/
ENV GRAPH_ID_PREFIX=""

RUN apk update
RUN apk upgrade
RUN apk add bash

COPY --from=build /gstore/target/scala-2.12/gstore-assembly-0.2.0-SNAPSHOT.jar /app/app.jar

SHELL ["/bin/bash", "-c"]
CMD PREFIX_ARG=${GRAPH_ID_PREFIX:+"-DgraphidPrefix=$GRAPH_ID_PREFIX"}; \
    java -DvirtuosoUri=$VIRT_URI -DvirtuosoUser=$VIRT_USER -DvirtuosoPass=$VIRT_PASS -DgitLocalDir=$GIT_LOCAL_DIR -DlogsFolder=$LOGS_FOLDER $PREFIX_ARG -jar /app/app.jar
