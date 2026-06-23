param(
    [string]$SourceRoot = "backend/src/main/java",
    [string[]]$Files = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[java-hygiene] $Message"
}

function Fail {
    param([string]$Message)
    throw "Java source hygiene failed: $Message"
}

function Count-UnescapedQuotes {
    param([string]$Line)
    $count = 0
    $escaped = $false
    foreach ($char in $Line.ToCharArray()) {
        if ($escaped) {
            $escaped = $false
            continue
        }
        if ($char -eq [char]92) {
            $escaped = $true
            continue
        }
        if ($char -eq [char]34) {
            $count += 1
        }
    }
    return $count
}

if ($Files.Count -eq 0 -and -not (Test-Path -LiteralPath $SourceRoot)) {
    Fail "source root does not exist: $SourceRoot"
}

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

$problems = New-Object System.Collections.Generic.List[string]
$filesToCheck = @(if ($Files.Count -gt 0) {
    $Files | ForEach-Object { Get-Item -LiteralPath $_ }
} else {
    Get-ChildItem -LiteralPath $SourceRoot -Recurse -Filter *.java
})

foreach ($file in $filesToCheck) {
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName -Encoding UTF8) {
        $lineNumber += 1
        foreach ($marker in $mojibakeMarkers) {
            if ($line.Contains($marker.Text)) {
                $problems.Add("$($file.FullName):$lineNumber contains suspicious $($marker.Label)")
            }
        }

        if ($line.Contains('"""')) {
            continue
        }
        $quoteCount = Count-UnescapedQuotes -Line $line
        if (($quoteCount % 2) -ne 0) {
            $problems.Add("$($file.FullName):$lineNumber has an odd number of unescaped double quotes")
        }
    }
}

if ($problems.Count -gt 0) {
    $problems | Select-Object -First 20 | ForEach-Object { Write-Host $_ }
    Fail "found $($problems.Count) suspicious Java source hygiene issue(s)"
}

Write-Step "PASS checked $($filesToCheck.Count) Java files"
