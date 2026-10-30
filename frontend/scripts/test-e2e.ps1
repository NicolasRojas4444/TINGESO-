$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$project = 'siga-ui-check-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$variables = @('COMPOSE_PROJECT_NAME', 'POSTGRES_PORT', 'KEYCLOAK_PORT', 'BACKEND_PORT', 'FRONTEND_PORT', 'FRONTEND_URL', 'KEYCLOAK_PUBLIC_URL', 'BACKEND_PUBLIC_URL', 'CORS_ALLOWED_ORIGINS', 'TEACHER_SELF_REGISTRATION_ENABLED', 'KEYCLOAK_ADMIN', 'KEYCLOAK_ADMIN_PASSWORD', 'E2E_DATABASE_URL')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
$settings = @{}
Get-Content (Join-Path $root '.env') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1]] = $matches[2] }
}
foreach ($name in @('KEYCLOAK_ADMIN', 'KEYCLOAK_ADMIN_PASSWORD', 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($settings[$name])) { throw "Missing $name in the root .env" }
}
Push-Location $root
try {
    $env:COMPOSE_PROJECT_NAME = $project
    $env:POSTGRES_PORT = '15432'
    $env:KEYCLOAK_PORT = '18081'
    $env:BACKEND_PORT = '18080'
    $env:FRONTEND_PORT = '15173'
    $env:FRONTEND_URL = 'http://frontend:5173'
    $env:KEYCLOAK_PUBLIC_URL = 'http://keycloak:8080'
    $env:BACKEND_PUBLIC_URL = 'http://backend:8080'
    $env:CORS_ALLOWED_ORIGINS = 'http://frontend:5173'
    $env:TEACHER_SELF_REGISTRATION_ENABLED = 'true'
    $env:KEYCLOAK_ADMIN = $settings.KEYCLOAK_ADMIN
    $env:KEYCLOAK_ADMIN_PASSWORD = $settings.KEYCLOAK_ADMIN_PASSWORD
    $dbUser = [Uri]::EscapeDataString($settings.POSTGRES_USER)
    $dbPassword = [Uri]::EscapeDataString($settings.POSTGRES_PASSWORD)
    $dbName = [Uri]::EscapeDataString($settings.POSTGRES_DB)
    $env:E2E_DATABASE_URL = "postgresql://${dbUser}:${dbPassword}@postgres:5432/${dbName}"
    docker compose --progress plain up --build -d --wait --wait-timeout 240
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Docker startup failed' }
    docker build --progress plain -f frontend/Dockerfile.e2e -t siga-ipt-frontend-e2e:local frontend
    if ($LASTEXITCODE -ne 0) { throw 'Browser test image build failed' }
    $results = Join-Path $root 'frontend/test-results'
    New-Item -ItemType Directory -Path $results -Force | Out-Null
    docker run --rm --network "${project}_siga" `
        --env 'E2E_FRONTEND_URL=http://frontend:5173' `
        --env 'E2E_SECURE_ORIGIN=http://frontend:5173' `
        --env 'E2E_KEYCLOAK_URL=http://keycloak:8080' `
        --env 'E2E_BACKEND_URL=http://backend:8080' `
        --env KEYCLOAK_ADMIN --env KEYCLOAK_ADMIN_PASSWORD --env E2E_DATABASE_URL `
        --mount "type=bind,source=$results,target=/app/test-results" siga-ipt-frontend-e2e:local
    if ($LASTEXITCODE -ne 0) { throw 'Browser flow tests failed; inspect frontend/test-results locally' }
} finally {
    # Only this invocation's unique project and disposable volumes are removed.
    docker compose --project-name $project down --volumes
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    Pop-Location
}
