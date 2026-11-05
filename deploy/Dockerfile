# The context is a release tag from "git archive". See scripts/deploy-live.sh.
# The Java version is java.version in pom.xml.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY . .
RUN ./mvnw -q -B -DskipTests clean package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/app/target/app-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
