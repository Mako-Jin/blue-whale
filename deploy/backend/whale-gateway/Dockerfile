# ============================================================
# 蓝鲸 API Gateway - Spring Boot 多阶段构建
# ============================================================

# ---- Stage 1: Maven Build ----
FROM maven:3-eclipse-temurin-21 AS builder

WORKDIR /build

# 先拷贝 pom.xml 并下载依赖, 利用 Docker 层缓存
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# 再拷贝源码并打包
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runner

LABEL org.opencontainers.image.title="bluewhale-gateway"
LABEL org.opencontainers.image.description="蓝鲸 API Gateway"
LABEL org.opencontainers.image.vendor="BlueWhale"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

WORKDIR /app

# 从构建产物拷贝 jar
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
