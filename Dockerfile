FROM hseeberger/scala-sbt:graalvm-ce-21.1.0-java8_1.5.1_2.12.13 AS build

COPY . /databus
WORKDIR /databus
RUN sbt 'set test in assembly := {}' clean assembly

FROM openjdk:8-alpine

ENV VIRT_URI=http://virtuoso:8890
ENV VIRT_USER=""
ENV VIRT_PASS=""
ENV GIT_ROOT=""

RUN apk update
RUN apk upgrade
RUN apk add tar
RUN apk add outils-sha256
RUN apk add gawk
RUN apk add bash
RUN apk add curl
RUN apk add nginx
RUN mkdir /run/nginx

COPY --from=build /databus/target/scala-2.12/databus-dataid-repo-assembly-0.2.0-SNAPSHOT.jar /app/app.jar
COPY backup.sh /backup.sh
COPY generate-backup-meta.sh /generate-backup-meta.sh
RUN chmod +x /backup.sh
RUN chmod +x /generate-backup-meta.sh

SHELL ["/bin/bash", "-c"]
CMD echo -e "events {\n\
      worker_connections  4096;\n\
    }\n\
    http {\n\
        resolver 127.0.0.11 ipv6=off;\n\
        server { # simple reverse-proxy\n\
            listen       0.0.0.0:80;\n\
            add_header Access-Control-Allow-Origin *;\n\
            location / {\n\
                proxy_pass      http://127.0.0.1:8080;\n\
            }\n\
            location /sparql {\n\
                proxy_pass      $VIRT_URI/sparql;\n\
            }\n\
        }\n\
    }\n" > /etc/nginx/nginx.conf ; nginx ; java -DvirtuosoUri=$VIRT_URI/sparql-auth -DvirtuosoUser=$VIRT_USER -DvirtuosoPass=$VIRT_PASS -DlocalGitRoot=$GIT_ROOT -jar /app/app.jar
