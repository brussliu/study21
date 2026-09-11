# Study 2.1 — user-api 镜像（多阶段）
# 阶段1：Maven 构建（含 common-core / common-security 依赖模块）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend ./backend
WORKDIR /build/backend
RUN mvn -B -pl user-api -am clean package -DskipTests

# 阶段2：JRE 运行环境
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/backend/user-api/target/user-api-0.1.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
