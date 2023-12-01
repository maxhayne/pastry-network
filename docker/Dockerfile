# Author: Max Hayne

### Build stage
FROM gradle:8.1.1-jdk17-alpine AS build

RUN mkdir /app && chown -R gradle:gradle /app

WORKDIR /app

USER gradle

COPY --chown=gradle:gradle build.gradle /app
COPY --chown=gradle:gradle src /app/src

RUN gradle build


### Discovery run stage
FROM eclipse-temurin:17.0.8.1_1-jre-alpine AS discovery

# Create non-root user to be used for application
RUN addgroup -g 1001 -S discovery && adduser -u 1001 -S discovery -G discovery

RUN mkdir /app && chown -R discovery:discovery /app

WORKDIR /app

# Move jar created from build stage to working directory
COPY --from=build --chown=discovery:discovery /app/build/libs/*.jar /app/pastry.jar

# Copy config folder for 'application.properties' file
COPY --chown=discovery:discovery config /app/config

# Switch to non-root user
USER discovery

ENTRYPOINT ["java", "-cp", "./pastry.jar", "cs555.system.node.Discovery"]


### Peer run stage
FROM eclipse-temurin:17.0.8.1_1-jre-alpine AS peer

# Create non-root user to be used for application
RUN addgroup -g 1001 -S peer && adduser -u 1001 -S peer -G peer

RUN mkdir /app && chown -R peer:peer /app

WORKDIR /app

COPY --from=build --chown=peer:peer /app/build/libs/*.jar /app/pastry.jar
COPY --chown=server:server config /app/config

# Switch to non-root user
USER peer

ENTRYPOINT ["java", "-cp", "./pastry.jar", "cs555.system.node.Peer"]

