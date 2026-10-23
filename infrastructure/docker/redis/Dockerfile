# 蓝鲸 Redis 7 自定义镜像 (Alpine)
# 基础镜像可通过构建参数替换为国内镜像源
ARG REDIS_BASE_IMAGE=redis:7-alpine
FROM ${REDIS_BASE_IMAGE}

# 自定义配置已通过 volume 挂载: redis.conf -> /usr/local/etc/redis/redis.conf
# 此处保留默认入口, 由 docker-compose command 覆盖参数

EXPOSE 6379
