param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$AdminToken = "",
    [string]$AdminUsername = "",
    [string]$AdminPassword = "",
    [string]$StudentUsername = "",
    [string]$StudentPassword = "student123",
    [string]$StudentName = "",
    [int]$DurationMinutes = 120,
    [switch]$SkipSubmit,
    [switch]$OmitLastAnswer,
    [switch]$CheckForgedQuestionRejection,
    [switch]$ExpectUnreleasedStudentInsightsHidden,
    [switch]$CheckScoreReleaseVisibilityCycle,
    [switch]$CheckMonitorEventDedup,
    [switch]$CheckMonitorActionForceSubmitBinding,
    [switch]$CheckMonitorForceSubmitTransaction,
    [switch]$CheckMonitorWarnNotification,
    [switch]$ExpectWriteBack,
    [switch]$CleanupAfterRun,
    [int]$CleanupOlderThanHours = 0,
    [switch]$KeepFixtureConfigEnabled,
    [string]$ResultFile = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$VerifyScript = Join-Path $PSScriptRoot "verify-attempt-resilience.ps1"

function Write-Step {
    param([string]$Message)
    Write-Host "[acceptance] $Message"
}

function Fail {
    param([string]$Message)
    throw "Attempt resilience acceptance failed: $Message"
}

function Invoke-ExamApi {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = "",
        $Body = $null
    )
    $uri = ($BaseUrl.TrimEnd("/") + $Path)
    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $params["Body"] = ($Body | ConvertTo-Json -Depth 30)
    }
    try {
        $response = Invoke-RestMethod @params
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        throw "HTTP $Method $Path failed: $detail"
    }
    if ($null -eq $response) {
        Fail "HTTP $Method $Path returned an empty response"
    }
    if ($response.PSObject.Properties.Name -contains "success" -and -not [bool]$response.success) {
        Fail "API $Path returned success=false: $($response.message)"
    }
    return $response
}

function Login-AndGetToken {
    param([string]$Username, [string]$Password)
    $response = Invoke-ExamApi -Method "POST" -Path "/api/auth/login" -Body @{
        username = $Username
        password = $Password
    }
    if (-not $response.data.token) {
        Fail "admin login did not return a token"
    }
    return "$($response.data.token)"
}

function Get-SystemConfigValue {
    param([string]$Key)
    $response = Invoke-ExamApi -Method "GET" -Path "/api/system/configs?category=SYSTEM" -Token $AdminToken
    foreach ($item in $response.data) {
        if ("$($item.configKey)" -eq $Key) {
            return "$($item.configValue)"
        }
    }
    return $null
}

function Set-SystemConfigValue {
    param([string]$Key, [string]$Value)
    Invoke-ExamApi -Method "PUT" -Path "/api/system/configs/$([uri]::EscapeDataString($Key))" -Token $AdminToken -Body @{
        configValue = $Value
    } | Out-Null
}

function New-DefaultStudentUsername {
    $stamp = Get-Date -Format "yyyyMMddHHmmss"
    $suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
    return "verify_student_${stamp}_$suffix"
}

function Resolve-PowerShellExecutable {
    $windowsPowerShell = Join-Path $PSHOME "powershell.exe"
    if (Test-Path $windowsPowerShell) {
        return $windowsPowerShell
    }
    $pwsh = Get-Command pwsh -ErrorAction SilentlyContinue
    if ($pwsh) {
        return $pwsh.Source
    }
    $powershell = Get-Command powershell -ErrorAction SilentlyContinue
    if ($powershell) {
        return $powershell.Source
    }
    return "powershell"
}

function Invoke-VerificationScript {
    param([long]$AttemptId, [long]$ExamId, [string]$Username, [string]$Password)
    if (-not (Test-Path $VerifyScript)) {
        Fail "verify script not found: $VerifyScript"
    }
    $args = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $VerifyScript,
        "-BaseUrl", $BaseUrl,
        "-AttemptId", "$AttemptId",
        "-ExamId", "$ExamId",
        "-Username", $Username,
        "-Password", $Password,
        "-AdminToken", $AdminToken
    )
    if (-not $SkipSubmit -and -not $CheckMonitorForceSubmitTransaction) {
        $args += "-Submit"
    }
    if ($ExpectWriteBack) {
        $args += "-ExpectWriteBack"
    }
    if ($OmitLastAnswer) {
        $args += "-OmitLastAnswer"
    }
    if ($CheckForgedQuestionRejection) {
        $args += "-CheckForgedQuestionRejection"
    }
    if ($ExpectUnreleasedStudentInsightsHidden) {
        $args += "-ExpectUnreleasedStudentInsightsHidden"
    }
    if ($CheckScoreReleaseVisibilityCycle) {
        $args += "-CheckScoreReleaseVisibilityCycle"
    }
    if ($CheckMonitorEventDedup) {
        $args += "-CheckMonitorEventDedup"
    }
    if ($CheckMonitorActionForceSubmitBinding) {
        $args += "-CheckMonitorActionForceSubmitBinding"
    }
    if ($CheckMonitorForceSubmitTransaction) {
        $args += "-CheckMonitorForceSubmitTransaction"
    }
    if ($CheckMonitorWarnNotification) {
        $args += "-CheckMonitorWarnNotification"
    }
    $mode = "submit replay"
    if ($CheckMonitorForceSubmitTransaction) {
        $mode = "monitor force-submit transaction"
    } elseif ($SkipSubmit) {
        $mode = "non-destructive"
    }
    Write-Step "Running $mode verification for attempt $AttemptId"
    $powershell = Resolve-PowerShellExecutable
    & $powershell @args
    if ($LASTEXITCODE -ne 0) {
        Fail "verify-attempt-resilience.ps1 exited with code $LASTEXITCODE"
    }
}

