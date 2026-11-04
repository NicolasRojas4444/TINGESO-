$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '../..')
Push-Location $root
try {
    docker build --progress plain -f backend/Dockerfile.test --output type=local,dest=backend/build/unit-test-artifacts backend
    if ($LASTEXITCODE -ne 0) { throw 'Unit tests or service coverage verification failed.' }

    $report = Join-Path $root 'backend/build/unit-test-artifacts/reports/jacoco/test/html/index.html'
    if (!(Test-Path -LiteralPath $report)) { throw 'The JaCoCo HTML report was not generated.' }
    Write-Output "JaCoCo service coverage report: $report"
    Start-Process $report
} finally {
    Pop-Location
}
