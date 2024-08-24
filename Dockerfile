# Supported Versions and Interoperability for Confluent Platform
# https://docs.confluent.io/platform/current/installation/versions-interoperability.html#java
FROM eclipse-temurin:17-jdk AS base

ADD ./ transforms
WORKDIR transforms

RUN ./gradlew installDist

FROM confluentinc/cp-kafka-connect:7.7.0

COPY --from=base /transforms/demo-connect-transforms/build/install/demo-connect-transforms /opt/connect-transforms
