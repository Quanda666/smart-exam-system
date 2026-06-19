param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long[]]$AttemptIds = @(),
    [string[]]$StudentTokens = @(),
    [string]$Username = "",
    [string]$Password = "",
    [int]$ConcurrentUsers = 20,
    [int]$Iterations = 5,
    [int]$ThinkTimeMs = 200,
    [string]$ResultFile = "",
    [int]$MaxAverageRequestLatencyMs = 0,
    [int]$MaxP95RequestLatencyMs = 0,
    [int]$MaxP99RequestLatencyMs = 0,
    [string]$MaxOperationP95Ms = "",
    [string]$MaxOperationP99Ms = "",
    [string]$MaxOperationMaxMs = "",
    [switch]$IncludeSubmit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[load-smoke] $Message"
}

function Fail {
    param([string]$Message)
    throw "Load smoke failed: $Message"
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
    Write-Step "Wrote load smoke result to $resultPath"
}

function Convert-ThresholdMap {
    param([string]$Spec)
    $map = @{}
    if (-not $Spec) {
        return $map
    }
    $entries = $Spec -split "[,;]" | Where-Object { $_ -and $_.Trim() }
    foreach ($entry in $entries) {
        $parts = $entry.Split("=", 2)
        if ($parts.Count -ne 2 -or -not $parts[0].Trim() -or -not $parts[1].Trim()) {
            Fail "Invalid operation threshold entry '$entry'. Use operation=milliseconds"
        }
        $operation = $parts[0].Trim()
        $threshold = 0
        if (-not [int]::TryParse($parts[1].Trim(), [ref]$threshold) -or $threshold -le 0) {
            Fail "Invalid threshold value for operation '$operation': $($parts[1])"
        }
        $map[$operation] = $threshold
    }
    return $map
}

if ($AttemptIds.Count -eq 0) {
    Fail "Provide at least one attempt id. Use the resilience fixture/discovery scripts to prepare disposable attempts."
}
if ($StudentTokens.Count -eq 0 -and (-not $Username -or -not $Password)) {
    Fail "Provide StudentTokens, or Username and Password for login in each worker."
}
if ($ConcurrentUsers -lt 1 -or $Iterations -lt 1) {
    Fail "ConcurrentUsers and Iterations must be positive"
}
if ($MaxAverageRequestLatencyMs -lt 0 -or $MaxP95RequestLatencyMs -lt 0 -or $MaxP99RequestLatencyMs -lt 0) {
    Fail "Latency thresholds must be zero or positive"
}
if ($IncludeSubmit) {
    Write-Step "IncludeSubmit is enabled. Use only disposable attempts because submit is destructive."
}

$operationP95Thresholds = Convert-ThresholdMap -Spec $MaxOperationP95Ms
$operationP99Thresholds = Convert-ThresholdMap -Spec $MaxOperationP99Ms
$operationMaxThresholds = Convert-ThresholdMap -Spec $MaxOperationMaxMs

