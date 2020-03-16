FROM openjdk:11-jre
WORKDIR /deployments
COPY target/kakka-*-SNAPSHOT.jar kakka.jar
CMD java $JAVA_OPTIONS -jar kakka.jar