function Invoke-FixtureCleanup {
    param([string]$Username)
    $prefix = $Username
    if ($Username -match "^(verify_student_[0-9]{14}_[0-9a-f]{8}).*$") {
        $prefix = $Matches[1]
    }
    $query = "olderThanHours=$CleanupOlderThanHours&studentPrefix=$([uri]::EscapeDataString($prefix))&dryRun=false"
    Write-Step "Cleaning disposable fixture data for student prefix $prefix"
    Invoke-ExamApi -Method "DELETE" -Path "/api/exams/attempt-resilience/fixtures?$query" -Token $AdminToken | Out-Null
}

if (-not $AdminToken) {
    if (-not $AdminUsername -or -not $AdminPassword) {
        Fail "Provide AdminToken or AdminUsername/AdminPassword"
    }
    Write-Step "Logging in as admin $AdminUsername"
    $AdminToken = Login-AndGetToken -Username $AdminUsername -Password $AdminPassword
}

if (-not $StudentUsername) {
    $StudentUsername = New-DefaultStudentUsername
}
if (-not $StudentName) {
    $StudentName = "Resilience Test $StudentUsername"
}

$originalFixtureConfig = $null
$fixtureConfigChanged = $false
$fixture = $null

try {
    Write-Step "Checking system.testFixtureEnabled"
    $originalFixtureConfig = Get-SystemConfigValue -Key "system.testFixtureEnabled"
    if ($null -eq $originalFixtureConfig) {
        Fail "system.testFixtureEnabled config was not found"
    }
    if ($originalFixtureConfig.ToLowerInvariant() -ne "true") {
        Write-Step "Temporarily enabling system.testFixtureEnabled"
        Set-SystemConfigValue -Key "system.testFixtureEnabled" -Value "true"
        $fixtureConfigChanged = $true
    }

    Write-Step "Preparing disposable fixture for $StudentUsername"
    $fixtureResponse = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt-resilience/fixture" -Token $AdminToken -Body @{
        studentUsername = $StudentUsername
        studentPassword = $StudentPassword
        studentName = $StudentName
        durationMinutes = $DurationMinutes
    }
    $fixture = $fixtureResponse.data
    if (-not $fixture.attemptId) {
        Fail "fixture endpoint did not return attemptId"
    }

    Invoke-VerificationScript -AttemptId ([long]$fixture.attemptId) -ExamId ([long]$fixture.examId) -Username "$($fixture.studentUsername)" -Password "$($fixture.studentPassword)"

    if ($CleanupAfterRun) {
        Invoke-FixtureCleanup -Username "$($fixture.studentUsername)"
    }

    Write-Step "PASS acceptance pipeline completed"
    $resultMode = "SUBMIT_REPLAY"
    if ($CheckMonitorForceSubmitTransaction) {
        $resultMode = "MONITOR_FORCE_SUBMIT"
    } elseif ($SkipSubmit) {
        $resultMode = "NON_DESTRUCTIVE"
    }
    $result = @{
        success = $true
        mode = $resultMode
        baseUrl = $BaseUrl
        studentUsername = "$($fixture.studentUsername)"
        studentPassword = "$($fixture.studentPassword)"
        examId = $fixture.examId
        attemptId = $fixture.attemptId
        cleanupAfterRun = [bool]$CleanupAfterRun
    }
    if ($ResultFile) {
        $resultPath = if ([System.IO.Path]::IsPathRooted($ResultFile)) {
            $ResultFile
        } else {
            Join-Path $RepoRoot $ResultFile
        }
        $resultDir = Split-Path -Parent $resultPath
        if ($resultDir) {
            New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
        }
        $result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resultPath -Encoding UTF8
        Write-Step "Wrote acceptance result to $resultPath"
    }
    $result | ConvertTo-Json -Depth 10
} finally {
    if ($fixtureConfigChanged -and -not $KeepFixtureConfigEnabled -and $null -ne $originalFixtureConfig) {
        Write-Step "Restoring system.testFixtureEnabled to $originalFixtureConfig"
        Set-SystemConfigValue -Key "system.testFixtureEnabled" -Value $originalFixtureConfig
    } elseif ($fixtureConfigChanged -and $KeepFixtureConfigEnabled) {
        Write-Step "Keeping system.testFixtureEnabled enabled by request"
    }
}
