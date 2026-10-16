# 放置实际 TLS 证书 (fullchain.pem / privkey.pem) 的目录
#
# 开发环境: 可使用 mkcert / openssl 自签证书
#   openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
#     -keyout privkey.pem -out fullchain.pem -subj "/CN=*.bluewhale.local"
#
# 生产环境: 推荐 certbot / Let's Encrypt / 证书管理器
#
# 注意: 不要把真实证书提交到 git
