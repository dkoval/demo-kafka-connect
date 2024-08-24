ARG CONFLUENT_PLATFORM_VERSION=7.7.0
ARG CONFLUENT_KAFKA_CONNECT_S3_PLUGIN_VERSION=10.5.14

FROM confluentinc/cp-kafka-connect:${CONFLUENT_PLATFORM_VERSION} AS platform

ARG CONFLUENT_KAFKA_CONNECT_S3_PLUGIN_VERSION

# Pre-install Amazon S3 Sink connector plugin
# https://rmoff.net/2020/06/19/how-to-install-connector-plugins-in-kafka-connect/
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-s3:${CONFLUENT_KAFKA_CONNECT_S3_PLUGIN_VERSION}

# Supported Versions and Interoperability for Confluent Platform
# https://docs.confluent.io/platform/current/installation/versions-interoperability.html#java
FROM eclipse-temurin:17-jdk AS builder

# Package custom Kafka Connect SMTs
ADD ./ transforms
WORKDIR transforms
RUN ./gradlew installDist

FROM platform

COPY --from=builder /transforms/demo-connect-transforms/build/install/demo-connect-transforms /opt/connect-transforms