$worker = {
    param(
        [string]$BaseUrl,
        [long[]]$AttemptIds,
        [string[]]$StudentTokens,
        [string]$Username,
        [string]$Password,
        [int]$WorkerIndex,
        [int]$Iterations,
        [int]$ThinkTimeMs,
        [bool]$IncludeSubmit
    )

    $ErrorActionPreference = "Stop"
    $script:success = 0
    $script:failed = 0
    $script:totalMs = 0L
    $script:latenciesMs = New-Object System.Collections.Generic.List[long]
    $script:requests = New-Object System.Collections.Generic.List[object]
    $script:errors = New-Object System.Collections.Generic.List[string]

    function Get-OperationName {
        param([string]$Path)
        if ($Path -eq "/api/auth/login") {
            return "login"
        }
        if ($Path -match "/api/exams/attempt/\d+/start$") {
            return "startExam"
        }
        if ($Path -match "/api/exams/attempt/\d+/save$") {
            return "saveDraft"
        }
        if ($Path -match "/api/exams/attempt/\d+/heartbeat$") {
            return "attemptHeartbeat"
        }
        if ($Path -match "/api/exams/attempt/\d+/submit$") {
            return "submitExam"
        }
        return $Path
    }

    function Add-RequestSample {
        param(
            [string]$Method,
            [string]$Path,
            [long]$ElapsedMs,
            [bool]$Succeeded
        )
        $script:requests.Add([pscustomobject]@{
            operation = Get-OperationName -Path $Path
            method = $Method
            path = $Path
            elapsedMs = $ElapsedMs
            success = $Succeeded
        })
    }

    function Invoke-Api {
        param([string]$Method, [string]$Path, [string]$Token, $Body = $null)
        $uri = $BaseUrl.TrimEnd("/") + $Path
        $headers = @{
            "X-Request-Id" = "load-smoke-$WorkerIndex-$([guid]::NewGuid().ToString("N"))"
        }
        if ($Token) {
            $headers["Authorization"] = "Bearer $Token"
        }
        $params = @{
            Method = $Method
            Uri = $uri
            Headers = $headers
            ContentType = "application/json"
            TimeoutSec = 30
        }
        if ($null -ne $Body) {
            $params["Body"] = ($Body | ConvertTo-Json -Depth 30 -Compress)
        }
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $response = Invoke-RestMethod @params
            $watch.Stop()
            $elapsedMs = $watch.ElapsedMilliseconds
            $script:totalMs += $elapsedMs
            $script:latenciesMs.Add($elapsedMs)
            if ($response.PSObject.Properties.Name -contains "success" -and -not [bool]$response.success) {
                $script:failed += 1
                Add-RequestSample -Method $Method -Path $Path -ElapsedMs $elapsedMs -Succeeded $false
                if ($script:errors.Count -lt 5) {
                    $script:errors.Add("$Method $Path -> success=false $($response.message)")
                }
                return $null
            }
            Add-RequestSample -Method $Method -Path $Path -ElapsedMs $elapsedMs -Succeeded $true
            $script:success += 1
            return $response
        } catch {
            $watch.Stop()
            $elapsedMs = $watch.ElapsedMilliseconds
            $script:totalMs += $elapsedMs
            $script:latenciesMs.Add($elapsedMs)
            Add-RequestSample -Method $Method -Path $Path -ElapsedMs $elapsedMs -Succeeded $false
            $script:failed += 1
            $message = $_.Exception.Message
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
                $message = $_.ErrorDetails.Message
            }
            if ($script:errors.Count -lt 5) {
                $script:errors.Add("$Method $Path -> $message")
            }
            return $null
        }
    }

    $token = ""
    if ($StudentTokens.Count -gt 0) {
        $token = $StudentTokens[$WorkerIndex % $StudentTokens.Count]
    } else {
        $login = Invoke-Api -Method "POST" -Path "/api/auth/login" -Token "" -Body @{
            username = $Username
            password = $Password
        }
        if ($login -and $login.data -and $login.data.token) {
            $token = "$($login.data.token)"
        }
    }

    $attemptId = $AttemptIds[$WorkerIndex % $AttemptIds.Count]
    if (-not $token) {
        $errors = @($script:errors.ToArray())
        $errors += "no token available"
        return [pscustomobject]@{
            worker = $WorkerIndex
            success = $script:success
            failed = $script:failed + 1
            averageMs = 0
            latenciesMs = $script:latenciesMs.ToArray()
            requests = $script:requests.ToArray()
            errors = $errors
        }
    }

    Invoke-Api -Method "POST" -Path "/api/exams/attempt/$attemptId/start" -Token $token | Out-Null

    for ($i = 1; $i -le $Iterations; $i++) {
        $revision = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + $WorkerIndex + $i
        $answers = @{
            loadSmoke = "worker-$WorkerIndex-iteration-$i"
        } | ConvertTo-Json -Compress
        Invoke-Api -Method "POST" -Path "/api/exams/attempt/$attemptId/save" -Token $token -Body @{
            answers = $answers
            clientDraftId = "load-smoke-$WorkerIndex"
            revision = $revision
        } | Out-Null
        Invoke-Api -Method "POST" -Path "/api/exams/attempt/$attemptId/heartbeat" -Token $token | Out-Null
        if ($ThinkTimeMs -gt 0) {
            Start-Sleep -Milliseconds $ThinkTimeMs
        }
    }

    if ($IncludeSubmit) {
        Invoke-Api -Method "POST" -Path "/api/exams/attempt/$attemptId/submit" -Token $token -Body @{
            answers = @{}
            submitToken = "load-smoke-submit-$WorkerIndex-$([guid]::NewGuid().ToString("N"))"
        } | Out-Null
    }

    $average = if (($script:success + $script:failed) -gt 0) { [math]::Round($script:totalMs / ($script:success + $script:failed), 2) } else { 0 }
    return [pscustomobject]@{
        worker = $WorkerIndex
        success = $script:success
        failed = $script:failed
        averageMs = $average
        latenciesMs = $script:latenciesMs.ToArray()
        requests = $script:requests.ToArray()
        errors = $script:errors.ToArray()
    }
}

