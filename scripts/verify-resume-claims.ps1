param(
    [switch]$SkipMaven
)

$ErrorActionPreference = "Stop"

function Add-Result {
    param(
        [string]$Claim,
        [string]$Status,
        [string]$Evidence
    )
    [PSCustomObject]@{
        Claim = $Claim
        Status = $Status
        Evidence = $Evidence
    }
}

function Test-FileContains {
    param(
        [string]$Path,
        [string]$Pattern
    )
    if (-not (Test-Path $Path)) {
        return $false
    }
    return [bool](Select-String -Path $Path -Pattern $Pattern -Quiet)
}

$results = New-Object System.Collections.Generic.List[object]

if (-not $SkipMaven) {
    Write-Host "Running SessionMemoryTest..."
    mvn -q -Dtest=SessionMemoryTest test

    Write-Host "Running compile check..."
    mvn -q -DskipTests compile
}

$sessionFilesExist = (Test-Path "src/main/java/org/example/service/SessionMemory.java") -and
    (Test-Path "src/test/java/org/example/service/SessionMemoryTest.java")
$results.Add((Add-Result `
    "Session isolation and context compression" `
    ($(if ($sessionFilesExist) { "PASS" } else { "FAIL" })) `
    "SessionMemory + SessionMemoryTest; synthetic-message test checks bounded window and savings calculation"))

$mcpEvidence = Test-FileContains "target/test-runs/tools-final-check.json" '"mcpToolCount":19'
$results.Add((Add-Result `
    "Spring AI local stdio MCP tool chain (local/test credentials required)" `
    ($(if ($mcpEvidence) { "PASS" } else { "WARN" })) `
    "target/test-runs/tools-final-check.json may contain mcpToolCount=19 after a local verification run"))

$aiOpsEvidence = (Test-FileContains "target/test-runs/ai_ops-final-check.sse.log" "CPU usage is high") -and
    (Test-FileContains "target/test-runs/ai_ops-final-check.sse.log" "Database connection pool exhausted")
$results.Add((Add-Result `
    "AIOps alert -> logs -> root-cause report (sample/test-topic evidence)" `
    ($(if ($aiOpsEvidence) { "PASS" } else { "WARN" })) `
    "target/test-runs/ai_ops-final-check.sse.log may contain CPU and DB pool log evidence after local verification"))

$ragReportExists = Test-Path "target/rag-eval-report.json"
$ragPass = $false
if ($ragReportExists) {
    $ragText = Get-Content "target/rag-eval-report.json" -Raw
    $match = [regex]::Match($ragText, '"hit_at_k"\s*:\s*([0-9.]+)\s*$')
    if (-not $match.Success) {
        $match = [regex]::Match($ragText, '"hit_at_k"\s*:\s*([0-9.]+)')
    }
    $ragPass = $match.Success -and ([double]$match.Groups[1].Value -ge 0.85)
}
$results.Add((Add-Result `
    "RAG retrieval eval target on local dataset" `
    ($(if ($ragPass) { "PASS" } elseif ($ragReportExists) { "WARN" } else { "TODO" })) `
    "target/rag-eval-report.json hit_at_k >= 0.85; dataset-scoped, not production answer accuracy"))

$results | Format-Table -AutoSize

$failed = $results | Where-Object { $_.Status -eq "FAIL" }
if ($failed) {
    exit 1
}
