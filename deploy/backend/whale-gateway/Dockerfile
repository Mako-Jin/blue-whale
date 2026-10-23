# ============================================================
# 蓝鲸 API Gateway - Spring Boot 多阶段构建
# 构建上下文：仓库根目录
#   docker build -f deploy/backend/whale-gateway/Dockerfile -t bluewhale/whale-gateway .
# 基础镜像可经 --build-arg 指向国内镜像站（Docker Hub 直连不稳定）
# ============================================================

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ---- Stage 1: Maven Build ----
FROM ${MAVEN_IMAGE} AS builder

WORKDIR /build

# 后端为 Maven 多模块工程（backend/pom.xml 聚合），单模块无法脱离聚合 POM 构建，
# 因此拷贝整个 backend 目录；.mvn 与 config/checkstyle 供 Maven Wrapper 与 Checkstyle 门禁使用
COPY .mvn .mvn
COPY config/checkstyle config/checkstyle
COPY backend backend

# -pl 指定模块、-am 连带构建依赖模块；跳过测试（测试由 CI 单独执行）
RUN mvn -B -ntp -f backend/pom.xml -pl whale-services/whale-gateway -am package -DskipTests \
    && mkdir -p /out \
    && cp backend/whale-services/whale-gateway/target/*.jar /out/app.jar

# ---- Stage 2: Runtime ----
FROM ${JRE_IMAGE} AS runner

LABEL org.opencontainers.image.title="bluewhale-gateway"
LABEL org.opencontainers.image.description="蓝鲸 API Gateway"
LABEL org.opencontainers.image.vendor="BlueWhale"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && addgroup -S whale \
    && adduser -S -G whale whale

WORKDIR /app

COPY --from=builder /out/app.jar app.jar
RUN chown -R whale:whale /app

# 非 root 运行
USER whale

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
