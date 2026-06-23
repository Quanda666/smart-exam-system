param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string[]]$RequiredOperations = @(),
    [string[]]$RequiredOperationOutcomes = @(),
    [string]$PrometheusFile = "",
    [string]$ResultFile = "",
    [switch]$RequireSubmitReplayMismatchOutcomes,
    [switch]$AllowEmptyRequirements
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[exam-metrics] $Message"
}

function Fail {
    param([string]$Message)
    throw "Exam metrics smoke failed: $Message"
}

function Write-JsonResult {
    param($Result)
    if (-not $ResultFile) {
        return
    }
    $resultPath = if ([System.IO.Path]::IsPathRooted($ResultFile)) {
        $ResultFile
    } else {
        Join-Path (Get-Location) $ResultFile
    }
    $resultDir = Split-Path -Parent $resultPath
    if ($resultDir) {
        New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
    }
    $Result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $resultPath -Encoding UTF8
    Write-Step "Wrote metrics smoke result to $resultPath"
}

function Parse-Labels {
    param([string]$LabelsText)
    $labels = @{}
    if (-not $LabelsText) {
        return $labels
    }
    $matches = [regex]::Matches($LabelsText, '([a-zA-Z_][a-zA-Z0-9_]*)="((?:\\"|[^"])*)"')
    foreach ($match in $matches) {
        $labels[$match.Groups[1].Value] = ($match.Groups[2].Value -replace '\\"', '"')
    }
    return $labels
}

function Expand-List {
    param([string[]]$Values)
    $expanded = @()
    foreach ($value in $Values) {
        if ($null -eq $value) {
            continue
        }
        $expanded += "$value" -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    }
    return $expanded
}

function Add-MetricSample {
    param(
        [hashtable]$SamplesByOperation,
        [hashtable]$SamplesByOperationOutcome,
        [string]$Operation,
        [string]$Outcome,
        [double]$Value
    )
    if (-not $Operation) {
        return
    }
    if (-not $SamplesByOperation.ContainsKey($Operation)) {
        $SamplesByOperation[$Operation] = 0.0
    }
    $SamplesByOperation[$Operation] = [double]$SamplesByOperation[$Operation] + $Value

    if ($Outcome) {
        $key = "$Operation`:$Outcome"
        if (-not $SamplesByOperationOutcome.ContainsKey($key)) {
            $SamplesByOperationOutcome[$key] = 0.0
        }
        $SamplesByOperationOutcome[$key] = [double]$SamplesByOperationOutcome[$key] + $Value
    }
}

function New-MetricsResult {
    param(
        [bool]$Success,
        [string]$FailureReason = "",
        [string[]]$MissingOperations = @(),
        [string[]]$MissingOperationOutcomes = @(),
        [string[]]$InvalidRequiredOperationOutcomes = @(),
        [object[]]$ObservedOperations = @(),
        [object[]]$ObservedOperationOutcomes = @()
    )
    return [ordered]@{
        success = $Success
        generatedAt = (Get-Date).ToString("o")
        baseUrl = if ($PrometheusFile) { "" } else { $BaseUrl }
        prometheusFile = $PrometheusFile
        requiredOperations = $RequiredOperations
        requiredOperationOutcomes = $RequiredOperationOutcomes
        missingOperations = @($MissingOperations)
        missingOperationOutcomes = @($MissingOperationOutcomes)
        invalidRequiredOperationOutcomes = @($InvalidRequiredOperationOutcomes)
        failureReason = $FailureReason
        observedOperations = @($ObservedOperations)
        observedOperationOutcomes = @($ObservedOperationOutcomes)
    }
}

function Fail-WithMetricsResult {
    param([string]$Message)
    Write-JsonResult -Result (New-MetricsResult -Success $false -FailureReason $Message)
    Fail $Message
}

$RequiredOperations = @(Expand-List -Values $RequiredOperations)
$RequiredOperationOutcomes = @(Expand-List -Values $RequiredOperationOutcomes)
if ($RequireSubmitReplayMismatchOutcomes) {
    $RequiredOperationOutcomes += @(
        "submitExam:replay_payload_mismatch",
        "submitExam:replay_token_mismatch"
    )
}

if (-not $AllowEmptyRequirements -and $RequiredOperations.Count -eq 0 -and $RequiredOperationOutcomes.Count -eq 0) {
    Fail-WithMetricsResult "Provide RequiredOperations or RequiredOperationOutcomes, or pass -AllowEmptyRequirements"
}

$prometheusContent = ""
if ($PrometheusFile) {
    if (-not (Test-Path -LiteralPath $PrometheusFile)) {
        Fail-WithMetricsResult "PrometheusFile does not exist: $PrometheusFile"
    }
    $prometheusContent = Get-Content -LiteralPath $PrometheusFile -Raw
} else {
    $uri = $BaseUrl.TrimEnd("/") + "/actuator/prometheus"
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -Headers @{
            "X-Request-Id" = "exam-metrics-$([guid]::NewGuid().ToString("N"))"
        }
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        Fail-WithMetricsResult "GET /actuator/prometheus failed: $detail"
    }

    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        Fail-WithMetricsResult "GET /actuator/prometheus returned HTTP $($response.StatusCode)"
    }
    $prometheusContent = $response.Content
}

