# Kakka - Geocoder Build Pipeline

## Project Overview

**Kakka** is a Java-based build pipeline for preparing and managing Elasticsearch data for the Pelias geocoder application. It's part of the Entur ecosystem (Norwegian public transport infrastructure) and is responsible for building geocoding data from multiple sources and triggering regular exports from Tiamat (NSR - National Stop Registry).

- **Organization**: Entur (entur)
- **Language**: Java 21
- **Framework**: Spring Boot with Apache Camel
- **Build Tool**: Maven
- **License**: EUPL v1.2 (European Union Public License)

## Architecture

### Technology Stack

- **Spring Boot**: Application framework and dependency injection
- **Apache Camel 4.4.5**: Enterprise Integration Patterns and routing
- **Google Cloud Platform**: 
  - Cloud Storage (GCS) for blob storage
  - Pub/Sub for messaging
- **PostgreSQL**: Database with Flyway migrations
- **GeoTools 32.2**: Geospatial data processing
- **Kubernetes**: Deployment orchestration
- **Docker**: Containerization

### Core Components

1. **Geocoder Routes** (`geocoder/routes/`)
   - **Control**: Main orchestration and task management
   - **Tiamat**: NSR (National Stop Registry) data export and updates
   - **Kartverket**: Norwegian mapping authority data processing
   - **OSM**: OpenStreetMap data processing
   - **Pelias**: Elasticsearch index building and mapping

2. **Data Sources**
   - **Tiamat**: Stop places, administrative units, tariff zones, POIs, countries
   - **Kartverket**: Norwegian place names and SOSI format data
   - **OpenStreetMap**: Street and location data in PBF format
   - **GTFS**: Public transport stop data
   - **NeTEx**: Public transport network exchange format

3. **Output**
   - Elasticsearch bulk commands for Pelias geocoder
   - Exported NeTEx files from Tiamat

### Key Design Patterns

- **Apache Camel Routes**: Event-driven processing pipelines
- **Kubernetes CronJobs**: Scheduled data processing tasks
- **Blob Storage**: Intermediate file storage on GCS
- **Pub/Sub Messaging**: Asynchronous task coordination
- **Idempotent Repository**: Prevents duplicate processing

## Project Structure

```
kakka/
├── src/main/java/no/entur/kakka/
│   ├── App.java                      # Main Spring Boot application
│   ├── Constants.java                # Application-wide constants
│   ├── config/                       # Configuration classes
│   │   ├── GcsStorageConfig.java    # Google Cloud Storage setup
│   │   ├── OAuth2Config.java        # Authentication
│   │   └── AuthorizationConfig.java # Authorization
│   ├── domain/                       # Domain models
│   ├── geocoder/                     # Geocoding business logic
│   │   └── routes/                   # Camel route definitions
│   │       ├── control/              # Task orchestration
│   │       ├── tiamat/               # NSR data export
│   │       ├── kartverket/           # Norwegian mapping data
│   │       ├── osm/                  # OpenStreetMap processing
│   │       └── pelias/               # Elasticsearch indexing
│   ├── openstreetmap/                # OSM data models
│   ├── repository/                   # Data access layer
│   ├── rest/                         # REST API endpoints
│   ├── routes/                       # General Camel routes
│   │   ├── file/                     # File upload/download
│   │   ├── status/                   # Health and status
│   │   └── blobstore/                # Cloud storage operations
│   └── security/                     # Security configuration
├── src/main/resources/
│   ├── db/                           # Flyway database migrations
│   ├── logback.xml                   # Logging configuration
│   └── schema.sql                    # Database schema
├── api/                              # API definitions
│   ├── export/                       # Export API specs
│   ├── geocoder-admin/               # Admin API
│   └── poi-filter/                   # POI filtering
├── helm/                             # Kubernetes Helm charts
├── terraform/                        # Infrastructure as code
├── Dockerfile                        # Multi-stage Docker build
└── pom.xml                           # Maven dependencies
```

## Key Dependencies

### Entur Libraries
- `entur-google-pubsub`: Google Pub/Sub integration
- `storage-gcp-gcs`: Google Cloud Storage abstraction
- `netex-java-model`: NeTEx format support
- `organisation`: Organization context
- `oauth2`: Authentication
- `slack`: Notifications

### Geospatial
- **GeoTools**: Coordinate reference systems, GeoJSON
- **JTS**: Geometry processing
- **SOSI Reader**: Norwegian SOSI format
- **OSM PBF**: OpenStreetMap binary format

### Data Processing
- **GTFS-lib**: GTFS feed parsing
- **BeanIO**: Fixed/delimited file processing
- **Apache Commons**: Text, IO utilities

## Configuration

### Required Properties

The application requires an `application.properties` file with:

```properties
# Server Configuration
server.host=0.0.0.0
server.port=8776
server.admin.port=8888

# Security
kakka.security.user-context-service=full-access|token-based

# Google Cloud Storage
blobstore.gcs.container.name=<bucket-name>
blobstore.gcs.project.id=<project-id>
blobstore.gcs.credential.path=<path-to-json>

# External Services
tiamat.url=http4://tiamat:1888

# Kartverket Credentials
kartverket.username=<username>
kartverket.password=<password>

# Profiles
spring.profiles.active=gcs-blobstore
```

