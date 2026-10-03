# Resume service image with the exact Word -> PDF toolchain.
# Build context: ./backend, for example:
# docker build -f resume-service/Dockerfile --build-arg SERVICE=resume-service .
FROM docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-17 AS build
ARG SERVICE=resume-service
WORKDIR /workspace
COPY . .
RUN mvn -s settings.xml.example -pl ${SERVICE} -am clean package -DskipTests

FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre AS runtime
ARG SERVICE=resume-service
ENV JAVA_OPTS="" \
    RESUME_SOFFICE_PATH="/usr/bin/soffice" \
    RESUME_RENDER_TIMEOUT_SECONDS="45" \
    RESUME_RENDER_CONCURRENCY="2" \
    LANG="zh_CN.UTF-8" \
    LC_ALL="zh_CN.UTF-8"
WORKDIR /app
RUN set -eu; for source in /etc/apt/sources.list /etc/apt/sources.list.d/*.sources; do \
    if [ -f "$source" ]; then sed -i -E 's@http://(archive|security).ubuntu.com/ubuntu/?@https://mirrors.aliyun.com/ubuntu/@g; s@http://([a-z]+).ubuntu.com/ubuntu/?@https://mirrors.aliyun.com/ubuntu/@g' "$source"; fi; done \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends libreoffice-writer libreoffice-core fonts-noto-cjk fonts-liberation2 curl locales \
    && locale-gen zh_CN.UTF-8 \
    && rm -rf /var/lib/apt/lists/*
EXPOSE 8103
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD curl -fsS http://127.0.0.1:8103/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

FROM runtime AS final
ARG SERVICE=resume-service
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar /app/app.jar
