FROM gcr.io/distroless/java25-debian13:nonroot@sha256:59bbb349c9752fc57b75744b41b1614c9dabe8bb4869523aabd5a4288f6c09a0 AS builder
COPY target/*-SNAPSHOT.jar /tmp/application.jar
# No shell, and nonroot can only write to /tmp
RUN [ "/usr/bin/java", "-Djarmode=tools", "-jar", "/tmp/application.jar", "extract", "--layers", "--destination", "/tmp/extracted" ]

FROM gcr.io/distroless/java25-debian13:nonroot@sha256:59bbb349c9752fc57b75744b41b1614c9dabe8bb4869523aabd5a4288f6c09a0
WORKDIR /deployments
COPY --from=builder /tmp/extracted/dependencies/ ./
COPY --from=builder /tmp/extracted/spring-boot-loader/ ./
COPY --from=builder /tmp/extracted/snapshot-dependencies/ ./
COPY --from=builder /tmp/extracted/application/ ./
# Netty and grpc still use JNI and sun.misc.Unsafe, which Java 25 warns about
ENTRYPOINT [ "/usr/bin/java", "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-jar", "application.jar" ]
