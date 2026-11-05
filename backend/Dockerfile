
# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
# Тести виконуються окремо (потрібен Docker для Testcontainers), тут лише збірка артефакту.
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/app.jar extract --layers --launcher --destination build/extracted

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
