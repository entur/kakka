# Kakka ![Build](https://github.com/entur/kakka/actions/workflows/push.yml/badge.svg)

Kakka is the build pipeline for the elastic search backing of the Pelias geocoder application. 
 
Kakka is also responsible for triggering regular exports from Tiamat (NSR)



## Configuration
* The application is unable to run without configuration. This must be defined externally to the application in a file called application.properties. Copy application.properties into either the current directory, i.e. where the application will be run from, or a /config subdirectory of this folder
* Typical application.properties for dev environment:

```
shutdown.timeout=1
camel.springboot.name=Kakka


server.admin.host=0.0.0.0
server.admin.port=8888
server.host=0.0.0.0
server.port=8776

kakka.security.user-context-service=full-access

blobstore.gcs.container.name=marduk-test
blobstore.gcs.credential.path=/home/tomgag/.ssh/Carbon-ef49cabc6d04.json
blobstore.delete.external.blobs=false
blobstore.gcs.project.id=carbon-1287



logging.config=classpath:logback.xml
logging.level.no=DEBUG
logging.level.no.entur.kakka=INFO
logging.level.org=INFO
logging.level.org.apache.camel.util=INFO

spring.main.sources=no.entur.kakka
spring.profiles.active=gcs-blobstore

tiamat.url=http4://tiamat:1888
babylon.url=http4://babylon:9030/rest

kartverket.username=
kartverket.password=

```
* Run with maven `mvn spring-boot:run -Dspring.profiles.active=dev`

* Build: `mvn clean install`
* Local run: `java -Xmx1280m -Dspring.profiles.active=dev -jar target/kakka-0.0.1-SNAPSHOT.jar`
* Docker image: `mvn -Dspring.profiles.active=dev -Pf8-build`
* Run the docker image in docker inside vagrant:

     ```docker rm -f kakka ; mvn -Pf8-build && docker run -it --name kakka -e JAVA_OPTIONS="-Xmx1280m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" --link lamassu  -v /git/config/kakka/dev/application.properties:/app/config/application.properties:ro dr.rutebanken.org/rutebanken/kakka:0.0.1-SNAPSHOT```

* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html

## Security
An authorization service implementation must be selected.
The following implementation gives full access to all authenticated users:

```properties
kakka.security.user-context-service=full-access
```

The following implementation enables OAuth2 token-based authorization:
```properties
kakka.security.user-context-service=token-based
```


## Liveness and readyiness
In production, kakka can be probed with:
- http://<host>:<port>/health/live
- http://<host>:<port>/health/ready
to check liveness and readiness, accordingly