if ($prometheusContent -notmatch "# HELP" -and $prometheusContent -notmatch "# TYPE") {
    Fail-WithMetricsResult "/actuator/prometheus did not return Prometheus text exposition"
}

$samplesByOperation = @{}
$samplesByOperationOutcome = @{}
$counterLinePattern = '^(smart_exam_exam_operation_total(?:_total)?)(?:\{([^}]*)\})?\s+([0-9eE+\-.]+)$'

foreach ($line in ($prometheusContent -split "`n")) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith("#")) {
        continue
    }
    $match = [regex]::Match($trimmed, $counterLinePattern)
    if (-not $match.Success) {
        continue
    }
    $labels = Parse-Labels -LabelsText $match.Groups[2].Value
    $value = 0.0
    if (-not [double]::TryParse($match.Groups[3].Value, [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture, [ref]$value)) {
        continue
    }
    Add-MetricSample -SamplesByOperation $samplesByOperation `
        -SamplesByOperationOutcome $samplesByOperationOutcome `
        -Operation "$($labels["operation"])" `
        -Outcome "$($labels["outcome"])" `
        -Value $value
}

if ($samplesByOperation.Count -eq 0) {
    Write-JsonResult -Result (New-MetricsResult -Success $false -FailureReason "no smart_exam_exam_operation_total samples were found")
    Fail "no smart_exam_exam_operation_total samples were found"
}

$observedOperations = @(
    $samplesByOperation.Keys | Sort-Object | ForEach-Object {
        [ordered]@{
            operation = $_
            total = [double]$samplesByOperation[$_]
        }
    }
)
$observedOperationOutcomes = @(
    $samplesByOperationOutcome.Keys | Sort-Object | ForEach-Object {
        $parts = "$_" -split ":", 2
        [ordered]@{
            operation = $parts[0]
            outcome = if ($parts.Count -gt 1) { $parts[1] } else { "" }
            total = [double]$samplesByOperationOutcome[$_]
        }
    }
)

$missingOperations = @()
foreach ($operation in $RequiredOperations) {
    if (-not $samplesByOperation.ContainsKey($operation) -or [double]$samplesByOperation[$operation] -le 0) {
        $missingOperations += $operation
        continue
    }
    Write-Step "operation=$operation total=$($samplesByOperation[$operation])"
}

$missingOperationOutcomes = @()
$invalidRequiredOperationOutcomes = @()
foreach ($pair in $RequiredOperationOutcomes) {
    if ($pair -notmatch '^([^:]+):(.+)$') {
        $invalidRequiredOperationOutcomes += $pair
        continue
    }
    if (-not $samplesByOperationOutcome.ContainsKey($pair) -or [double]$samplesByOperationOutcome[$pair] -le 0) {
        $missingOperationOutcomes += $pair
        continue
    }
    Write-Step "$pair total=$($samplesByOperationOutcome[$pair])"
}

if ($missingOperations.Count -gt 0 -or $missingOperationOutcomes.Count -gt 0 -or $invalidRequiredOperationOutcomes.Count -gt 0) {
    $failureParts = @()
    if ($missingOperations.Count -gt 0) {
        $known = ($samplesByOperation.Keys | Sort-Object) -join ", "
        $failureParts += "missing operations: $($missingOperations -join ', ') (known: $known)"
    }
    if ($missingOperationOutcomes.Count -gt 0) {
        $knownOutcomes = ($samplesByOperationOutcome.Keys | Sort-Object) -join ", "
        $failureParts += "missing operation outcomes: $($missingOperationOutcomes -join ', ') (known: $knownOutcomes)"
    }
    if ($invalidRequiredOperationOutcomes.Count -gt 0) {
        $failureParts += "invalid required operation outcomes: $($invalidRequiredOperationOutcomes -join ', ')"
    }
    $failureReason = $failureParts -join "; "
    Write-JsonResult -Result (New-MetricsResult `
            -Success $false `
            -FailureReason $failureReason `
            -MissingOperations $missingOperations `
            -MissingOperationOutcomes $missingOperationOutcomes `
            -InvalidRequiredOperationOutcomes $invalidRequiredOperationOutcomes `
            -ObservedOperations $observedOperations `
            -ObservedOperationOutcomes $observedOperationOutcomes)
    Fail $failureReason
}
$result = New-MetricsResult -Success $true -ObservedOperations $observedOperations -ObservedOperationOutcomes $observedOperationOutcomes
Write-JsonResult -Result $result

Write-Step "PASS exam operation metrics are present"
