param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$AdminToken = "",
    [string]$AdminUsername = "",
    [string]$AdminPassword = "",
    [string]$StudentUsername = "verify_student",
    [string]$StudentPassword = "student123",
    [string]$StudentName = "Resilience Test Student",
    [int]$DurationMinutes = 120,
    [switch]$EnableFixtureConfig
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[fixture] $Message"
}

function Fail {
    param([string]$Message)
    throw "Fixture preparation failed: $Message"
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
        $params["Body"] = ($Body | ConvertTo-Json -Depth 20)
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
    $payload = @{
        username = $Username
        password = $Password
    }
    $response = Invoke-ExamApi -Method "POST" -Path "/api/auth/login" -Body $payload
    if (-not $response.data.token) {
        Fail "Admin login did not return a token"
    }
    return "$($response.data.token)"
}

if (-not $AdminToken) {
    if (-not $AdminUsername -or -not $AdminPassword) {
        Fail "Provide AdminToken or AdminUsername/AdminPassword"
    }
    Write-Step "Logging in as admin $AdminUsername"
    $AdminToken = Login-AndGetToken -Username $AdminUsername -Password $AdminPassword
}

if ($EnableFixtureConfig) {
    Write-Step "Enabling system.testFixtureEnabled"
    Invoke-ExamApi -Method "PUT" -Path "/api/system/configs/system.testFixtureEnabled" -Token $AdminToken -Body @{
        configValue = "true"
    } | Out-Null
}

Write-Step "Preparing fixture for student $StudentUsername"
$fixture = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt-resilience/fixture" -Token $AdminToken -Body @{
    studentUsername = $StudentUsername
    studentPassword = $StudentPassword
    studentName = $StudentName
    durationMinutes = $DurationMinutes
}

$data = $fixture.data
Write-Step "Prepared attempt $($data.attemptId), exam $($data.examId), student $($data.studentUsername)"
Write-Host ""
Write-Host "Next non-destructive verification command:"
Write-Host "powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 -BaseUrl $BaseUrl -AttemptId $($data.attemptId) -Username $($data.studentUsername) -Password $($data.studentPassword) -AdminToken <admin-token>"
Write-Host ""
Write-Host "Next submit replay verification command:"
Write-Host "powershell -ExecutionPolicy Bypass -File scripts\verify-attempt-resilience.ps1 -BaseUrl $BaseUrl -AttemptId $($data.attemptId) -Username $($data.studentUsername) -Password $($data.studentPassword) -AdminToken <admin-token> -Submit"
Write-Host ""
Write-Host "One-shot fixture + verification pipeline:"
Write-Host "powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 -BaseUrl $BaseUrl -AdminToken <admin-token>"
Write-Host ""
Write-Host "Cleanup preview command:"
Write-Host "powershell -ExecutionPolicy Bypass -File scripts\cleanup-attempt-resilience-fixtures.ps1 -BaseUrl $BaseUrl -AdminToken <admin-token> -StudentPrefix $($data.studentUsername) -OlderThanHours 0"
Write-Host ""
$data | ConvertTo-Json -Depth 10
