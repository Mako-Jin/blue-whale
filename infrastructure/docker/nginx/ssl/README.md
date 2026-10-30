# 放置实际 TLS 证书 (fullchain.pem / privkey.pem) 的目录
#
# 开发环境: 推荐直接用仓库脚本生成（CN=bluewhale.local，SAN 含 *.bluewhale.local /
#   bluewhale.local / localhost / 127.0.0.1，默认 825 天）：
#     powershell -ExecutionPolicy Bypass -File scripts/dev/gen-dev-certs.ps1
#   （-Force 覆盖旧证书；-Days / -CommonName / -SanList 可调）
#
# 也可以手工执行等价命令：
#   openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
#     -keyout privkey.pem -out fullchain.pem -subj "/CN=*.bluewhale.local"
#
# 生产环境: 推荐 certbot / Let's Encrypt / 证书管理器
#
# 注意: 不要把真实证书提交到 git（*.pem / *.key / *.crt 已在 .gitignore）
