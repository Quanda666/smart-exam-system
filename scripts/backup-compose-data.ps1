param(
    [string]$EnvFile = ".env",
    [string]$BackupDir = "",
    [switch]$SkipMysql,
    [switch]$IncludeRedis,
    [int]$RetentionDays = -1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[backup] $Message"
}

function Fail {
    param([string]$Message)
    throw "Backup failed: $Message"
}

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
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
        $values[$pair[0].Trim()] = $pair[1].Trim().Trim('"').Trim("'")
    }
    return $values
}

function Get-ValueOrDefault {
    param([hashtable]$Values, [string]$Key, [string]$DefaultValue)
    if ($Values.ContainsKey($Key) -and "$($Values[$Key])" -ne "") {
        return "$($Values[$Key])"
    }
    return $DefaultValue
}

function Join-Arguments {
    param([string[]]$Arguments)
    $parts = @()
    foreach ($arg in $Arguments) {
        if ($null -eq $arg) {
            continue
        }
        if ($arg -match '[\s"]') {
            $escaped = $arg -replace '\\', '\\'
            $escaped = $escaped -replace '"', '\"'
            $parts += '"' + $escaped + '"'
        } else {
            $parts += $arg
        }
    }
    return ($parts -join " ")
}

function Invoke-Native {
    param(
        [string]$FileName,
        [string[]]$Arguments,
        [string]$OutputFile = ""
    )
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FileName
    $psi.Arguments = Join-Arguments -Arguments $Arguments
    $psi.UseShellExecute = $false
    $psi.RedirectStandardError = $true
    if ($OutputFile) {
        $psi.RedirectStandardOutput = $true
    }
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    if (-not $process.Start()) {
        Fail "could not start $FileName"
    }
    if ($OutputFile) {
        $stream = [System.IO.File]::Open($OutputFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
        try {
            $process.StandardOutput.BaseStream.CopyTo($stream)
        } finally {
            $stream.Dispose()
        }
    }
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        Fail "$FileName $($psi.Arguments) exited with $($process.ExitCode): $stderr"
    }
    return $stderr
}

function Test-ContainerRunning {
    param([string]$Name)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $result = & docker inspect -f "{{.State.Running}}" $Name 2>$null
        $exit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    return $exit -eq 0 -and "$result".Trim() -eq "true"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail "Docker CLI is required"
}

$envPath = if (Test-Path -LiteralPath $EnvFile) { (Resolve-Path -LiteralPath $EnvFile).Path } else { ".env.example" }
$envValues = Read-DotEnv -Path $envPath

$mysqlDatabase = Get-ValueOrDefault -Values $envValues -Key "MYSQL_DATABASE" -DefaultValue "smart_exam_system"
$mysqlUser = Get-ValueOrDefault -Values $envValues -Key "MYSQL_USER" -DefaultValue (Get-ValueOrDefault -Values $envValues -Key "MYSQL_USERNAME" -DefaultValue "smartexam")
$mysqlPassword = Get-ValueOrDefault -Values $envValues -Key "MYSQL_PASSWORD" -DefaultValue "smartexam123"
$redisPassword = Get-ValueOrDefault -Values $envValues -Key "REDIS_PASSWORD" -DefaultValue ""

if (-not $BackupDir) {
    $BackupDir = Get-ValueOrDefault -Values $envValues -Key "BACKUP_DIR" -DefaultValue "backups"
}
if ($RetentionDays -lt 0) {
    $RetentionDays = [int](Get-ValueOrDefault -Values $envValues -Key "BACKUP_RETENTION_DAYS" -DefaultValue "14")
}

if (-not $SkipMysql -and -not (Test-ContainerRunning -Name "smart-exam-mysql")) {
    Fail "smart-exam-mysql is not running"
}
if ($IncludeRedis -and -not (Test-ContainerRunning -Name "smart-exam-redis")) {
    Fail "smart-exam-redis is not running"
}

$backupRootBase = if ([System.IO.Path]::IsPathRooted($BackupDir)) { $BackupDir } else { Join-Path (Get-Location) $BackupDir }
New-Item -ItemType Directory -Force -Path $backupRootBase | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $backupRootBase "smart-exam-$timestamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

$manifest = [ordered]@{
    createdAt = (Get-Date).ToString("o")
    envFile = $envPath
    mysql = $null
    redis = $null
}

if (-not $SkipMysql) {
    $mysqlFile = Join-Path $backupRoot "$mysqlDatabase.sql"
    Write-Step "Dumping MySQL database '$mysqlDatabase' to $mysqlFile"
    Invoke-Native -FileName "docker" -Arguments @(
        "exec",
        "-e", "MYSQL_PWD=$mysqlPassword",
        "smart-exam-mysql",
        "mysqldump",
        "-u", $mysqlUser,
        "--single-transaction",
        "--quick",
        "--routines",
        "--triggers",
        "--events",
        "--default-character-set=utf8mb4",
        $mysqlDatabase
    ) -OutputFile $mysqlFile | Out-Null
    $manifest.mysql = [ordered]@{
        database = $mysqlDatabase
        user = $mysqlUser
        file = (Split-Path -Leaf $mysqlFile)
        bytes = (Get-Item -LiteralPath $mysqlFile).Length
    }
}

if ($IncludeRedis) {
    Write-Step "Forcing Redis SAVE before copying /data"
    $redisArgs = @("exec", "smart-exam-redis", "redis-cli")
    if ($redisPassword) {
        $redisArgs += @("-a", $redisPassword)
    }
    $redisArgs += "SAVE"
    Invoke-Native -FileName "docker" -Arguments $redisArgs | Out-Null
    $redisDir = Join-Path $backupRoot "redis-data"
    New-Item -ItemType Directory -Force -Path $redisDir | Out-Null
    Write-Step "Copying Redis data directory to $redisDir"
    Invoke-Native -FileName "docker" -Arguments @("cp", "smart-exam-redis:/data/.", $redisDir) | Out-Null
    $manifest.redis = [ordered]@{
        directory = "redis-data"
        copiedAfterSave = $true
    }
}

$manifestPath = Join-Path $backupRoot "manifest.json"
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

if ($RetentionDays -gt 0) {
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    Get-ChildItem -LiteralPath $backupRootBase -Directory -Filter "smart-exam-*" |
        Where-Object { $_.CreationTime -lt $cutoff } |
        ForEach-Object {
            Write-Step "Retention candidate older than $RetentionDays days: $($_.FullName)"
        }
}

Write-Step "PASS backup created at $backupRoot"
