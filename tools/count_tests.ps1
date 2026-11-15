Set-Location 'C:\Users\User\.gemini\antigravity\scratch\nebula'

$testFiles = Get-ChildItem -Recurse -File -Filter 'TEST-*.xml' | Where-Object { $_.FullName -notmatch "intermediates" }
$totalTests = 0
$totalFailures = 0
$totalSkipped = 0

foreach ($f in $testFiles) {
    [xml]$x = Get-Content $f.FullName
    $tests = [int]$x.testsuite.tests
    $failures = [int]$x.testsuite.failures
    $skipped = [int]$x.testsuite.skipped
    $totalTests += $tests
    $totalFailures += $failures
    $totalSkipped += $skipped
    Write-Host "$($x.testsuite.name): $tests tests ($failures failures, $skipped skipped)"
}

Write-Host "`n=== SUMMARY ==="
Write-Host "Total Unit Tests: $totalTests"
Write-Host "Failures: $totalFailures"
Write-Host "Skipped: $totalSkipped"
