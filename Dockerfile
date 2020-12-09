FROM tomcat:9.0.40-jdk8

COPY target/scala-2.12/databus-dataid-repo_2.12-0.2.0-SNAPSHOT.war /usr/local/tomcat/webapps/databus.war
