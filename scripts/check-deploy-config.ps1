param(
    [string]$EnvFile = ".env",
    [switch]$UseExample,
    [switch]$SkipDockerConfig,
    [switch]$Strict
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[deploy-config] $Message"
}

function Warn {
    param([string]$Message)
    Write-Warning "[deploy-config] $Message"
}

function Fail {
    param([string]$Message)
    throw "Deploy config check failed: $Message"
}

function Resolve-ConfigPath {
    param([string]$Path)
    if (Test-Path -LiteralPath $Path) {
        return (Resolve-Path -LiteralPath $Path).Path
    }
    return $null
}

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    if (-not $Path) {
        return $values
    }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $pair = $line -split "=", 2
        if ($pair.Count -ne 2) {
            return
        }
        $key = $pair[0].Trim()
        $value = $pair[1].Trim()
        if ($value.Length -ge 2) {
            $first = $value.Substring(0, 1)
            $last = $value.Substring($value.Length - 1, 1)
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        $values[$key] = $value
    }
    return $values
}

function Get-ValueOrDefault {
    param(
        [hashtable]$Values,
        [string]$Key,
        [string]$DefaultValue
    )
    if ($Values.ContainsKey($Key) -and $null -ne $Values[$Key] -and "$($Values[$Key])" -ne "") {
        return "$($Values[$Key])"
    }
    return $DefaultValue
}