### Security Models

1. **Full Access** (`full-access`): All authenticated users have full access
2. **Token-based** (`token-based`): OAuth2 token-based authorization

## Building & Running

### Local Development

```bash
# Build
mvn clean install

# Run with Maven
mvn spring-boot:run -Dspring.profiles.active=dev

# Run JAR directly
java -Xmx1280m -Dspring.profiles.active=dev -jar target/kakka-0.0.1-SNAPSHOT.jar
```

### Docker

```bash
# Build Docker image
mvn -Dspring.profiles.active=dev -Pf8-build

# Run container
docker run -it --name kakka \
  -e JAVA_OPTIONS="-Xmx1280m" \
  -v /path/to/application.properties:/app/config/application.properties:ro \
  kakka:0.0.1-SNAPSHOT
```

### Testing

```bash
# Run all tests
mvn verify

# Run with coverage
mvn test jacoco:report
```

## API Endpoints

### Health & Monitoring

- `GET /health/live` - Liveness probe
- `GET /health/ready` - Readiness probe
- `GET /actuator/prometheus` - Prometheus metrics

### Admin Operations

The application exposes REST endpoints for:
- File upload/download
- Geocoder task management
- Tiamat export triggering
- Status monitoring

## Data Processing Pipeline

### Typical Workflow

1. **Data Ingestion**
   - Download from Kartverket, OSM, or trigger Tiamat export
   - Store raw data in GCS blob storage
   - Validate and checksum files

2. **Data Transformation**
   - Parse source formats (SOSI, PBF, NeTEx, GTFS)
   - Convert to GeoJSON
   - Apply coordinate transformations
   - Filter and enrich data

3. **Elasticsearch Indexing**
   - Map to Pelias document schema
   - Apply boost configurations for stop places
   - Generate bulk commands
   - Write to Elasticsearch

4. **Status & Monitoring**
   - Track job progress
   - Send notifications via Slack
   - Update Kubernetes pod labels
   - Publish status to Pub/Sub

## Kubernetes Integration

### CronJobs

Scheduled tasks managed via Kubernetes CronJobs:
- Geocoder data refresh
- Tiamat export status checks
- Regular data synchronization

### Service Discovery

Uses Kubernetes API for:
- Master route election (singleton routes)
- Pod status updates
- Deployment management

## Development Notes

### Code Organization

- **Camel Routes**: Each route builder handles a specific data source or process
- **Constants**: Centralized in `Constants.java` using `Rutebanken*` prefixes
- **Configuration**: Spring `@Configuration` classes in `config/` package
- **Security**: OAuth2 and role-based access control

### Key Patterns

1. **Singleton Routes**: Use Camel Master component for leader election
2. **Content Changed Detection**: MD5 checksums prevent redundant processing
3. **Blob Storage Abstraction**: GCS operations through helper libraries
4. **Idempotent Consumers**: Prevent duplicate message processing

### External Integrations

- **Tiamat**: GraphQL API for stop place registry
- **Kartverket**: Norwegian mapping authority API
- **Pelias**: Elasticsearch-based geocoder

## CI/CD

### GitHub Actions

Workflow: `.github/workflows/push.yml`

1. **Maven Verify**: Build and test
2. **Sonar Scan**: Code quality analysis (SonarCloud)
3. **Docker Lint**: Container linting
4. **Build & Push**: Docker image to registry

### Deployment

- Helm charts in `helm/kakka/`
- Terraform configurations in `terraform/`
- External secrets management for credentials

## Monitoring & Observability

- **Logging**: Logback with Logstash encoder (JSON format)
- **Metrics**: Micrometer with Prometheus registry
- **Tracing**: Camel MDC logging for request correlation
- **Health**: Spring Boot Actuator endpoints

## Related Projects

- **Pelias**: The geocoder that consumes Kakka's output
- **Tiamat**: National Stop Registry (NSR)
- **Marduk**: Route data processing pipeline (shares blob storage)

## Contributing

This is an Entur internal project. All contributions must:
- Follow existing code patterns
- Include unit tests
- Pass CI/CD pipeline
- Comply with EUPL v1.2 license

## Troubleshooting

### Common Issues

1. **Missing configuration**: Ensure `application.properties` is properly mounted
2. **GCS credentials**: Verify service account JSON path and permissions
3. **Memory**: Adjust `-Xmx` based on data volume (default: 1280m)
4. **Timeouts**: Increase `shutdown.timeout` for long-running jobs

### Debugging

```bash
# Enable debug logging
logging.level.no.entur.kakka=DEBUG

# Remote debugging
JAVA_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

## License

Licensed under the **EUPL v1.2** (European Union Public License)
- See `LICENSE.txt` for full text
- Compatible with GPL v2/v3, AGPL v3, MPL v2, and others
- Requires derivative works to use compatible licenses
