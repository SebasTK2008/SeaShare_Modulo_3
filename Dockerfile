FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system seashare \
    && adduser --system --ingroup seashare seashare
COPY --from=build --chown=seashare:seashare /workspace/target/seashare-m3-0.0.1-SNAPSHOT.jar app.jar
USER seashare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
