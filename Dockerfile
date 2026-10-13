FROM eclipse-temurin:21-jre

WORKDIR /app
COPY rbac-bootstrap/target/rbac-bootstrap-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
