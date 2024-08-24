FROM confluentinc/cp-kafka-connect:7.7.0 as platform

# Pre-install Amazon S3 Sink connector plugin
# https://rmoff.net/2020/06/19/how-to-install-connector-plugins-in-kafka-connect/
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-s3:10.5.14

# Supported Versions and Interoperability for Confluent Platform
# https://docs.confluent.io/platform/current/installation/versions-interoperability.html#java
FROM eclipse-temurin:17-jdk AS base

# Package custom Kafka Connect SMTs
ADD ./ transforms
WORKDIR transforms
RUN ./gradlew installDist

FROM platform

COPY --from=base /transforms/demo-connect-transforms/build/install/demo-connect-transforms /opt/connect-transforms
