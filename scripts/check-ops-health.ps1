param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [switch]$SkipActuator,
    [switch]$ExpectPrometheus
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[ops-health] $Message"
}

function Fail {
    param([string]$Message)
    throw "Ops health check failed: $Message"
}

function Invoke-HealthEndpoint {
    param([string]$Path)
    $uri = $BaseUrl.TrimEnd("/") + $Path
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -Headers @{
            "X-Request-Id" = "ops-health-$([guid]::NewGuid().ToString("N"))"
        }
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        Fail "GET $Path failed: $detail"
    }
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        Fail "GET $Path returned HTTP $($response.StatusCode)"
    }
    $requestId = $response.Headers["X-Request-Id"]
    if (-not $requestId) {
        Fail "GET $Path did not return X-Request-Id"
    }
    Write-Step "GET $Path -> $($response.StatusCode), requestId=$requestId"
    return $response
}

$apiHealth = Invoke-HealthEndpoint -Path "/api/health"
$apiJson = $apiHealth.Content | ConvertFrom-Json
if (-not $apiJson.success) {
    Fail "/api/health returned success=false"
}
if (-not $apiJson.data.application) {
    Fail "/api/health did not return application"
}

if (-not $SkipActuator) {
    $liveness = Invoke-HealthEndpoint -Path "/actuator/health/liveness"
    $readiness = Invoke-HealthEndpoint -Path "/actuator/health/readiness"
    $metrics = Invoke-HealthEndpoint -Path "/actuator/metrics"
    ($liveness.Content | ConvertFrom-Json) | Out-Null
    ($readiness.Content | ConvertFrom-Json) | Out-Null
    ($metrics.Content | ConvertFrom-Json) | Out-Null
    if ($ExpectPrometheus) {
        $prometheus = Invoke-HealthEndpoint -Path "/actuator/prometheus"
        if ($prometheus.Content -notmatch "# HELP" -and $prometheus.Content -notmatch "# TYPE") {
            Fail "/actuator/prometheus did not return Prometheus text exposition"
        }
    }
}

Write-Step "PASS health endpoints are reachable and trace headers are present"
