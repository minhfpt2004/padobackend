FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system pado && useradd --system --gid pado --home-dir /app pado

COPY --from=build --chown=pado:pado /workspace/target/*.jar /app/app.jar

USER pado

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
