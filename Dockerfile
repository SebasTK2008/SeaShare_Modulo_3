FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN addgroup --system seashare && adduser --system --ingroup seashare seashare
COPY --from=build /workspace/target/seashare-m3-0.0.1-SNAPSHOT.jar app.jar
USER seashare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
