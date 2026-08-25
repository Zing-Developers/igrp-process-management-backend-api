FROM docker.tools.irn.internal/base/java-sdk:latest AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true clean package && ls -lh target

FROM docker.tools.irn.internal/base/java-jre:latest
WORKDIR /app
COPY --from=build --chown=javauser:javauser /app/target/*.jar /app/app.jar

EXPOSE 8080

USER javauser:javauser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

