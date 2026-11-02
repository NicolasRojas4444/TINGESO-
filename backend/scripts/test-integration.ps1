$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '../..')
$compose = Join-Path $root 'compose.integration.yaml'
$previousPassword = $env:INTEGRATION_DB_PASSWORD
$environmentFile = New-TemporaryFile
$exitCode = 1
try {
    $env:INTEGRATION_DB_PASSWORD = [Guid]::NewGuid().ToString('N')
    docker compose --env-file $environmentFile.FullName -p siga-integration-tests -f $compose up --build --abort-on-container-exit --exit-code-from integration-tests
    $exitCode = $LASTEXITCODE
}
finally {
    docker compose --env-file $environmentFile.FullName -p siga-integration-tests -f $compose down
    $cleanupCode = $LASTEXITCODE
    $env:INTEGRATION_DB_PASSWORD = $previousPassword
    Remove-Item -LiteralPath $environmentFile.FullName
    if ($cleanupCode -ne 0 -and $exitCode -eq 0) { $exitCode = $cleanupCode }
}
exit $exitCode
