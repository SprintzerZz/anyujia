FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app \
    && useradd --system --create-home --home-dir /home/app --gid app app \
    && mkdir -p /home/app/nacos \
    && chown -R app:app /home/app
COPY --chown=app:app anyujia-gateway.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
