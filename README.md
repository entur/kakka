# Kakka ![Build](https://github.com/entur/kakka/actions/workflows/push.yml/badge.svg)

Kakka is a data pipeline for managing Tiamat (NSR - National Stop Registry) data. It downloads administrative units from Kartverket, converts them to NeTEx format and imports them into Tiamat, and triggers NeTEx exports via Kingu.

## What it does

- **Downloads administrative units** (counties, municipalities) from Kartverket and uploads to GCS with MD5-based change detection
- **Updates Tiamat** with administrative units and neighbouring countries (SOSI/GeoJSON → NeTEx)
- **Updates the Organisation Registry (Baba)** with administrative zone boundaries
- **Triggers Tiamat/Kingu NeTEx exports** on a schedule and copies completed exports to the target GCS bucket
- **Accepts tariff zone file uploads** via REST and imports them into Tiamat

## Architecture

- **Java 21**, **Spring Boot**, **Apache Camel 4.4.5**
- **Google Cloud Storage** — blob storage for Kartverket files and NeTEx exports
- **Google Pub/Sub** — async messaging with Kingu (NeTEx exporter) and job status events
- **GeoTools / JTS** — coordinate transforms and geometry (SOSI UTM-33 → WGS84)
- **netex-java-model** — NeTEx XML serialization
- **Kubernetes** — leader election for singleton Camel routes via Camel Master component

## Project Structure

```
src/main/java/no/entur/kakka/
├── App.java                        # Spring Boot entry point
├── Constants.java                  # Exchange header constants
├── config/                         # Spring configuration (GCS, OAuth2, TiamatExport)
├── domain/                         # Data models
├── pipeline/                       # Domain pipeline logic
│   ├── BaseRouteBuilder.java       # Base Camel route (error handling, singleton, quartz)
│   ├── geojson/                    # GeoJSON adapters (Kartverket, WhosOnFirst countries)
│   ├── nabu/                       # Organisation Registry update routes
│   ├── netex/                      # SOSI/GeoJSON → NeTEx conversion
│   ├── routes/
│   │   ├── control/                # GeoCoderTask / GeoCoderTaskType models
│   │   ├── file/                   # Local directory cleanup
│   │   ├── kartverket/             # Kartverket download + GCS upload routes
│   │   ├── tiamat/                 # Tiamat update + Kingu export routes
│   │   └── util/                   # MarkContentChangedAggregationStrategy
│   ├── services/                   # KartverketService (GeoNorge API client)
│   └── sosi/                       # SOSI format parsing and adapters
├── repository/                     # BlobStoreRepository (GCS, local-disk, in-memory)
├── rest/                           # AdminRestRouteBuilder (REST endpoints)
├── routes/                         # Infrastructure routes (blobstore, file upload, status)
├── security/                       # OAuth2, authorization service
└── services/                       # BlobStoreService
```

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/services/export/stop_places/v2` | Trigger Kingu NeTEx export |
| `POST` | `/services/organisation_admin/administrative_zones/update` | Update Baba org registry |
| `POST` | `/services/tariff_zone_admin/{providerId}/files` | Upload tariff zone NeTEx file |
| `GET`  | `/actuator/prometheus` | Prometheus metrics |
| `GET`  | `/actuator/health/liveness` | Liveness probe |
| `GET`  | `/actuator/health/readiness` | Readiness probe |

## Configuration

The application requires an external `application.properties`. Key properties:

```properties
# Server
server.host=0.0.0.0
server.port=8080

# Security
kakka.security.authorization-service=full-access

# Google Cloud Storage
blobstore.gcs.container.name=<kakka-bucket>
blobstore.gcs.target-container.name=<marduk-bucket>
blobstore.gcs.project.id=<project-id>
blobstore.gcs.credential.path=<optional-path-to-service-account.json>

# Tiamat
tiamat.url=http://tiamat:1888
tiamat.exports.config.path=/etc/tiamat-config/tiamat-exports.yml

# Kartverket
kartverket.username=<username>
kartverket.password=<password>

# Organisation registry (Baba)
organisations.api.url=http://baba/services/organisations/

# Pub/Sub
pubsub.kakka.inbound.subscription.kingu.netex.export=google-pubsub:<project>:<subscription>
pubsub.kakka.outbound.topic.kingu.netex.export=google-pubsub:<project>:<topic>
pubsub.kakka.inbound.subscription.tariff.zone.file.queue=google-pubsub:<project>:<subscription>
pubsub.kakka.outbound.topic.tariff.zone.file.queue=google-pubsub:<project>:<topic>
pubsub.kakka.outbound.topic.nabu.job.event.topic=google-pubsub:<project>:<topic>

# Active profile
spring.profiles.active=gcs-blobstore
```

## Building & Running

```bash
# Build and test
mvn clean install

# Run locally
mvn spring-boot:run

# Run JAR
java -Xmx1280m -jar target/kakka-0.0.1-SNAPSHOT.jar
```

## Security

Two authorization modes:

```properties
# All authenticated users have full access
kakka.security.authorization-service=full-access

# OAuth2 token-based role checks
kakka.security.authorization-service=token-based
```

## CI/CD

GitHub Actions workflow (`.github/workflows/push.yml`):
1. Maven verify
2. SonarCloud scan
3. Docker lint
4. Build & push image

Helm charts in `helm/kakka/`, Terraform in `terraform/`.

## Related Projects

- **Tiamat** — National Stop Registry (NSR), primary data target
- **Kingu** — NeTEx exporter, triggered via Pub/Sub
- **Baba** — Organisation Registry, receives administrative zone updates
- **Marduk** — Route data pipeline, shares GCS target bucket

## License

Licensed under the **EUPL v1.2** — see `LICENSE.txt`.