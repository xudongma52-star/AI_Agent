# ── 构建阶段 ──
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true
COPY src ./src
RUN mvn package -DskipTests -B -q

# ── 运行阶段 ──
FROM eclipse-temurin:21-jre-jammy

# openhtmltopdf PDF 渲染依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    fontconfig libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 10002

ENTRYPOINT ["java", "-jar", "app.jar"]
