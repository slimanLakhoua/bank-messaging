FROM eclipse-temurin:21.0.13_9-jre-noble
WORKDIR /app
COPY target/messaging-0.0.1-SNAPSHOT.jar app.jar
COPY docker-healthcheck.sh /app/healthcheck.sh
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 CMD ["bash", "/app/healthcheck.sh"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
