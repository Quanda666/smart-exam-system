param(
    [string]$AcceptanceResultFile = "artifacts/nightly-acceptance-result.json",
    [string]$LoadResultFile = "artifacts/nightly-load-smoke-result.json",
    [string]$MetricsResultFile = "artifacts/nightly-metrics-smoke-result.json",
    [string]$OutputFile = "",
    [switch]$AlsoWriteStepSummary
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[summary] $Message"
}

function Read-JsonFileOrNull {
    param([string]$Path)
    if (-not $Path -or -not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        Write-Step "Could not parse JSON file ${Path}: $($_.Exception.Message)"
        return $null
    }
}

function Get-JsonValue {
    param(
        $Object,
        [string]$Name
    )
    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Value-OrDash {
    param($Value)
    if ($null -eq $Value -or "$Value" -eq "") {
        return "-"
    }
    return "$Value"
}

function Redact-Secret {
    param($Value)
    if ($null -eq $Value) {
        return ""
    }
    $text = "$Value"
    $text = $text -replace '(?i)studentPassword[^:=,}]*[:=]\s*[^,\s}]+', 'studentPassword=***'
    $text = $text -replace '(?i)(^|[^A-Za-z])password[^:=,}]*[:=]\s*[^,\s}]+', '${1}password=***'
    return $text
}

function Bool-Status {
    param($Value)
    if ($null -eq $Value) {
        return "unknown"
    }
    if ([bool]$Value) {
        return "PASS"
    }
    return "FAIL"
}

function Add-Section {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Title
    )
    $Lines.Add("")
    $Lines.Add("## $Title")
}

function Add-FieldLine {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Label,
        $Value
    )
    $displayValue = Value-OrDash $Value
    $Lines.Add("| $Label | $displayValue |")
}

$acceptance = Read-JsonFileOrNull -Path $AcceptanceResultFile
$load = Read-JsonFileOrNull -Path $LoadResultFile
$metrics = Read-JsonFileOrNull -Path $MetricsResultFile

$acceptanceStatus = Bool-Status (Get-JsonValue -Object $acceptance -Name "success")
$loadStatus = Bool-Status (Get-JsonValue -Object $load -Name "success")
$metricsStatus = Bool-Status (Get-JsonValue -Object $metrics -Name "success")

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Smart Exam Nightly Acceptance")
$lines.Add("")
$lines.Add("| Area | Status |")
$lines.Add("| --- | --- |")
$lines.Add("| Attempt resilience | $acceptanceStatus |")
$lines.Add("| Load smoke | $loadStatus |")
$lines.Add("| Metrics smoke | $metricsStatus |")

Add-Section -Lines $lines -Title "Attempt Fixture"
if ($null -ne $acceptance) {
    $lines.Add("| Field | Value |")
    $lines.Add("| --- | --- |")
    Add-FieldLine -Lines $lines -Label "Mode" -Value (Get-JsonValue -Object $acceptance -Name "mode")
    Add-FieldLine -Lines $lines -Label "Attempt ID" -Value (Get-JsonValue -Object $acceptance -Name "attemptId")
    Add-FieldLine -Lines $lines -Label "Exam ID" -Value (Get-JsonValue -Object $acceptance -Name "examId")
    Add-FieldLine -Lines $lines -Label "Student" -Value (Get-JsonValue -Object $acceptance -Name "studentUsername")
    Add-FieldLine -Lines $lines -Label "Cleanup After Run" -Value (Get-JsonValue -Object $acceptance -Name "cleanupAfterRun")
} else {
    $lines.Add("Acceptance result file is missing or unreadable: $AcceptanceResultFile.")
}

