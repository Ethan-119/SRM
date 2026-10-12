# ============================================
# SRM 后端应用镜像（多阶段构建）
# 构建：docker build -t srm-app .
# ============================================

# ---- 1. 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY maven-settings.xml /root/.m2/settings.xml
RUN mvn -B -s /root/.m2/settings.xml dependency:go-offline
COPY src ./src
RUN mvn -B -s /root/.m2/settings.xml clean package -DskipTests

# ---- 2. 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
