# 安装仓库 Git 钩子（R1）：把 core.hooksPath 指向版本库内的 .githooks
#
# 用法：pwsh -File scripts/ci/install-hooks.ps1
# 说明：只修改当前克隆的本地 Git 配置，不影响其他开发者；不需要时执行
#   git config --unset core.hooksPath 即可恢复。

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$hook = Join-Path $root '.githooks/pre-commit'
if (-not (Test-Path -LiteralPath $hook -PathType Leaf)) {
    throw "未找到钩子文件：$hook"
}

Push-Location $root
try {
    git config core.hooksPath .githooks
    Write-Host '已安装：core.hooksPath=.githooks（提交前校验当天 devlog）'
} finally {
    Pop-Location
}
