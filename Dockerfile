FROM hseeberger/scala-sbt:graalvm-ce-21.1.0-java8_1.5.1_2.12.13 AS build

COPY . /databus
WORKDIR /databus
RUN sbt clean package

FROM tomcat:9.0.40-jdk8
COPY --from=build /databus/target/scala-2.12/databus-dataid-repo_2.12-0.2.0-SNAPSHOT.war /usr/local/tomcat/webapps/databus.war