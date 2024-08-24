ARG CONFLUENT_PLATFORM_VERSION=7.7.0
ARG CONNECT_S3_PLUGIN_VERSION=10.5.14
ARG CONNECT_PLUGIN_DIR=/usr/local/share/connect-plugins

FROM confluentinc/cp-kafka-connect:${CONFLUENT_PLATFORM_VERSION} AS platform

ARG CONNECT_S3_PLUGIN_VERSION

# Pre-install Amazon S3 Sink connector plugin
# https://rmoff.net/2020/06/19/how-to-install-connector-plugins-in-kafka-connect/
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-s3:${CONNECT_S3_PLUGIN_VERSION}

FROM platform

ARG CONNECT_PLUGIN_DIR

# Copy SMTs with all dependent JAR files into Kafka Connect container
COPY --chown=appuser:appuser demo-connect-transforms/build/install ${CONNECT_PLUGIN_DIR}