function Assert-Port {
    param(
        [hashtable]$Values,
        [string]$Key,
        [string]$DefaultValue
    )
    $raw = Get-ValueOrDefault -Values $Values -Key $Key -DefaultValue $DefaultValue
    $port = 0
    if (-not [int]::TryParse($raw, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
        Fail "$Key must be a TCP port between 1 and 65535, got '$raw'"
    }
    return $port
}

function Warn-Or-Fail {
    param([string]$Message)
    if ($Strict) {
        Fail $Message
    }
    Warn $Message
}

$envPath = if ($UseExample) { ".env.example" } else { $EnvFile }
$resolvedEnv = Resolve-ConfigPath -Path $envPath

if (-not $resolvedEnv) {
    if ($UseExample -or $EnvFile -ne ".env") {
        Fail "environment file '$envPath' does not exist"
    }
    Warn ".env not found; checking .env.example instead. Copy .env.example to .env before running the stack."
    $resolvedEnv = Resolve-ConfigPath -Path ".env.example"
}

if (-not $resolvedEnv) {
    Fail "no environment template found"
}

Write-Step "Checking environment file: $resolvedEnv"
$values = Read-DotEnv -Path $resolvedEnv

$required = @(
    "MYSQL_DATABASE",
    "MYSQL_USER",
    "MYSQL_PASSWORD",
    "MYSQL_ROOT_PASSWORD",
    "SERVER_PORT",
    "FRONTEND_PORT",
    "REDIS_PORT",
    "PROMETHEUS_PORT"
)

foreach ($key in $required) {
    if (-not $values.ContainsKey($key)) {
        Fail "$key is missing from $resolvedEnv"
    }
}

$serverPort = Assert-Port -Values $values -Key "SERVER_PORT" -DefaultValue "8080"
$frontendPort = Assert-Port -Values $values -Key "FRONTEND_PORT" -DefaultValue "3000"
$mysqlPort = Assert-Port -Values $values -Key "MYSQL_PORT" -DefaultValue "3306"
$redisPort = Assert-Port -Values $values -Key "REDIS_PORT" -DefaultValue "6379"
$prometheusPort = Assert-Port -Values $values -Key "PROMETHEUS_PORT" -DefaultValue "9090"
$backupRetentionDaysRaw = Get-ValueOrDefault -Values $values -Key "BACKUP_RETENTION_DAYS" -DefaultValue "14"
$backupRetentionDays = 0
if (-not [int]::TryParse($backupRetentionDaysRaw, [ref]$backupRetentionDays) -or $backupRetentionDays -lt 0) {
    Fail "BACKUP_RETENTION_DAYS must be a non-negative integer, got '$backupRetentionDaysRaw'"
}
$loadConcurrencyRaw = Get-ValueOrDefault -Values $values -Key "LOAD_TEST_DEFAULT_CONCURRENCY" -DefaultValue "20"
$loadConcurrency = 0
if (-not [int]::TryParse($loadConcurrencyRaw, [ref]$loadConcurrency) -or $loadConcurrency -lt 1) {
    Fail "LOAD_TEST_DEFAULT_CONCURRENCY must be a positive integer, got '$loadConcurrencyRaw'"
}
$loadIterationsRaw = Get-ValueOrDefault -Values $values -Key "LOAD_TEST_DEFAULT_ITERATIONS" -DefaultValue "5"
$loadIterations = 0
if (-not [int]::TryParse($loadIterationsRaw, [ref]$loadIterations) -or $loadIterations -lt 1) {
    Fail "LOAD_TEST_DEFAULT_ITERATIONS must be a positive integer, got '$loadIterationsRaw'"
}

$ports = @{
    SERVER_PORT = $serverPort
    FRONTEND_PORT = $frontendPort
    MYSQL_PORT = $mysqlPort
    REDIS_PORT = $redisPort
    PROMETHEUS_PORT = $prometheusPort
}
$seenPorts = @{}
foreach ($entry in $ports.GetEnumerator()) {
    if ($seenPorts.ContainsKey($entry.Value)) {
        Warn-Or-Fail "$($entry.Key) conflicts with $($seenPorts[$entry.Value]) on port $($entry.Value)"
    } else {
        $seenPorts[$entry.Value] = $entry.Key
    }
}

$mysqlPassword = Get-ValueOrDefault -Values $values -Key "MYSQL_PASSWORD" -DefaultValue ""
$mysqlRootPassword = Get-ValueOrDefault -Values $values -Key "MYSQL_ROOT_PASSWORD" -DefaultValue ""
$weakPasswords = @("root", "password", "smartexam123", "123456")

if ($weakPasswords -contains $mysqlPassword.ToLowerInvariant()) {
    Warn-Or-Fail "MYSQL_PASSWORD uses a weak sample value; change it outside throwaway local runs"
}
if ($weakPasswords -contains $mysqlRootPassword.ToLowerInvariant()) {
    Warn-Or-Fail "MYSQL_ROOT_PASSWORD uses a weak sample value; change it outside throwaway local runs"
}
if ($mysqlPassword -and $mysqlRootPassword -and $mysqlPassword -eq $mysqlRootPassword) {
    Warn-Or-Fail "MYSQL_PASSWORD and MYSQL_ROOT_PASSWORD are identical; use separate credentials"
}

$composePath = Resolve-ConfigPath -Path "docker-compose.yml"
if (-not $composePath) {
    Fail "docker-compose.yml is missing"
}
$composeText = Get-Content -LiteralPath $composePath -Raw
foreach ($needle in @("smart-exam-redis", "smart-exam-prometheus", "MYSQL_ROOT_PASSWORD: `${MYSQL_ROOT_PASSWORD")) {
    if ($composeText -notlike "*$needle*") {
        Fail "docker-compose.yml does not contain expected fragment: $needle"
    }
}

$prometheusPath = Resolve-ConfigPath -Path "deploy\prometheus.yml"
if (-not $prometheusPath) {
    Fail "deploy\prometheus.yml is missing"
}
$prometheusText = Get-Content -LiteralPath $prometheusPath -Raw
if ($prometheusText -notmatch "/actuator/prometheus" -or $prometheusText -notmatch "backend:8080") {
    Fail "deploy\prometheus.yml must scrape backend:8080/actuator/prometheus"
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Warn "Docker CLI not found; skipped docker compose config validation"
} elseif (-not $SkipDockerConfig) {
    Write-Step "Running docker compose config"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $configOutput = & docker compose --env-file $resolvedEnv config 2>&1
        $composeExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($composeExitCode -ne 0) {
        $message = ($configOutput | Select-Object -Last 12) -join [Environment]::NewLine
        Fail "docker compose config failed:$([Environment]::NewLine)$message"
    }
    Write-Step "docker compose config PASS"
}

Write-Step "PASS deployment config is structurally valid"
