# Kakka - Tiamat Data Pipeline

## Project Overview

**Kakka** is a Java-based pipeline for managing Tiamat (NSR - National Stop Registry) data. It downloads administrative units from Kartverket, converts them to NeTEx and imports them into Tiamat, triggers NeTEx exports via Kingu, and updates the Organisation Registry (Baba) with administrative zone data.

The Pelias/geocoder pipeline was fully removed on the `remove-geocoder-piple` branch.

- **Organization**: Entur (entur)
- **Language**: Java 21
- **Framework**: Spring Boot with Apache Camel
- **Build Tool**: Maven
- **License**: EUPL v1.2

## Architecture

### Technology Stack

- **Spring Boot**: Application framework and dependency injection
- **Apache Camel 4.4.5**: Enterprise Integration Patterns and routing
- **Google Cloud Platform**:
  - Cloud Storage (GCS) for blob storage
  - Pub/Sub for messaging
- **GeoTools 32.2**: Geospatial data processing and coordinate transforms
- **JTS**: Geometry processing
- **netex-java-model 2.0.14**: NeTEx XML serialization
- **Kubernetes**: Deployment orchestration with leader election

### Core Components

1. **Pipeline Routes** (`pipeline/routes/`)
   - **Kartverket**: Download and upload administrative unit SOSI files with MD5 change detection
   - **Tiamat**: Import administrative units, countries, tariff zones; trigger NeTEx exports
   - **Nabu**: Update Organisation Registry (Baba) with administrative zone boundaries

2. **Data Sources**
   - **Kartverket**: Norwegian administrative units in SOSI format (counties, municipalities)
   - **GCS (GeoJSON)**: Neighbouring country boundaries (WhosOnFirst)
   - **REST upload**: Tariff zone NeTEx files
   - **Pub/Sub (Kingu)**: NeTEx export completion notifications

3. **Output**
   - NeTEx TopographicPlace data → Tiamat
   - Administrative zone boundaries (JSON) → Baba
   - NeTEx export ZIPs copied from Kingu bucket → Marduk/target bucket

### Key Design Patterns

- **Singleton Routes**: Camel Master component with Kubernetes leader election — only one instance runs cluster-wide
- **Content Change Detection**: MD5 companion files (`.md5`) in GCS — Kartverket files only processed when content changes
- **Adapter Pattern**: `TopographicPlaceAdapter` interface unifies SOSI, GeoJSON, and NeTEx sources into one conversion pipeline
- **Pub/Sub Header Mapping**: Message attributes bidirectionally mapped to/from Camel exchange headers in `BaseRouteBuilder`
- **Exponential Backoff**: 5s → 15s → 45s redelivery on failures (configured in `BaseRouteBuilder`)
- **Two-Project GCS Model**: Reads from Kingu bucket (source project), writes to Marduk bucket (target project)

## Project Structure

```
src/main/java/no/entur/kakka/
├── App.java                        # Spring Boot entry point
├── Constants.java                  # Camel exchange header constants
├── config/                         # Spring @Configuration classes
│   ├── GcsStorageConfig.java       # Two-project GCS storage setup
│   ├── TiamatExportConfig.java     # Loads tiamat-exports.yml
│   ├── AuthorizationConfig.java    # OAuth2 role extractors + authorization service
│   ├── OAuth2Config.java           # Multi-issuer JWT + OAuth2 client
│   └── HttpClientConfig.java       # ET-Client-Name/ID headers
├── domain/                         # BlobStoreFiles model
├── pipeline/                       # Domain pipeline logic
│   ├── BaseRouteBuilder.java       # Abstract base: error handling, singleton routes, quartz validation
│   ├── GeoCoderConstants.java      # Direct endpoint constants for task routing
│   ├── geojson/                    # GeoJSON adapters (KartverketCounty, WhosOnFirstCountry, etc.)
│   ├── nabu/                       # OrganisationRegistryAdministrativeUnitsUpdateRouteBuilder
│   ├── netex/                      # SOSI/GeoJSON → NeTEx conversion (TopographicPlaceConverter, etc.)
│   ├── routes/
│   │   ├── control/                # GeoCoderTask (phases), GeoCoderTaskType (enum)
│   │   ├── file/                   # CommonFileRoutesBuilder (cleanUpLocalDirectory)
│   │   ├── kartverket/             # AdministrativeUnitsDownloadRouteBuilder, KartverketFileRouteBuilder
│   │   ├── tiamat/                 # Kingu/Tiamat routes, TiamatExportTask model
│   │   └── util/                   # MarkContentChangedAggregationStrategy
│   ├── services/                   # KartverketService (GeoNorge API via jskdata)
│   └── sosi/                       # SOSI parsing: SosiElementWrapper, SosiCounty, GeometryTransformer, etc.
├── repository/                     # BlobStoreRepository interface + GCS/local/in-memory impls
├── rest/                           # AdminRestRouteBuilder
├── routes/                         # Infrastructure routes
│   ├── AutoCreatePubSubSubscriptionEventNotifier.java  # Test/dev: auto-create Pub/Sub topics
│   ├── blobstore/                  # BlobStoreRoute (uploadBlob, getBlob, copyBlob, copyKinguBlob)
│   ├── file/                       # FileUploadRouteBuilder (tariff zone REST → GCS → Pub/Sub)
│   └── status/                     # StatusRouteBuilder (JobEvent → Pub/Sub)
├── security/                       # KakkaAuthorizationService, AuthorizationHeaderProcessor
└── services/                       # BlobStoreService (Camel bean wrapping BlobStoreRepository)
```

