param(
    [string]$Python = "python"
)

$ErrorActionPreference = "Stop"
$venvDir = Join-Path $PSScriptRoot ".venv-edge-tts"
$venvPython = Join-Path $venvDir "Scripts\python.exe"

if (-not (Test-Path -LiteralPath $venvPython)) {
    & $Python -m venv $venvDir
}

& $venvPython -m pip install --upgrade pip
& $venvPython -m pip install edge-tts
& $venvPython -m edge_tts --version

Write-Host "edge-tts is ready: $venvPython"
