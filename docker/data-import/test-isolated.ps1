$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$project = 'siga-data-check-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$names = @('COMPOSE_PROJECT_NAME', 'POSTGRES_PORT', 'KEYCLOAK_PORT', 'BACKEND_PORT', 'KEYCLOAK_PUBLIC_URL', 'LOCAL_DATA_SETUP_ENABLED', 'ISOLATED_DATA_IMPORT_TEST')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
Push-Location $root
try {
    $env:COMPOSE_PROJECT_NAME = $project
    $env:POSTGRES_PORT = '15432'
    $env:KEYCLOAK_PORT = '18081'
    $env:BACKEND_PORT = '18080'
    $env:KEYCLOAK_PUBLIC_URL = 'http://keycloak:8080'
    $env:LOCAL_DATA_SETUP_ENABLED = 'true'
    docker compose up -d --wait --wait-timeout 240 backend
    if ($LASTEXITCODE -ne 0) { throw 'Isolated startup failed' }
    docker compose -f compose.yaml -f compose.data.yaml run --build --rm --entrypoint node --env ISOLATED_DATA_IMPORT_TEST=true academic-import tests/integration.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Data import integration checks failed' }
} finally {
    # The unique project is disposable; main volumes are never targeted.
    docker compose --project-name $project down --volumes
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    Pop-Location
}
