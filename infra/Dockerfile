# apix-x402-onramp container image
#
# Multi-stage build: compile the Quarkus fast-jar in a Maven build stage, then run it on a slim JRE
# as a non-root user. No secret is ever COPY'd/ARG'd/ENV'd into this image — the payer mnemonic and
# payTo addresses are supplied at `docker run` time from an env_file on the box (see the runbook).

## Build stage ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Online (-B) at image-build time: a fresh build image has no ~/.m2 cache. Tests run in CI before.
RUN mvn -B -DskipTests package

## Runtime stage --------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd --system onramp \
    && useradd --system --gid onramp --home-dir /app --shell /usr/sbin/nologin onramp
WORKDIR /app
# Quarkus fast-jar layout: the whole target/quarkus-app/ tree must be copied together.
COPY --from=build --chown=onramp:onramp /build/target/quarkus-app/ /app/quarkus-app/
USER onramp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-app/quarkus-run.jar"]