function Get-Percentile {
    param(
        [long[]]$Values,
        [double]$Percentile
    )
    if ($Values.Count -eq 0) {
        return 0
    }
    $sorted = @($Values | Sort-Object)
    $rank = [math]::Ceiling(($Percentile / 100) * $sorted.Count)
    $index = [math]::Max(0, [math]::Min($sorted.Count - 1, $rank - 1))
    return [long]$sorted[$index]
}

function Get-OperationMetrics {
    param([object[]]$Requests)
    if ($Requests.Count -eq 0) {
        return @()
    }
    $groups = $Requests | Group-Object -Property operation
    $metrics = foreach ($group in $groups) {
        $items = @($group.Group)
        $latencies = @($items | ForEach-Object { [long]$_.elapsedMs })
        $successCount = @($items | Where-Object { [bool]$_.success }).Count
        $failedCount = $items.Count - $successCount
        $averageMs = if ($latencies.Count -gt 0) {
            [math]::Round((($latencies | Measure-Object -Average).Average), 2)
        } else {
            0
        }
        $maxMs = if ($latencies.Count -gt 0) {
            [long](($latencies | Measure-Object -Maximum).Maximum)
        } else {
            0
        }
        [pscustomobject]@{
            operation = $group.Name
            total = $items.Count
            success = $successCount
            failed = $failedCount
            averageMs = $averageMs
            p95Ms = Get-Percentile -Values $latencies -Percentile 95
            p99Ms = Get-Percentile -Values $latencies -Percentile 99
            maxMs = $maxMs
        }
    }
    return @($metrics | Sort-Object operation)
}

function Add-ThresholdViolation {
    param(
        [System.Collections.Generic.List[object]]$Violations,
        [string]$Scope,
        [string]$Operation,
        [string]$Metric,
        [double]$ActualMs,
        [int]$ThresholdMs
    )
    if ($ThresholdMs -le 0) {
        return
    }
    if ($ActualMs -gt $ThresholdMs) {
        $Violations.Add([pscustomobject]@{
            scope = $Scope
            operation = $Operation
            metric = $Metric
            actualMs = $ActualMs
            thresholdMs = $ThresholdMs
        })
    }
}

Write-Step "Starting $ConcurrentUsers workers, iterations=$Iterations, attempts=$($AttemptIds -join ',')"
$jobs = for ($i = 0; $i -lt $ConcurrentUsers; $i++) {
    Start-Job -ScriptBlock $worker -ArgumentList @(
        $BaseUrl,
        $AttemptIds,
        $StudentTokens,
        $Username,
        $Password,
        $i,
        $Iterations,
        $ThinkTimeMs,
        [bool]$IncludeSubmit
    )
}

Wait-Job -Job $jobs | Out-Null
$results = @($jobs | Receive-Job)
$jobs | Remove-Job

$totalSuccess = ($results | Measure-Object -Property success -Sum).Sum
$totalFailed = ($results | Measure-Object -Property failed -Sum).Sum
$avgMs = if ($results.Count -gt 0) { [math]::Round((($results | Measure-Object -Property averageMs -Average).Average), 2) } else { 0 }
$allLatencies = @($results | ForEach-Object { $_.latenciesMs } | Where-Object { $null -ne $_ })
$requestAvgMs = if ($allLatencies.Count -gt 0) { [math]::Round((($allLatencies | Measure-Object -Average).Average), 2) } else { 0 }
$requestP95Ms = Get-Percentile -Values $allLatencies -Percentile 95
$requestP99Ms = Get-Percentile -Values $allLatencies -Percentile 99
$requestMaxMs = if ($allLatencies.Count -gt 0) { [long](($allLatencies | Measure-Object -Maximum).Maximum) } else { 0 }
$requestSamples = @($results | ForEach-Object { $_.requests } | Where-Object { $null -ne $_ })
$operationMetrics = @(Get-OperationMetrics -Requests $requestSamples)
$thresholdViolations = New-Object System.Collections.Generic.List[object]
Add-ThresholdViolation -Violations $thresholdViolations -Scope "global" -Operation "all" -Metric "averageRequestLatencyMs" -ActualMs $requestAvgMs -ThresholdMs $MaxAverageRequestLatencyMs
Add-ThresholdViolation -Violations $thresholdViolations -Scope "global" -Operation "all" -Metric "p95RequestLatencyMs" -ActualMs $requestP95Ms -ThresholdMs $MaxP95RequestLatencyMs
Add-ThresholdViolation -Violations $thresholdViolations -Scope "global" -Operation "all" -Metric "p99RequestLatencyMs" -ActualMs $requestP99Ms -ThresholdMs $MaxP99RequestLatencyMs
foreach ($metric in $operationMetrics) {
    $operation = "$($metric.operation)"
    if ($operationP95Thresholds.ContainsKey($operation)) {
        Add-ThresholdViolation -Violations $thresholdViolations -Scope "operation" -Operation $operation -Metric "p95Ms" -ActualMs $metric.p95Ms -ThresholdMs $operationP95Thresholds[$operation]
    }
    if ($operationP99Thresholds.ContainsKey($operation)) {
        Add-ThresholdViolation -Violations $thresholdViolations -Scope "operation" -Operation $operation -Metric "p99Ms" -ActualMs $metric.p99Ms -ThresholdMs $operationP99Thresholds[$operation]
    }
    if ($operationMaxThresholds.ContainsKey($operation)) {
        Add-ThresholdViolation -Violations $thresholdViolations -Scope "operation" -Operation $operation -Metric "maxMs" -ActualMs $metric.maxMs -ThresholdMs $operationMaxThresholds[$operation]
    }
}

