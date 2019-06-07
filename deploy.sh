sbt package
sudo cp -v target/scala-2.12/databus-dataid-repo_2.12-*.war /var/lib/tomcat8/webapps/dataid-repo.war
sbt clean package ; scp target/scala-2.12/databus-dataid-repo_2.12-0.1.0-SNAPSHOT.war  root@databus.dbpedia.org:/var/lib/tomcat8/webapps/dataid-repo.war