Add-Section -Lines $lines -Title "Load Smoke"
if ($null -ne $load) {
    $lines.Add("| Field | Value |")
    $lines.Add("| --- | --- |")
    Add-FieldLine -Lines $lines -Label "Concurrent Users" -Value (Get-JsonValue -Object $load -Name "concurrentUsers")
    Add-FieldLine -Lines $lines -Label "Iterations" -Value (Get-JsonValue -Object $load -Name "iterations")
    Add-FieldLine -Lines $lines -Label "Include Submit" -Value (Get-JsonValue -Object $load -Name "includeSubmit")
    Add-FieldLine -Lines $lines -Label "Total Success" -Value (Get-JsonValue -Object $load -Name "totalSuccess")
    Add-FieldLine -Lines $lines -Label "Total Failed" -Value (Get-JsonValue -Object $load -Name "totalFailed")
    Add-FieldLine -Lines $lines -Label "Average Request Latency Ms" -Value (Get-JsonValue -Object $load -Name "averageRequestLatencyMs")
    Add-FieldLine -Lines $lines -Label "P95 Request Latency Ms" -Value (Get-JsonValue -Object $load -Name "p95RequestLatencyMs")
    Add-FieldLine -Lines $lines -Label "P99 Request Latency Ms" -Value (Get-JsonValue -Object $load -Name "p99RequestLatencyMs")
    Add-FieldLine -Lines $lines -Label "Max Request Latency Ms" -Value (Get-JsonValue -Object $load -Name "maxRequestLatencyMs")
    Add-FieldLine -Lines $lines -Label "Average Worker Latency Ms" -Value (Get-JsonValue -Object $load -Name "averageWorkerLatencyMs")
    $thresholdViolations = @(Get-JsonValue -Object $load -Name "thresholdViolations")
    if ($thresholdViolations.Count -gt 0) {
        $lines.Add("")
        $lines.Add("| Threshold Scope | Operation | Metric | Actual Ms | Threshold Ms |")
        $lines.Add("| --- | --- | --- | ---: | ---: |")
        foreach ($item in @($thresholdViolations | Sort-Object scope,operation,metric)) {
            $scope = Value-OrDash (Get-JsonValue -Object $item -Name "scope")
            $operation = Value-OrDash (Get-JsonValue -Object $item -Name "operation")
            $metric = Value-OrDash (Get-JsonValue -Object $item -Name "metric")
            $actualMs = Value-OrDash (Get-JsonValue -Object $item -Name "actualMs")
            $thresholdMs = Value-OrDash (Get-JsonValue -Object $item -Name "thresholdMs")
            $lines.Add("| $scope | $operation | $metric | $actualMs | $thresholdMs |")
        }
    }
    $operationMetrics = @(Get-JsonValue -Object $load -Name "operationMetrics")
    if ($operationMetrics.Count -gt 0) {
        $lines.Add("")
        $lines.Add("| Operation | Total | Failed | Avg Ms | P95 Ms | P99 Ms | Max Ms |")
        $lines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
        foreach ($item in @($operationMetrics | Sort-Object operation)) {
            $operation = Value-OrDash (Get-JsonValue -Object $item -Name "operation")
            $total = Value-OrDash (Get-JsonValue -Object $item -Name "total")
            $failed = Value-OrDash (Get-JsonValue -Object $item -Name "failed")
            $averageMs = Value-OrDash (Get-JsonValue -Object $item -Name "averageMs")
            $p95Ms = Value-OrDash (Get-JsonValue -Object $item -Name "p95Ms")
            $p99Ms = Value-OrDash (Get-JsonValue -Object $item -Name "p99Ms")
            $maxMs = Value-OrDash (Get-JsonValue -Object $item -Name "maxMs")
            $lines.Add("| $operation | $total | $failed | $averageMs | $p95Ms | $p99Ms | $maxMs |")
        }
    }
    $sampleErrors = @(Get-JsonValue -Object $load -Name "sampleErrors")
    if ($sampleErrors.Count -gt 0) {
        $lines.Add("")
        $lines.Add("Sample errors:")
        foreach ($errorText in @($sampleErrors | Select-Object -First 5)) {
            $redacted = Redact-Secret $errorText
            $lines.Add("- ``$redacted``")
        }
    }
} else {
    $lines.Add("Load result file is missing or unreadable. This is expected when submit replay mode skips load smoke.")
}