Write-Step "Requests success=$totalSuccess failed=$totalFailed averageRequestLatencyMs=$requestAvgMs p95RequestLatencyMs=$requestP95Ms p99RequestLatencyMs=$requestP99Ms"
if ($operationMetrics.Count -gt 0) {
    Write-Step "Operation latency breakdown:"
    foreach ($metric in $operationMetrics) {
        Write-Host ("  {0}: total={1} failed={2} avgMs={3} p95Ms={4} p99Ms={5}" -f $metric.operation, $metric.total, $metric.failed, $metric.averageMs, $metric.p95Ms, $metric.p99Ms)
    }
}
if ($thresholdViolations.Count -gt 0) {
    Write-Step "Latency threshold violations:"
    foreach ($violation in $thresholdViolations) {
        Write-Host ("  {0}/{1} {2}: actualMs={3} thresholdMs={4}" -f $violation.scope, $violation.operation, $violation.metric, $violation.actualMs, $violation.thresholdMs)
    }
}
$sampleErrors = @($results | ForEach-Object { $_.errors } | Where-Object { $_ } | Select-Object -First 10)
if ($sampleErrors.Count -gt 0) {
    Write-Step "Sample errors:"
    $sampleErrors | ForEach-Object { Write-Host "  $_" }
}

$thresholdViolationArray = @($thresholdViolations.ToArray())
$result = [ordered]@{
    success = ($totalFailed -eq 0 -and $thresholdViolationArray.Count -eq 0)
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $BaseUrl
    attemptIds = $AttemptIds
    concurrentUsers = $ConcurrentUsers
    iterations = $Iterations
    thinkTimeMs = $ThinkTimeMs
    includeSubmit = [bool]$IncludeSubmit
    totalSuccess = [int]$totalSuccess
    totalFailed = [int]$totalFailed
    averageWorkerLatencyMs = $avgMs
    averageRequestLatencyMs = $requestAvgMs
    p95RequestLatencyMs = $requestP95Ms
    p99RequestLatencyMs = $requestP99Ms
    maxRequestLatencyMs = $requestMaxMs
    thresholds = [ordered]@{
        maxAverageRequestLatencyMs = $MaxAverageRequestLatencyMs
        maxP95RequestLatencyMs = $MaxP95RequestLatencyMs
        maxP99RequestLatencyMs = $MaxP99RequestLatencyMs
        maxOperationP95Ms = $MaxOperationP95Ms
        maxOperationP99Ms = $MaxOperationP99Ms
        maxOperationMaxMs = $MaxOperationMaxMs
    }
    thresholdViolations = $thresholdViolationArray
    operationMetrics = $operationMetrics
    sampleErrors = $sampleErrors
    workers = $results
}
Write-JsonResult -Result $result

if ($totalFailed -gt 0) {
    Fail "load smoke completed with $totalFailed failed requests"
}
if ($thresholdViolationArray.Count -gt 0) {
    Fail "load smoke completed with $($thresholdViolationArray.Count) latency threshold violations"
}

Write-Step "PASS load smoke completed"
