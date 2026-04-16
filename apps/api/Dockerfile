FROM docker.1ms.run/library/maven:3.9.9-eclipse-temurin-22 AS builder

WORKDIR /app

COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM docker.1ms.run/library/eclipse-temurin:22-jre-jammy AS runner

WORKDIR /app

RUN groupadd --system spring \
 && useradd --system --gid spring --create-home spring

COPY --from=builder /app/target/*.jar /app/app.jar
RUN chown -R spring:spring /app

USER spring

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
