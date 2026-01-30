FROM bellsoft/liberica-openjre-alpine:25.0.2 AS builder
WORKDIR /builder
# Patch OpenSSL/libssl3 in the builder stage too (for image scanning)
RUN apk update \
 && apk upgrade --no-cache openssl libssl3 libcrypto3 \
 && rm -rf /var/cache/apk/*
COPY target/*-SNAPSHOT.jar application.jar
RUN java -Djarmode=tools  -jar application.jar extract --layers --destination extracted

FROM bellsoft/liberica-openjre-alpine:25.0.2
# Patch OpenSSL / libssl3 in the runtime image
RUN apk update \
 && apk upgrade --no-cache openssl libssl3 libcrypto3 \
 && apk add --no-cache tini \
 && rm -rf /var/cache/apk/*
WORKDIR /deployments
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
ENTRYPOINT [ "/sbin/tini", "--", "java", "-jar", "application.jar" ]
