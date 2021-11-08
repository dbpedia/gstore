FROM hseeberger/scala-sbt:graalvm-ce-21.1.0-java8_1.5.1_2.12.13 AS build

ENV

# TODO this probably copies the mounted databus dir
COPY . /gstore
WORKDIR /gstore
RUN sbt 'set test in assembly := {}' clean assembly

FROM openjdk:8-alpine

RUN apk update
RUN apk upgrade
RUN apk add tar outils-sha256 gawk bash curl nginx
RUN mkdir /run/nginx

COPY --from=build /gstore/target/scala-2.12/databus-dataid-repo-assembly-0.2.0-SNAPSHOT.jar /app/app.jar

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
                proxy_pass      $VIRT_URI/sparql;\n\
            }\n\
            location /DAV {\n\
                proxy_pass      $VIRT_URI/DAV;\n\
            }\n\
        }\n\
    }\n" > /etc/nginx/nginx.conf 
CMD nginx
CMD java -DvirtuosoUri=$VIRT_URI/sparql-auth -DvirtuosoUser=$VIRT_USER -DvirtuosoPass=$VIRT_PASS -DlocalGitRoot=$GIT_ROOT -jar /app/app.jar
