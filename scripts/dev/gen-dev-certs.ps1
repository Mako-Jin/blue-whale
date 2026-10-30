param(
    [string]$OutDir,
    [string]$CommonName = 'bluewhale.local',
    [string]$SanList = 'DNS:*.bluewhale.local,DNS:bluewhale.local,DNS:localhost,IP:127.0.0.1',
    [int]$Days = 825,
    [switch]$Force
)

# Generate a local self-signed TLS certificate for the dev/staging nginx front door.
# Production must use Let's Encrypt / a managed certificate instead (see SSL README).
#
# Files are written to infrastructure/docker/nginx/ssl (fullchain.pem + privkey.pem).
# Both extensions are covered by .gitignore, so nothing is committed.

$ErrorActionPreference = 'Stop'

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $workspaceRoot 'infrastructure\docker\nginx\ssl' }
$OutDir = (Resolve-Path -LiteralPath (New-Item -ItemType Directory -Force -Path $OutDir).FullName).Path
$crtPath = Join-Path $OutDir 'fullchain.pem'
$keyPath = Join-Path $OutDir 'privkey.pem'

function Resolve-OpenSsl {
    $cmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($candidate in @(
            'C:\Program Files\Git\usr\bin\openssl.exe',
            'C:\Program Files\OpenSSL-Win64\bin\openssl.exe',
            'C:\Program Files\OpenSSL\bin\openssl.exe')) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    throw 'openssl not found. Install OpenSSL (or Git for Windows) and make sure openssl is on PATH.'
}

$openssl = Resolve-OpenSsl

if ((Test-Path -LiteralPath $crtPath) -and (Test-Path -LiteralPath $keyPath) -and (-not $Force)) {
    Write-Host "Certificate already exists: $crtPath"
    Write-Host 'Use -Force to regenerate.'
    & $openssl x509 -in $crtPath -noout -subject -dates -ext subjectAltName
    exit 0
}

Write-Host "Generating self-signed certificate (CN=$CommonName, $Days days) into $OutDir"

# A minimal openssl.cnf is generated on every run: some Windows OpenSSL builds ship no
# default config, and passing -config keeps the output deterministic across versions.
$configPath = Join-Path $env:TEMP ("bluewhale-openssl-{0}.cnf" -f ([guid]::NewGuid().ToString('N')))
$configText = @"
[req]
distinguished_name = dn
x509_extensions = v3_req
prompt = no

[dn]
CN = $CommonName

[v3_req]
subjectAltName = $SanList
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
"@
Set-Content -LiteralPath $configPath -Value $configText -Encoding ASCII

try {
    & $openssl req -x509 -nodes -newkey rsa:2048 `
        -days $Days `
        -keyout $keyPath `
        -out $crtPath `
        -config $configPath
    if ($LASTEXITCODE -ne 0) { throw "openssl req failed (exit=$LASTEXITCODE)" }
} finally {
    Remove-Item -LiteralPath $configPath -Force -ErrorAction SilentlyContinue
}

& $openssl x509 -in $crtPath -noout -subject -dates -ext subjectAltName
if ($LASTEXITCODE -ne 0) { throw "openssl x509 verification failed (exit=$LASTEXITCODE)" }

Write-Host ''
Write-Host "Done. key=$keyPath cert=$crtPath"
Write-Host "Nginx mounts this folder at /etc/nginx/ssl (see infrastructure/docker/nginx)."
