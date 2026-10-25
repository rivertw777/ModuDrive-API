# JRE, not JDK: nothing in a running service needs a compiler or jcmd, so they aren't shipped.
# Two stages: the first can start the app once to record a Java AOT cache — the classes it loads and
# links on startup — and the image then starts from that cache: on demo's half vCPU auth-service went from
# ~139s to ~72s, so a new task (scale-out, crash, Spot replacement) serves twice as soon. Recording costs
# 1–2 minutes per image, so only the deploy builds it (AOT=true, deploy.yml); local images skip it.
FROM eclipse-temurin:25-jre-alpine AS aot
ARG JAR_FILE
ARG AOT=false
COPY ${JAR_FILE} /build/app.jar
# The cache is tied to the classpath, so it's recorded on the unpacked jar, at the path the image runs it.
RUN java -Djarmode=tools -jar /build/app.jar extract --destination /app
WORKDIR /app
# Training run: refresh the context and exit before anything starts (listeners, schedulers). There's no
# DB, Redis or broker here, so nothing may connect: Flyway and schema checks are off, Hibernate is told
# the dialect instead of asking the DB, and the URLs are placeholders nothing opens. The variables are the
# services' settings without a default (application*.yml) — a new one fails this build until it's added.
# jvm.args carries -XX:AOTCache only when there is one: the JVM logs errors at startup for a missing cache.
RUN : > jvm.args && [ "$AOT" != true ] || { \
    CLIENT_URL=http://localhost MAIL_FROM=training@localhost MODUDRIVE_FILE_DEFAULT_QUOTA_BYTES=1 \
    REDIS_HOST=localhost REDIS_PORT=6379 REDIS_PASSWORD=training \
    STORAGE_BLOCK_SIZE=4194304 STORAGE_S3_BUCKET=training STORAGE_S3_REGION=ap-northeast-2 AWS_REGION=ap-northeast-2 \
    java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -Dspring.profiles.active=prod \
      -Dspring.flyway.enabled=false -Dspring.sql.init.mode=never -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.datasource.url=jdbc:postgresql://localhost:5432/training \
      -jar app.jar \
    && echo "-XX:AOTCache=app.aot" > jvm.args; }

FROM eclipse-temurin:25-jre-alpine
# Not root: a compromised process can't change the image's files or bind low ports. On AWS the root
# filesystem is also read-only (ecs.tf), with only /tmp writable.
RUN addgroup -S app && adduser -S -G app app
# /tmp is a volume so Fargate copies this mode onto the scratch volume it mounts there — otherwise
# that volume comes up root-owned 0755 and the app user can't write (Tomcat's temp dir, uploads).
RUN chmod 1777 /tmp
VOLUME ["/tmp"]
USER app
EXPOSE 8080
COPY --from=aot /app /app
WORKDIR /app
ENTRYPOINT ["java", "@jvm.args", "-jar", "app.jar"]
