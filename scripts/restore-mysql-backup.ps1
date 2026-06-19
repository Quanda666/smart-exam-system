param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$EnvFile = ".env",
    [switch]$DropExisting,
    [switch]$ConfirmRestore
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[restore-mysql] $Message"
}

function Fail {
    param([string]$Message)
    throw "MySQL restore failed: $Message"
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
        [string]$InputFile = ""
    )
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FileName
    $psi.Arguments = Join-Arguments -Arguments $Arguments
    $psi.UseShellExecute = $false
    $psi.RedirectStandardError = $true
    if ($InputFile) {
        $psi.RedirectStandardInput = $true
    }
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    if (-not $process.Start()) {
        Fail "could not start $FileName"
    }
    if ($InputFile) {
        $stream = [System.IO.File]::OpenRead($InputFile)
        try {
            $stream.CopyTo($process.StandardInput.BaseStream)
            $process.StandardInput.Close()
        } finally {
            $stream.Dispose()
        }
    }
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        Fail "$FileName $($psi.Arguments) exited with $($process.ExitCode): $stderr"
    }
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

if (-not $ConfirmRestore) {
    Fail "restore is destructive; re-run with -ConfirmRestore after verifying the target database"
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail "Docker CLI is required"
}
if (-not (Test-Path -LiteralPath $BackupFile)) {
    Fail "backup file does not exist: $BackupFile"
}
if (-not (Test-ContainerRunning -Name "smart-exam-mysql")) {
    Fail "smart-exam-mysql is not running"
}

$backupPath = (Resolve-Path -LiteralPath $BackupFile).Path
$envPath = if (Test-Path -LiteralPath $EnvFile) { (Resolve-Path -LiteralPath $EnvFile).Path } else { ".env.example" }
$envValues = Read-DotEnv -Path $envPath

$mysqlDatabase = Get-ValueOrDefault -Values $envValues -Key "MYSQL_DATABASE" -DefaultValue "smart_exam_system"
$mysqlUser = Get-ValueOrDefault -Values $envValues -Key "MYSQL_USER" -DefaultValue (Get-ValueOrDefault -Values $envValues -Key "MYSQL_USERNAME" -DefaultValue "smartexam")
$mysqlPassword = Get-ValueOrDefault -Values $envValues -Key "MYSQL_PASSWORD" -DefaultValue "smartexam123"

if ($DropExisting) {
    Write-Step "Dropping and recreating database '$mysqlDatabase'"
    Invoke-Native -FileName "docker" -Arguments @(
        "exec",
        "-e", "MYSQL_PWD=$mysqlPassword",
        "smart-exam-mysql",
        "mysql",
        "-u", $mysqlUser,
        "-e", "DROP DATABASE IF EXISTS ``$mysqlDatabase``; CREATE DATABASE ``$mysqlDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    )
}

Write-Step "Restoring $backupPath into database '$mysqlDatabase'"
Invoke-Native -FileName "docker" -Arguments @(
    "exec",
    "-i",
    "-e", "MYSQL_PWD=$mysqlPassword",
    "smart-exam-mysql",
    "mysql",
    "-u", $mysqlUser,
    "--default-character-set=utf8mb4",
    $mysqlDatabase
) -InputFile $backupPath

Write-Step "PASS MySQL restore completed"
