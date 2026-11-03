param([switch]$DevelopmentOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$temporaryRoot = if ($env:TEMP) { Join-Path $env:TEMP 'opencode' } else { [IO.Path]::GetTempPath() }
$directory = Join-Path $temporaryRoot ('siga-official-check-' + [Guid]::NewGuid().ToString('N'))
$utf8 = New-Object System.Text.UTF8Encoding($false)
New-Item -ItemType Directory -Path $directory | Out-Null
$certDirectory = Join-Path $directory 'certs\live\sigarojas.duckdns.org'
New-Item -ItemType Directory -Force $certDirectory | Out-Null
$password = 'Check-' + [Guid]::NewGuid().ToString('N')
Push-Location $root
try {
    if (!$DevelopmentOnly) {
        $certMount = Join-Path $directory 'certs'
        docker run --rm --mount "type=bind,source=$certMount,target=/certs" --entrypoint bash postgres:17.6 -ec 'openssl req -x509 -newkey rsa:2048 -nodes -keyout /certs/live/sigarojas.duckdns.org/privkey.pem -out /certs/live/sigarojas.duckdns.org/fullchain.pem -days 1 -subj /CN=sigarojas.duckdns.org -addext subjectAltName=DNS:sigarojas.duckdns.org 2>/dev/null'
        if ($LASTEXITCODE) { throw 'Test certificate generation failed' }
    }
    [IO.File]::WriteAllText((Join-Path $directory 'ports.yaml'), "services:`n  nginx:`n    ports: !override`n      - '127.0.0.1:29080:80'`n      - '127.0.0.1:29443:443'`n", $utf8)
    $modes = if ($DevelopmentOnly) { @('dev') } else { @('dev', 'prod') }
    foreach ($mode in $modes) {
        $project = 'siga-official-' + $mode + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
        $settings = @(
            "COMPOSE_PROJECT_NAME=$project", 'POSTGRES_DB=siga_official_demo_test', 'POSTGRES_USER=official_check',
            "POSTGRES_PASSWORD=$password", "KEYCLOAK_DB_PASSWORD=$password", 'KEYCLOAK_ADMIN=check-admin',
            "KEYCLOAK_ADMIN_PASSWORD=$password", "KEYCLOAK_REGISTRATION_CLIENT_SECRET=$password",
            'KEYCLOAK_REQUEST_TIMEOUT_SECONDS=10', 'POSTGRES_PORT=25432', 'KEYCLOAK_PORT=28081',
            'BACKEND_PORT=28080', 'FRONTEND_PORT=25173', 'KEYCLOAK_PUBLIC_URL=http://keycloak:8080',
            'FRONTEND_URL=http://localhost:25173', 'CORS_ALLOWED_ORIGINS=http://localhost:25173',
            'DEMO_SETUP_ENABLED=true', 'DEMO_PASSWORD=tuki123', 'APP_PULL_POLICY=build',
            'PUBLIC_URL=https://sigarojas.duckdns.org:29443', "LETSENCRYPT_DIRECTORY=$($directory.Replace('\','/'))/certs",
            'BACKEND_IMAGE=siga-official-backend:check', 'FRONTEND_IMAGE=siga-official-frontend:check'
        )
        $envFile = Join-Path $directory "$mode.env"
        [IO.File]::WriteAllLines($envFile, $settings, $utf8)
        $compose = @('compose', '--env-file', $envFile, '-f', 'compose.yaml')
        if ($mode -eq 'prod') { $compose = @('compose', '--env-file', $envFile, '-f', 'compose.production.yaml', '-f', (Join-Path $directory 'ports.yaml')) }
        try {
            foreach ($cycle in 1..2) {
                & docker @compose up --build -d --wait --wait-timeout 420
                if ($LASTEXITCODE) { throw "$mode initial startup/recreation failed" }
                & docker @compose run --rm --no-deps --entrypoint node -e ISOLATED_OFFICIAL_DEMO_TEST=true demo-data tests/official-demo-integration.mjs
                if ($LASTEXITCODE) { throw "$mode official academic workflow failed" }
                & docker @compose up --build -d --wait --wait-timeout 300
                if ($LASTEXITCODE) { throw "$mode repeated startup failed" }
                & docker @compose run --rm --no-deps --entrypoint node -e ISOLATED_OFFICIAL_DEMO_TEST=true demo-data tests/official-restart.mjs
                if ($LASTEXITCODE) { throw "$mode restart lost user data" }
                if ($mode -eq 'prod') {
                    $code = curl.exe --silent --show-error --resolve sigarojas.duckdns.org:29443:127.0.0.1 --cacert "$certDirectory\fullchain.pem" -o NUL -w '%{http_code}' https://sigarojas.duckdns.org:29443/api/v3/api-docs
                    if ($LASTEXITCODE -or $code -ne '200') { throw 'Production HTTPS API unavailable' }
                }
                if ($cycle -eq 1) {
                    & docker @compose run --rm --no-deps --entrypoint node -e ISOLATED_OFFICIAL_DEMO_TEST=true demo-data tests/official-migration.mjs
                    if ($LASTEXITCODE) { throw "$mode legacy data migration failed" }
                    & docker @compose run --rm --no-deps --entrypoint node demo-data verify-demo.mjs
                    if ($LASTEXITCODE) { throw "$mode migrated demo passwords/access changed" }
                }
                Write-Output "PASS $mode cycle $cycle : automatic catalog/accounts, academic workflow and restart preservation."
                # Only this uniquely named disposable project is ever deleted.
                & docker @compose down --volumes
                if ($LASTEXITCODE) { throw 'Disposable reset failed' }
            }
            Write-Output "PASS $mode : down --volumes followed by a single up --build recreates the official initial demo."
        } finally {
            & docker @compose down --volumes
        }
    }
} finally {
    foreach ($name in @('dev.env', 'prod.env', 'ports.yaml', 'certs\live\sigarojas.duckdns.org\fullchain.pem', 'certs\live\sigarojas.duckdns.org\privkey.pem')) {
        $path = Join-Path $directory $name
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
    Pop-Location
}
