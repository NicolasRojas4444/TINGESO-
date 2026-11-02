$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '../..')
$compose = Join-Path $root 'compose.yaml'
$environmentFile = New-TemporaryFile
$exitCode = 1
try {
    # Generate disposable test settings; never read or change the application .env.
    # Only integration-tests and its isolated database dependency are started.
    $password = [Guid]::NewGuid().ToString('N')
    $settings = @"
POSTGRES_DB=siga_integration
POSTGRES_USER=siga_integration
POSTGRES_PASSWORD=$password
KEYCLOAK_DB_PASSWORD=$password
KEYCLOAK_ADMIN=integration-admin
KEYCLOAK_ADMIN_PASSWORD=$password
KEYCLOAK_REGISTRATION_CLIENT_SECRET=$password
INTEGRATION_DB_PASSWORD=$password
POSTGRES_PORT=5432
BACKEND_PORT=8080
KEYCLOAK_PORT=8081
FRONTEND_PORT=5173
FRONTEND_URL=http://localhost:5173
KEYCLOAK_PUBLIC_URL=http://localhost:8081
CORS_ALLOWED_ORIGINS=http://localhost:5173
JPA_DDL_AUTO=validate
SELF_REGISTRATION_ENABLED=false
TEACHER_SELF_REGISTRATION_ENABLED=false
KEYCLOAK_REQUEST_TIMEOUT_SECONDS=10
"@
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
