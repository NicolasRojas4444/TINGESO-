FROM eclipse-temurin:21.0.9_10-jdk-jammy AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon --console=plain -x test

FROM eclipse-temurin:21.0.9_10-jre-jammy
WORKDIR /app
RUN groupadd --system siga && useradd --system --gid siga siga
COPY --from=build --chown=siga:siga /app/build/libs/*-SNAPSHOT.jar app.jar
USER siga
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
