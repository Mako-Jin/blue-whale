# ============================================================
# 蓝鲸后端服务通用 Dockerfile 模板
# 复制到具体服务目录并按需调整
# ============================================================

ARG MAVEN_VERSION=3
ARG JDK_VERSION=21
ARG BASE_IMAGE=eclipse-temurin

# ---- Stage 1: Maven Build ----
FROM maven:${MAVEN_VERSION}-${BASE_IMAGE}-${JDK_VERSION} AS builder

WORKDIR /build

# 缓存依赖
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# 根据实际模块路径替换: bluewhale-sample 改为你的 artifactId
RUN mvn -B -ntp clean package -DskipTests

# ---- Stage 2: Runtime ----
FROM ${BASE_IMAGE}:${JDK_VERSION}-jre-alpine AS runner

LABEL org.opencontainers.image.title="bluewhale-sample"
LABEL org.opencontainers.image.description="蓝鲸示例后端服务"
LABEL org.opencontainers.image.vendor="BlueWhale"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

WORKDIR /app

# 从构建产物拷贝 jar (示例 artifactId)
COPY --from=builder /build/target/bluewhale-sample*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
