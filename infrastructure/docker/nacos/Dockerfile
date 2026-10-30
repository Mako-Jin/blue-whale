# 蓝鲸 Nacos 3.2.4 自定义镜像（T-16 拍板统一 3.x）
# 如需外置 MySQL 持久化, 可基于官方镜像挂载 application.properties
FROM nacos/nacos-server:v3.2.4

# 生产环境可追加自定义配置:
# COPY custom.properties /home/nacos/conf/application.properties

# 3.x 端口：8848 open-api / 9848 gRPC / 8080 控制台（2.x 的 9849 不再使用）
EXPOSE 8848 9848 8080
