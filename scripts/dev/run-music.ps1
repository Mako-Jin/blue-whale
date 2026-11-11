﻿# ============================================================
# whale-music local launcher (Wave 5.75 M5 hardening)
#
# Usage:
#   powershell -File scripts/dev/run-music.ps1
#
# Env (override before running):
#   MYSQL_PASSWORD                  MySQL root password (required)
#   WHALE_MUSIC_UPLOAD_MAX_BYTES    upload limit used by bw-music-m5-smoke.ps1 (default 8192)
#   LOGGING_LEVEL_COM_BLUEWHALE     production log level, relaxed-binding to logging.level.com.bluewhale
#   MUSIC_JAR / MUSIC_HEAP          jar path / heap size
#
# Notes:
#   - Spring Boot relaxed binding: LOGGING_LEVEL_COM_BLUEWHALE=info =>
#     logging.level.com.bluewhale=info, overriding application.yml debug default.
#   - Keep this file UTF-8 with BOM so PowerShell 5.1 renders it correctly.
# ============================================================
$ErrorActionPreference = 'Stop'

$jar = if ($env:MUSIC_JAR) { $env:MUSIC_JAR } else { 'f:\blue-whale\whale-backend\whale-services\whale-music\target\whale-music.jar' }
if (-not (Test-Path -LiteralPath $jar)) { throw "jar not found: $jar" }

if (-not $env:MYSQL_PASSWORD) { throw 'MYSQL_PASSWORD not set: run with $env:MYSQL_PASSWORD=<your local password> (no built-in default since 2026-10-31)' }
if (-not $env:WHALE_MUSIC_UPLOAD_MAX_BYTES) { $env:WHALE_MUSIC_UPLOAD_MAX_BYTES = '8192' }
if (-not $env:LOGGING_LEVEL_COM_BLUEWHALE) { $env:LOGGING_LEVEL_COM_BLUEWHALE = 'info' }
if (-not $env:MUSIC_HEAP) { $env:MUSIC_HEAP = '512m' }

Write-Host "starting whale-music: jar=$jar heap=$($env:MUSIC_HEAP) uploadMax=$($env:WHALE_MUSIC_UPLOAD_MAX_BYTES) log=$($env:LOGGING_LEVEL_COM_BLUEWHALE)"
& 'D:\java\bin\java.exe' "-Xms256m" "-Xmx$($env:MUSIC_HEAP)" -jar $jar
