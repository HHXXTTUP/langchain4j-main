param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d{8}$')]
    [string]$ExpiryDate
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$privateKey = Join-Path $projectRoot '.private\monitor-license-ed25519.key'

if (-not (Test-Path -LiteralPath $privateKey)) {
    throw "Monitor signing private key was not found: $privateKey"
}

Push-Location $projectRoot
try {
    & mvn test-compile
    if ($LASTEXITCODE -ne 0) { throw 'Maven test compilation failed.' }
    & java -cp 'target\test-classes;target\classes' `
        dev.learning.stockanalyzer.service.MonitorLicenseTokenGenerator `
        $ExpiryDate `
        $privateKey
    if ($LASTEXITCODE -ne 0) { throw 'Monitor expiry generation failed.' }
} finally {
    Pop-Location
}
