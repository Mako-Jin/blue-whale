param(
    [Parameter(Position = 0)]
    [ValidateSet('encrypt', 'decrypt')]
    [string]$Mode = 'encrypt',

    [Parameter(Position = 1)]
    [string]$Value,

    # Batch mode: one plaintext (encrypt) / ENC(...) ciphertext (decrypt) per line
    [string]$InputFile,

    # Optional output file; when omitted results are written to stdout only.
    # Never pass the secret key on the command line: it is read from
    # WHALE_CONFIG_ENC_KEY or prompted as a secure string.
    [string]$OutputFile
)

# REQ-SEC-020: generate / verify ENC(...) values for whale configuration files.
#
# Examples:
#   pwsh scripts/security/encrypt-secret.ps1 encrypt 'real-mysql-password'
#   pwsh scripts/security/encrypt-secret.ps1 decrypt 'ENC(....)'
#   Get-Content secrets.txt | ForEach-Object { ... }   # or -InputFile secrets.txt
#
# The plaintext value and the key are never written to disk by this script;
# -OutputFile is only written when explicitly requested (review before commit).

$ErrorActionPreference = 'Stop'

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePom = Join-Path $workspaceRoot 'whale-backend\whale-common\whale-config-crypto\pom.xml'
$moduleClasses = Join-Path $workspaceRoot 'whale-backend\whale-common\whale-config-crypto\target\classes'

function Resolve-Maven {
    foreach ($candidate in @(
            (Join-Path $workspaceRoot 'mvnw.cmd'),
            'mvn.cmd',
            'mvn')) {
        if ($candidate -and (Get-Command $candidate -ErrorAction SilentlyContinue)) {
            return $candidate
        }
    }
    throw 'Maven not found. Install Maven or make sure mvn/mvnw is on PATH.'
}

function Resolve-EncryptionKey {
    if ($env:WHALE_CONFIG_ENC_KEY) {
        return $env:WHALE_CONFIG_ENC_KEY
    }
    $secure = Read-Host 'WHALE_CONFIG_ENC_KEY (input hidden)' -AsSecureString
    $plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    if ([string]::IsNullOrWhiteSpace($plain)) {
        throw 'WHALE_CONFIG_ENC_KEY is required (environment variable or interactive input).'
    }
    return $plain
}

function Build-ClassPath {
    if (-not (Test-Path -LiteralPath $moduleClasses)) {
        throw "Module is not built yet: $moduleClasses. Run: mvn -q -f `"$modulePom`" -DskipTests package"
    }
    $cpFile = Join-Path ([System.IO.Path]::GetTempPath()) 'whale-config-crypto.cp.txt'
    & (Resolve-Maven) -q -B -ntp -f $modulePom dependency:build-classpath -Dmdep.outputFile=$cpFile -DincludeScope=runtime | Out-Null
    if (-not (Test-Path -LiteralPath $cpFile)) {
        throw 'Failed to resolve the whale-config-crypto runtime classpath.'
    }
    $deps = (Get-Content -LiteralPath $cpFile -Raw).Trim()
    return "$moduleClasses;$deps"
}

$key = Resolve-EncryptionKey
$env:WHALE_CONFIG_ENC_KEY = $key
$classPath = Build-ClassPath

$rawValues = @()
if ($InputFile) {
    if (-not (Test-Path -LiteralPath $InputFile)) {
        throw "Input file not found: $InputFile"
    }
    $rawValues = Get-Content -LiteralPath $InputFile | Where-Object { $_ -and $_.Trim() }
} elseif ($Value) {
    $rawValues = @($Value)
} else {
    throw "Provide a value or -InputFile. Usage: encrypt-secret.ps1 <encrypt|decrypt> <value>"
}

$results = foreach ($item in $rawValues) {
    $output = & java -cp $classPath com.bluewhale.common.security.crypto.ConfigEncryptionTool $Mode $item
    if ($LASTEXITCODE -ne 0) {
        throw "ConfigEncryptionTool failed for one of the inputs (mode=$Mode)."
    }
    $output
}

if ($OutputFile) {
    $results | Set-Content -LiteralPath $OutputFile -Encoding utf8
    Write-Host "Written to $OutputFile (contains ciphertext only)."
} else {
    $results | ForEach-Object { Write-Output $_ }
}
