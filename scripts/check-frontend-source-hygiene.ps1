param(
    [string]$SourceRoot = "frontend/src",
    [string[]]$AdditionalFiles = @(
        "frontend/index.html",
        "frontend/package.json",
        "frontend/tsconfig.json",
        "frontend/tsconfig.node.json",
        "frontend/vite.config.ts"
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[frontend-hygiene] $Message"
}

function Fail {
    param([string]$Message)
    throw "Frontend source hygiene failed: $Message"
}

if (-not (Test-Path -LiteralPath $SourceRoot)) {
    Fail "source root does not exist: $SourceRoot"
}

$allowedExtensions = @(".vue", ".ts", ".js", ".css", ".html", ".json")
$mojibakeMarkers = @(
    @{ Text = [string][char]0xFFFD; Label = "replacement character U+FFFD" },
    @{ Text = [string][char]0x9422; Label = "mojibake marker U+9422" },
    @{ Text = [string][char]0x6FB6; Label = "mojibake marker U+6FB6" },
    @{ Text = [string][char]0x7EDB; Label = "mojibake marker U+7EDB" },
    @{ Text = [string][char]0x6D93; Label = "mojibake marker U+6D93" },
    @{ Text = [string][char]0x701B; Label = "mojibake marker U+701B" },
    @{ Text = [string][char]0x5BB8; Label = "mojibake marker U+5BB8" },
    @{ Text = [string][char]0x9429; Label = "common mojibake marker U+9429" },
    @{ Text = [string][char]0x7487; Label = "common mojibake marker U+7487" },
    @{ Text = [string][char]0x9351; Label = "common mojibake marker U+9351" },
    @{ Text = [string][char]0x951B; Label = "common mojibake marker U+951B" },
    @{ Text = [string][char]0x9286; Label = "common mojibake marker U+9286" },
    @{ Text = [string][char]0x95C8; Label = "common mojibake marker U+95C8" },
    @{ Text = [string][char]0x93C8; Label = "common mojibake marker U+93C8" },
    @{ Text = [string][char]0x20AC; Label = "common mojibake marker U+20AC" }
)

$files = New-Object System.Collections.Generic.List[System.IO.FileInfo]
Get-ChildItem -LiteralPath $SourceRoot -Recurse -File |
    Where-Object { $allowedExtensions -contains $_.Extension.ToLowerInvariant() } |
    ForEach-Object { $files.Add($_) }

foreach ($path in $AdditionalFiles) {
    if (Test-Path -LiteralPath $path) {
        $file = Get-Item -LiteralPath $path
        if ($allowedExtensions -contains $file.Extension.ToLowerInvariant()) {
            $files.Add($file)
        }
    }
}

$uniqueFiles = $files |
    Sort-Object FullName -Unique

$problems = New-Object System.Collections.Generic.List[string]
foreach ($file in $uniqueFiles) {
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName -Encoding UTF8) {
        $lineNumber += 1
        foreach ($marker in $mojibakeMarkers) {
            if ($line.Contains($marker.Text)) {
                $problems.Add("$($file.FullName):$lineNumber contains suspicious $($marker.Label)")
            }
        }
        if ($line -match "^\s*(<<<<<<<|>>>>>>>)") {
            $problems.Add("$($file.FullName):$lineNumber contains a Git conflict marker")
        }
    }
}

if ($problems.Count -gt 0) {
    $problems | Select-Object -First 20 | ForEach-Object { Write-Host $_ }
    Fail "found $($problems.Count) suspicious frontend source hygiene issue(s)"
}

Write-Step "PASS checked $($uniqueFiles.Count) frontend source files"
