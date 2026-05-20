param(
    [string[]]$Paths = @("README.md", "docs", "aiops-docs", "src/main/java", "src/main/resources", "eval", "scripts")
)

$ErrorActionPreference = "Stop"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$extensions = @(".java", ".md", ".yml", ".yaml", ".js", ".html", ".css", ".json", ".jsonl", ".xml", ".properties", ".txt", ".ps1")
# Keep this script ASCII-only. Windows PowerShell 5 may parse UTF-8
# scripts without BOM as ANSI/CP936, which can break non-ASCII literals.
$mojibakeMarkerCodes = @(
    0x6D63, # U+6D63
    0x8930, # U+8930
    0x935B, # U+935B
    0x7487, # U+7487
    0x93C3, # U+93C3
    0x7EFE, # U+7EFE
    0x9286, # U+9286
    0x4FD3, # U+4FD3
    0x9227, # U+9227
    0x6D93, # U+6D93
    0x9428, # U+9428
    0x93B4, # U+93B4
    0x9365, # U+9365
    0x5BE4, # U+5BE4
    0x7035, # U+7035
    0x59AB, # U+59AB
    0x6748, # U+6748
    0x6FB6, # U+6FB6
    0x9359, # U+9359
    0x9366, # U+9366
    0x59DD, # U+59DD
    0x7ECB, # U+7ECB
    0x95C6, # U+95C6
    0xFFFD  # replacement character
)
$mojibakeMarkers = $mojibakeMarkerCodes | ForEach-Object { [string][char]$_ }

$badFiles = New-Object System.Collections.Generic.List[object]

foreach ($path in $Paths) {
    $resolved = Join-Path $root $path
    if (-not (Test-Path $resolved)) {
        continue
    }

    $items = if ((Get-Item $resolved).PSIsContainer) {
        Get-ChildItem -LiteralPath $resolved -Recurse -File
    } else {
        Get-Item -LiteralPath $resolved
    }

    foreach ($file in $items) {
        if ($extensions -notcontains $file.Extension.ToLowerInvariant()) {
            continue
        }

        try {
            $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        } catch {
            $badFiles.Add([PSCustomObject]@{
                File = $file.FullName.Substring($root.Path.Length + 1)
                Marker = "UTF8_READ_FAILED"
            })
            continue
        }

        foreach ($marker in $mojibakeMarkers) {
            if ($content.Contains($marker)) {
                $badFiles.Add([PSCustomObject]@{
                    File = $file.FullName.Substring($root.Path.Length + 1)
                    Marker = $marker
                })
                break
            }
        }
    }
}

if ($badFiles.Count -gt 0) {
    $badFiles | Format-Table -AutoSize
    throw "Encoding check failed: possible mojibake markers found."
}

Write-Host "Encoding check passed: UTF-8 text files contain no configured mojibake markers."
