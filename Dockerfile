FROM openjdk:11-jre
ADD target/kakka-*-SNAPSHOT.jar kakka.jar

EXPOSE 9004
CMD java $JAVA_OPTIONS -jar /kakka.jar
