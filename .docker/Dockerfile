# JRE, not JDK: nothing in a running service needs a compiler or jcmd, so they aren't shipped.
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
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
