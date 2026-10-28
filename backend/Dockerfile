# ==========================================================
# LegacyGuard 后端镜像（多阶段构建）
# ==========================================================

# ---------- 阶段一：构建 ----------
# 用 alpine 变体：基础镜像小一半，构建阶段用完即弃
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# 容器内没有宿主的 ~/.m2/settings.xml，直接连 Maven Central 在国内会超时
COPY settings-docker.xml /root/.m2/settings.xml

# 先只复制 pom，让依赖下载单独成层 —— pom 不变时这层可以命中缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="LegacyGuard Backend"
LABEL org.opencontainers.image.description="数字遗产与账号托管平台 - 后端"

# 时区数据：alpine 默认不带 tzdata，不加会导致容器内时间是 UTC
RUN apk add --no-cache tzdata \
    && addgroup -S app \
    && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

RUN mkdir -p /app/logs && chown -R app:app /app
USER app

EXPOSE 8080

# MaxRAMPercentage 让 JVM 按容器内存上限的比例分配堆，而不是按宿主机的物理内存
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
