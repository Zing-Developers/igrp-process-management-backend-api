FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true clean package && ls -lh target

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
RUN useradd --system --no-create-home --uid 1001 appuser
USER 1001

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

