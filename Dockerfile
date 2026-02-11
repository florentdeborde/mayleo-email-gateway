# Step 1 : Build with Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
COPY . /app
WORKDIR /app
RUN mvn clean package -DskipTests

# Step 2 : Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]