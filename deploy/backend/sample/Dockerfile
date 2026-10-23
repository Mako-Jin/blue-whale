# ============================================================
# 蓝鲸后端业务服务通用 Dockerfile 模板（参数化，含网关/业务/APP 均适用）
# 构建上下文：仓库根目录
#   docker build -f deploy/backend/sample/Dockerfile \
#     --build-arg MODULE_PATH=whale-services/whale-auth \
#     --build-arg SERVICE_PORT=8081 -t bluewhale/whale-auth .
# 说明：MODULE_PATH 为相对 backend/ 的模块路径（Maven -pl 坐标）
# ============================================================

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ---- Stage 1: Maven Build ----
FROM ${MAVEN_IMAGE} AS builder

# 构建期参数（FROM 之后需重新声明才能用于 RUN）
ARG MODULE_PATH=whale-services/whale-auth

WORKDIR /build

COPY .mvn .mvn
COPY config/checkstyle config/checkstyle
COPY backend backend

RUN mvn -B -ntp -f backend/pom.xml -pl "${MODULE_PATH}" -am package -DskipTests \
    && mkdir -p /out \
    && cp "backend/${MODULE_PATH}"/target/*.jar /out/app.jar

# ---- Stage 2: Runtime ----
FROM ${JRE_IMAGE} AS runner

ARG SERVICE_PORT=8081

LABEL org.opencontainers.image.title="bluewhale-service"
LABEL org.opencontainers.image.description="蓝鲸后端业务服务（通用模板）"
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

USER whale

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
