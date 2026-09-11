FROM maven:3.9.9-eclipse-temurin-8@sha256:e20e4fc05fb5480c884a1f750c362ed0e91aca15cec5736e12796713710ec9b3 AS maven
FROM eclipse-temurin:21.0.11_10-jdk-jammy@sha256:ea0e59a081c886d919b177b7006ac02fbdc64e8ab7cae724e2afd62de42d6892 AS build
COPY --from=maven /usr/share/maven /usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"
WORKDIR /source
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl ffb-server -am package
WORKDIR /out
RUN jar xf /source/ffb-server/target/ffb-server.zip && rm server.ini

FROM eclipse-temurin:21.0.11_10-jre-jammy@sha256:9af01c30e85b5a9b15fe249a84fb6dcb19e06ee84fbf23ea2d116b0e195c2ce9
LABEL org.opencontainers.image.version="3.4.0-r2.2"
WORKDIR /app
COPY --from=build /out/ /app/
RUN mkdir -p /data/backup /app/htdocs && chown -R 10001:10001 /data /app
USER 10001:10001
ENTRYPOINT ["java", "-Xms128m", "-Xmx768m", "-cp", "FantasyFootballServer.jar:lib/*", "com.fumbbl.ffb.server.local.LocalServerMain", "/config/server.ini"]