Add-Section -Lines $lines -Title "Exam Metrics"
if ($null -ne $metrics) {
    $metricsFailureReason = Get-JsonValue -Object $metrics -Name "failureReason"
    if ($metricsFailureReason) {
        $lines.Add("Failure reason: ``$(Redact-Secret $metricsFailureReason)``")
        $lines.Add("")
    }
    $missingOperations = @(Get-JsonValue -Object $metrics -Name "missingOperations")
    $missingOperationOutcomes = @(Get-JsonValue -Object $metrics -Name "missingOperationOutcomes")
    $invalidRequiredOperationOutcomes = @(Get-JsonValue -Object $metrics -Name "invalidRequiredOperationOutcomes")
    if ($missingOperations.Count -gt 0 -or $missingOperationOutcomes.Count -gt 0 -or $invalidRequiredOperationOutcomes.Count -gt 0) {
        $lines.Add("| Missing Metrics Requirement | Values |")
        $lines.Add("| --- | --- |")
        if ($missingOperations.Count -gt 0) {
            $lines.Add("| Operations | ``$($missingOperations -join ', ')`` |")
        }
        if ($missingOperationOutcomes.Count -gt 0) {
            $lines.Add("| Operation outcomes | ``$($missingOperationOutcomes -join ', ')`` |")
        }
        if ($invalidRequiredOperationOutcomes.Count -gt 0) {
            $lines.Add("| Invalid outcome requirements | ``$($invalidRequiredOperationOutcomes -join ', ')`` |")
        }
        $lines.Add("")
    }
    $observedOperations = @(Get-JsonValue -Object $metrics -Name "observedOperations")
    if ($observedOperations.Count -gt 0) {
        $lines.Add("| Operation | Total |")
        $lines.Add("| --- | ---: |")
        foreach ($item in @($observedOperations | Sort-Object operation)) {
            $operation = Value-OrDash (Get-JsonValue -Object $item -Name "operation")
            $total = Value-OrDash (Get-JsonValue -Object $item -Name "total")
            $lines.Add("| $operation | $total |")
        }
    } else {
        $lines.Add("No observed operations were reported.")
    }

    $observedOperationOutcomes = @(Get-JsonValue -Object $metrics -Name "observedOperationOutcomes")
    if ($observedOperationOutcomes.Count -gt 0) {
        $lines.Add("")
        $lines.Add("| Operation | Outcome | Total |")
        $lines.Add("| --- | --- | ---: |")
        foreach ($item in @($observedOperationOutcomes | Sort-Object operation,outcome)) {
            $operation = Value-OrDash (Get-JsonValue -Object $item -Name "operation")
            $outcome = Value-OrDash (Get-JsonValue -Object $item -Name "outcome")
            $total = Value-OrDash (Get-JsonValue -Object $item -Name "total")
            $lines.Add("| $operation | $outcome | $total |")
        }
    }
} else {
    $lines.Add("Metrics result file is missing or unreadable: $MetricsResultFile.")
}

$markdown = ($lines -join [Environment]::NewLine) + [Environment]::NewLine

function Write-MarkdownTarget {
    param(
        [string]$Target,
        [bool]$Append
    )
    $targetPath = if ([System.IO.Path]::IsPathRooted($target)) {
        $Target
    } else {
        Join-Path (Get-Location) $Target
    }
    $targetDir = Split-Path -Parent $targetPath
    if ($targetDir) {
        New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    }
    if ($Append) {
        Add-Content -LiteralPath $targetPath -Value $markdown -Encoding UTF8
    } else {
        Set-Content -LiteralPath $targetPath -Value $markdown -Encoding UTF8
    }
    Write-Step "Wrote acceptance summary to $targetPath"
}

$wroteTarget = $false
if ($OutputFile) {
    Write-MarkdownTarget -Target $OutputFile -Append $false
    $wroteTarget = $true
}

if ($env:GITHUB_STEP_SUMMARY -and ((-not $OutputFile) -or $AlsoWriteStepSummary)) {
    Write-MarkdownTarget -Target $env:GITHUB_STEP_SUMMARY -Append $true
    $wroteTarget = $true
}

if (-not $wroteTarget) {
    Write-Host $markdown
} else {
    Write-Step "Acceptance summary completed"
}