## Active Camel Routes

| Route ID | Trigger | What it does |
|---|---|---|
| `admin-units-download-quartz` | Quartz MON-FRI 23:00 | Downloads Kartverket SOSI files, uploads to GCS (MD5-gated) |
| `tiamat-admin-units-update-quartz` | Quartz MON-FRI 23:00 | Converts SOSI → NeTEx → POST to Tiamat |
| `tiamat-geocoder-export-quartz` | Quartz MON-FRI 23:00 + 14:00 | Fires `direct:startNetexExport` |
| `kingu-publish-export-quartz` | Quartz daily 23:00 + 12:00 | Fires `direct:startNetexExport` |
| `netex-export-start-full` | `direct:startNetexExport` | Reads export job configs → publishes to Kingu Pub/Sub |
| `from-tiamat-export-queue-processed` | Pub/Sub (Kingu completion) | Copies export ZIP from Kingu bucket → Marduk bucket |
| `tiamatTariffZonesUpdate` | Pub/Sub (tariff zone file) | Downloads NeTEx from GCS → POST to Tiamat |
| `admin-tiamat-publish-export-full-v2` | `POST /export/stop_places/v2` | Manual NeTEx export trigger |
| `admin-tariff-zone-upload-file` | `POST /tariff_zone_admin/{id}/files` | Upload tariff zone file → GCS → Pub/Sub |
| `admin-org-reg-import-admin-zones` | `POST /organisation_admin/administrative_zones/update` | Updates Baba org registry |

## Key Dependencies

### Entur Libraries
- `entur-google-pubsub`: Camel Google Pub/Sub component
- `storage-gcp-gcs`: GCS blob storage helpers
- `netex-java-model`: NeTEx format (JAXB model)
- `organisation`: Role/permission extraction
- `oauth2`: Multi-issuer JWT resource server + OAuth2 client
- `slack`: Notifications

### Geospatial
- **GeoTools 32.2**: Coordinate reference systems (UTM-33 → WGS84)
- **JTS**: Geometry processing
- **jskdata**: Kartverket GeoNorge API client
- **wololo/jts2geojson**: JTS ↔ GeoJSON conversion

## Configuration

### Required Properties

```properties
# Server
server.host=0.0.0.0
server.port=8080

# Security
kakka.security.authorization-service=full-access

# GCS — two-project model
blobstore.gcs.container.name=<kakka-bucket>
blobstore.gcs.target-container.name=<marduk-bucket>
blobstore.gcs.source.container.name=<kingu-bucket>
blobstore.gcs.project.id=<project-id>
blobstore.gcs.target-project.id=<target-project-id>

# Tiamat
tiamat.url=http://tiamat:1888
tiamat.exports.config.path=/etc/tiamat-config/tiamat-exports.yml

# Kartverket
kartverket.username=<username>
kartverket.password=<password>

# Baba
organisations.api.url=http://baba/services/organisations/

# Pub/Sub
pubsub.kakka.inbound.subscription.kingu.netex.export=google-pubsub:<project>:<subscription>
pubsub.kakka.outbound.topic.kingu.netex.export=google-pubsub:<project>:<topic>
pubsub.kakka.inbound.subscription.tariff.zone.file.queue=google-pubsub:<project>:<subscription>
pubsub.kakka.outbound.topic.tariff.zone.file.queue=google-pubsub:<project>:<topic>
pubsub.kakka.outbound.topic.nabu.job.event.topic=google-pubsub:<project>:<topic>

spring.profiles.active=gcs-blobstore
```

### Tiamat Export Configuration (`tiamat-exports.yml`)

Loaded by `TiamatExportConfig`, defines the list of export jobs dispatched to Kingu via Pub/Sub. Each job maps to an `ExportParams` with name, export modes, and options.

### Security Models

1. **Full Access** (`full-access`): All authenticated users have full access
2. **Token-based** (`token-based`): OAuth2 JWT with role extraction (JWT claims or Baba API)

## Building & Running

```bash
# Build and test
mvn clean install

# Run tests only
mvn test

# Run with Maven
mvn spring-boot:run

# Run JAR
java -Xmx1280m -jar target/kakka-0.0.1-SNAPSHOT.jar
```

## REST Endpoints

| Method | Path | Auth required |
|---|---|---|
| `POST` | `/services/export/stop_places/v2` | Route data administrator |
| `POST` | `/services/organisation_admin/administrative_zones/update` | Organisation administrator |
| `POST` | `/services/tariff_zone_admin/{providerId}/files` | Route data administrator |
| `GET` | `/actuator/prometheus` | Public |
| `GET` | `/actuator/health/liveness` | Public |
| `GET` | `/actuator/health/readiness` | Public |

Valid `providerId` values: `RUT`, `AKT`, `KOL`, `OST`, `VOT`, `TRO`

## CI/CD

GitHub Actions (`.github/workflows/push.yml`):
1. Maven verify
2. SonarCloud scan
3. Docker lint
4. Build & push Docker image

Helm charts: `helm/kakka/` — Terraform: `terraform/`

## Related Projects

- **Tiamat** — National Stop Registry (NSR), primary data target
- **Kingu** — NeTEx exporter triggered by Kakka via Pub/Sub
- **Baba** — Organisation Registry receiving administrative zone updates
- **Marduk** — Route data pipeline sharing the target GCS bucket

## License

Licensed under the **EUPL v1.2** — see `LICENSE.txt`.