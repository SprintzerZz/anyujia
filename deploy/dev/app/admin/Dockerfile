FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --chown=app:app anyujia-admin.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
