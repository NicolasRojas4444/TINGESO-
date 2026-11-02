$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '../..')
$compose = Join-Path $root 'compose.yaml'
$environmentFile = New-TemporaryFile
$exitCode = 1
try {
    # Satisfy Compose interpolation using only the non-secret example, never .env.
    # Only integration-tests and its isolated database dependency are started.
    $password = [Guid]::NewGuid().ToString('N')
    $settings = [System.IO.File]::ReadAllText((Join-Path $root '.env.example'))
    $settings += "`nPOSTGRES_PASSWORD=$password`nKEYCLOAK_DB_PASSWORD=$password`nKEYCLOAK_ADMIN_PASSWORD=$password`nKEYCLOAK_REGISTRATION_CLIENT_SECRET=$password`nINTEGRATION_DB_PASSWORD=$password`n"
    [System.IO.File]::WriteAllText($environmentFile.FullName, $settings)
    docker compose --env-file $environmentFile.FullName -p siga-integration-tests -f $compose up --build --abort-on-container-exit --exit-code-from integration-tests integration-tests
    $exitCode = $LASTEXITCODE
}
finally {
    docker compose --env-file $environmentFile.FullName -p siga-integration-tests -f $compose down
    $cleanupCode = $LASTEXITCODE
    Remove-Item -LiteralPath $environmentFile.FullName
    if ($cleanupCode -ne 0 -and $exitCode -eq 0) { $exitCode = $cleanupCode }
}
exit $exitCode
