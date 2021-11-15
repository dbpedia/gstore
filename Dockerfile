FROM hseeberger/scala-sbt:graalvm-ce-21.1.0-java8_1.5.1_2.12.13 AS build

COPY . /databus   
WORKDIR /databus   
RUN sbt 'set test in assembly := {}' clean assembly

FROM openjdk:8-alpine

ENV GSTORE_BASE_DIR=/databus 
ENV GSTORE_PORT=3002
ENV GIT_LOCAL_DIR=/databus/git 
ENV VIRT_URI="http://localhost:3003"
ENV VIRT_USER=dba
ENV VIRT_PASS=everyoneknows

RUN apk update
RUN apk upgrade
RUN apk add tar outils-sha256 gawk bash curl nginx
RUN mkdir /run/nginx

#COPY --from=build /gstore/target/scala-2.12/gstore-assembly-0.2.0-SNAPSHOT.jar /app/app.jar
COPY --from=build /databus/target/scala-2.12/gstore-assembly-0.2.0-SNAPSHOT.jar /app/app.jar



SHELL ["/bin/bash", "-c"]
CMD echo -e "events {\n\
      worker_connections  4096;\n\
    }\n\
    http {\n\
        resolver 127.0.0.1 ipv6=off;\n\
        server { # simple reverse-proxy\n\
            listen       0.0.0.0:80;\n\
            add_header Access-Control-Allow-Origin *;\n\
            location / {\n\
                proxy_pass      http://127.0.0.1:8080;\n\
            }\n\
            location /sparql {\n\
                proxy_pass      ${VIRT_URI}/sparql;\n\
            }\n\
            location /DAV {\n\
                proxy_pass      ${VIRT_URI}/DAV;\n\
            }\n\
        }\n\
    }\n" > /etc/nginx/nginx.conf ; nginx ; java -DbaseDir=$GSTORE_BASE_DIR -DgitLocalDir=$GIT_LOCAL_DIR -DvirtuosoUri=$VIRT_URI/sparql-auth  -DvirtuosoUser=$VIRT_USER -DvirtuosoPass=$VIRT_PASS -jar /app/app.jar
