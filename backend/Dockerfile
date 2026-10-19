# Backend image: build with Maven on JDK 21, run on a slim JRE 21.
# Build context: backend/

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
# Dependencies first, so code changes don't re-download them.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/school-erp-*.jar /app.jar

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 erp && mkdir -p /data/files && chown erp /data/files
USER erp
WORKDIR /app
COPY --from=build /app.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError" \
    FILE_STORAGE_LOCAL_DIR=/data/files \
    SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
