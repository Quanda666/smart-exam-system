param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$AdminToken = "",
    [string]$AdminUsername = "",
    [string]$AdminPassword = "",
    [int]$OlderThanHours = 24,
    [string]$StudentPrefix = "verify_student",
    [switch]$Execute,
    [switch]$KeepFixtureConfigEnabled
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[cleanup] $Message"
}

function Fail {
    param([string]$Message)
    throw "Attempt resilience fixture cleanup failed: $Message"
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

if (-not $AdminToken) {
    if (-not $AdminUsername -or -not $AdminPassword) {
        Fail "Provide AdminToken or AdminUsername/AdminPassword"
    }
    Write-Step "Logging in as admin $AdminUsername"
    $AdminToken = Login-AndGetToken -Username $AdminUsername -Password $AdminPassword
}

$originalFixtureConfig = $null
$fixtureConfigChanged = $false

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

    $dryRun = -not $Execute
    $query = "olderThanHours=$OlderThanHours&studentPrefix=$([uri]::EscapeDataString($StudentPrefix))&dryRun=$($dryRun.ToString().ToLowerInvariant())"
    if ($dryRun) {
        Write-Step "Previewing cleanup for fixtures older than $OlderThanHours hours"
    } else {
        Write-Step "Cleaning fixtures older than $OlderThanHours hours"
    }
    $response = Invoke-ExamApi -Method "DELETE" -Path "/api/exams/attempt-resilience/fixtures?$query" -Token $AdminToken
    $response.data | ConvertTo-Json -Depth 20
    if ($dryRun) {
        Write-Host ""
        Write-Host "Preview only. Re-run with -Execute to apply cleanup."
    }
} finally {
    if ($fixtureConfigChanged -and -not $KeepFixtureConfigEnabled -and $null -ne $originalFixtureConfig) {
        Write-Step "Restoring system.testFixtureEnabled to $originalFixtureConfig"
        Set-SystemConfigValue -Key "system.testFixtureEnabled" -Value $originalFixtureConfig
    } elseif ($fixtureConfigChanged -and $KeepFixtureConfigEnabled) {
        Write-Step "Keeping system.testFixtureEnabled enabled by request"
    }
}
