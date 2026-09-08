FROM maven:3.9.9-eclipse-temurin-8@sha256:e20e4fc05fb5480c884a1f750c362ed0e91aca15cec5736e12796713710ec9b3 AS maven
FROM eclipse-temurin:8-jdk-jammy@sha256:7cb1137d4a02aeb7ca85faae69c5f0720703936cbfb0b4af21067c73174f9b5e AS build
COPY --from=maven /usr/share/maven /usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"
WORKDIR /source
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl ffb-server -am package
WORKDIR /out
RUN jar xf /source/ffb-server/target/ffb-server.zip && rm server.ini

FROM eclipse-temurin:8-jre-jammy@sha256:d53aa7811eba390450721b1037978605992f5d9467c4af629384f23a49f78436
LABEL org.opencontainers.image.version="3.4.0-m1c.1"
WORKDIR /app
COPY --from=build /out/ /app/
RUN mkdir -p /data/backup /app/htdocs && chown -R 10001:10001 /data /app
USER 10001:10001
ENTRYPOINT ["java", "-Xms128m", "-Xmx768m", "-cp", "FantasyFootballServer.jar:lib/*", "com.fumbbl.ffb.server.local.LocalServerMain", "/config/server.ini"]
