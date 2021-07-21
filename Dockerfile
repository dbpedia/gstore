FROM hseeberger/scala-sbt:graalvm-ce-21.1.0-java8_1.5.1_2.12.13 AS build

COPY . /databus
WORKDIR /databus
RUN sbt 'set test in assembly := {}' clean assembly

FROM openjdk:8-alpine
ENV JAVA_PRMS=""

COPY --from=build /databus/target/scala-2.12/databus-dataid-repo-assembly-0.2.0-SNAPSHOT.jar /app/app.jar
CMD java ${JAVA_PRMS} -jar /app/app.jar
