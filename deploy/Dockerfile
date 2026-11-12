# The context is a release tag from "git archive". See scripts/deploy-live.sh.
# The Java version is java.version in pom.xml.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY . .
RUN ./mvnw -q -B -DskipTests clean package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/app/target/app-*.jar app.jar
# The settings folder of Claude Code. Compose puts a volume on it, and the volume takes this mode on
# its first start. The container runs with the UID of the host user, so any user must write here.
RUN mkdir -m 1777 /claude-config
ENTRYPOINT ["java", "-jar", "app.jar"]
