# ============================================================
# 蓝鲸系统 - 开发环境自签 TLS 证书生成 (Windows PowerShell)
# 产物: infrastructure/docker/nginx/ssl/{fullchain.pem,privkey.pem}
# 说明: 自签证书仅供本地开发/联调, 浏览器需手动信任或忽略告警
# 用法: .\gen-dev-cert.ps1 [-Force]
# ============================================================

param(
    [switch]$Force,
    [string]$CommonName = 'bluewhale.local',
    [int]$Days = 825
)

$ErrorActionPreference = 'Stop'

$sslDir = Resolve-Path (Join-Path $PSScriptRoot '..\docker\nginx\ssl')
$certPath = Join-Path $sslDir 'fullchain.pem'
$keyPath = Join-Path $sslDir 'privkey.pem'

if ((Test-Path $certPath) -and -not $Force) {
    Write-Host "[SKIP] 证书已存在: $certPath (使用 -Force 覆盖)" -ForegroundColor Yellow
    exit 0
}

$openssl = (Get-Command openssl -ErrorAction SilentlyContinue).Source
if (-not $openssl) {
    Write-Host "[ERR] 未找到 openssl, 请安装 OpenSSL 或改用 Docker 生成:" -ForegroundColor Red
    Write-Host '  docker run --rm -v "${PWD}/ssl:/cert" alpine/openssl req -x509 ...' -ForegroundColor Cyan
    exit 1
}

$conf = @"
[req]
distinguished_name = dn
x509_extensions = v3_req
prompt = no

[dn]
CN = $CommonName

[v3_req]
subjectAltName = @alt_names
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth

[alt_names]
DNS.1 = $CommonName
DNS.2 = localhost
DNS.3 = api.localhost
DNS.4 = app.localhost
DNS.5 = *.bluewhale.local
IP.1 = 127.0.0.1
IP.2 = ::1
"@

$confPath = Join-Path $sslDir 'openssl-san.cnf'
Set-Content -Path $confPath -Value $conf -Encoding ASCII

& $openssl req -x509 -nodes -sha256 -days $Days -newkey rsa:2048 `
    -keyout $keyPath -out $certPath -config $confPath -extensions v3_req

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERR] 证书生成失败" -ForegroundColor Red
    exit 1
}

Remove-Item $confPath -Force

Write-Host "[OK] 证书已生成:" -ForegroundColor Green
Write-Host "     $certPath"
Write-Host "     $keyPath"
& $openssl x509 -in $certPath -noout -subject -dates -ext subjectAltName
