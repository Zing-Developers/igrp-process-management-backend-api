FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true clean package && ls -lh target

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar

COPY certs/irn/*.crt /usr/local/share/ca-certificates/

RUN apt-get update && apt-get install -y ca-certificates && \
    update-ca-certificates && \
    for cert in /usr/local/share/ca-certificates/*.crt; do \
      keytool -importcert -trustcacerts \
      -keystore "$JAVA_HOME/lib/security/cacerts" \
      -storepass changeit -noprompt \
      -alias "$(basename $cert .crt)" \
      -file "$cert"; \
    done && \
    rm -rf /var/lib/apt/lists/*

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

