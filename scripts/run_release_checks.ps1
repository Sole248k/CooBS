<#
.SYNOPSIS
    One command that runs every backend check against a running stack and reports, scenario by
    scenario, whether the backend is ready for a release.
.DESCRIPTION
    It runs, in this order:

      0. scripts/reset_demo_data.ps1              batch state and the risk engine's counters
      1. the E2E suite (Newman)                   postman/e2e/CooBS_End_To_End.postman_collection.json
      2. test_daily_limit_reservation.ps1         the daily limit is given back when a transfer does not post
      3. test_outbound_interbank.ps1              transfer to another bank: accepted, refused, not a partner
      4. test_inbound_interbank.ps1               transfer from another bank, credited once
      5. test_risk_scenarios.ps1 -Scenario Burst  the fifth transfer in a row is declined on its score
      6. test_month_end_interest_notification.ps1 end of day on a month-end sends the interest notification
      7. test_risk_scenarios.ps1 -Scenario EngineDown   stops the risk engine for about a minute
      8. test_dead_letter_replay.ps1              stops the notification database for a moment

    and then prints the twelve demo scenarios of the service scope document (section 14) with what
    proved each one and the result. The exit code is 0 only when nothing failed.

    Steps 7 and 8 stop a container and start it again. -Quick leaves them out.

    The checks move a few pesos between seeded accounts and run an end of day, as the E2E suite
    always has. Run it on a test stack, with nobody else using it. Scenario 12 (concurrent
    transfers) is the JMeter plan in test/ and is not run from here.

    Needs Docker, Node.js (for npx newman) and the whole stack up. Every step's output is kept in
    a folder under the temporary directory; its path is printed at the end.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run_release_checks.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run_release_checks.ps1 -Quick
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\run_release_checks.ps1 -List
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    # Leaves out the two checks that stop a container.
    [switch]$Quick,
    # Does not reset batch state and the risk counters first.
    [switch]$NoReset,
    # Prints what would run and stops.
    [switch]$List
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$collection = "postman/e2e/CooBS_End_To_End.postman_collection.json"

# What proves each demo scenario. "E2E" is the suite's own folders; the rest are the step ids below.
$scenarios = @(
    @{ No = 1;  Name = "Customer signs in and opens Customer 360";    Proofs = @("E2E") },
    @{ No = 2;  Name = "Customer sends a transfer to another member"; Proofs = @("E2E") },
    @{ No = 3;  Name = "Teller watches the same transfer";            Proofs = @("E2E") },
    @{ No = 4;  Name = "Transfer declined by risk";                   Proofs = @("E2E", "burst", "enginedown") },
    @{ No = 5;  Name = "Transfer with not enough money";              Proofs = @("E2E", "limit") },
    @{ No = 6;  Name = "Double tap on Send";                          Proofs = @("E2E") },
    @{ No = 7;  Name = "Teller reverses a transfer";                  Proofs = @("E2E") },
    @{ No = 8;  Name = "Notification consumer breaks, then is fixed"; Proofs = @("dlq") },
    @{ No = 9;  Name = "Admin runs end of day";                       Proofs = @("E2E", "monthend") },
    @{ No = 10; Name = "Transfer to another bank";                    Proofs = @("E2E", "outbound") },
    @{ No = 11; Name = "Transfer from another bank";                  Proofs = @("inbound") },
    @{ No = 12; Name = "Concurrent transfers on one account";         Proofs = @() }
)

$steps = @(
    @{ Id = "limit";      Name = "Daily limit reservation";        Script = "test_daily_limit_reservation.ps1";         Arguments = "" },
    @{ Id = "outbound";   Name = "Transfer to another bank";       Script = "test_outbound_interbank.ps1";              Arguments = "" },
    @{ Id = "inbound";    Name = "Transfer from another bank";     Script = "test_inbound_interbank.ps1";               Arguments = "" },
    @{ Id = "burst";      Name = "Risk: burst of transfers";       Script = "test_risk_scenarios.ps1";                  Arguments = "-Scenario Burst" },
    @{ Id = "monthend";   Name = "Month-end interest notification"; Script = "test_month_end_interest_notification.ps1"; Arguments = "" },
    @{ Id = "enginedown"; Name = "Risk engine stopped";            Script = "test_risk_scenarios.ps1";                  Arguments = "-Scenario EngineDown"; StopsAContainer = $true },
    @{ Id = "dlq";        Name = "Dead-letter replay";             Script = "test_dead_letter_replay.ps1";              Arguments = "";                     StopsAContainer = $true }
)

# Which demo scenario a request of the E2E suite belongs to, from its number. Newman reports a
# request as "<folder> / <request>", for example "05. Distributed Transfer Saga / 5.4 Verify ...".
function Get-ScenarioOfRequest([string]$requestName) {
    $request = ($requestName -split ' / ')[-1]
    if ($request -notmatch '^\s*(\d+)\.(\d+)') { return 0 }
    $folder = [int]$Matches[1]
    $step = [int]$Matches[2]
    switch ($folder) {
        2 { return 1 }
        3 { return 1 }
        4 { return 2 }
        5 { if ($step -eq 2) { return 6 } elseif ($step -eq 4) { return 3 } else { return 2 } }
        6 { return 2 }
        7 { return 7 }
        8 { return 2 }
        9 { return 7 }
        10 { if ($step -le 1) { return 5 } else { return 0 } }
        11 { return 9 }
        12 { if ($step -le 5) { return 4 } else { return 10 } }
        default { return 0 }
    }
}

# Runs a program to the end with its output in a file, and returns its exit code. Start-Process is
# used so that what the program writes to its error stream is never taken for a PowerShell error.
function Invoke-Program([string]$file, [string]$arguments, [string]$log) {
    $process = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $repo -NoNewWindow -PassThru `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $null = $process.Handle
    $process.WaitForExit()
    if ((Test-Path "$log.err") -and (Get-Item "$log.err").Length -gt 0) {
        Add-Content -Path $log -Value (Get-Content "$log.err")
    }
    Remove-Item "$log.err" -ErrorAction SilentlyContinue
    return $process.ExitCode
}

function Invoke-Script([string]$script, [string]$arguments, [string]$log) {
    $path = Join-Path $repo "scripts\$script"
    return Invoke-Program "powershell.exe" "-NoProfile -ExecutionPolicy Bypass -File `"$path`" $arguments" $log
}

if ($List) {
    Write-Host "Would run, against $EnvironmentFile`:"
    if (-not $NoReset) { Write-Host "  0. scripts\reset_demo_data.ps1" }
    Write-Host "  1. E2E suite: $collection"
    $n = 2
    foreach ($step in $steps) {
        $left = if ($Quick -and $step.StopsAContainer) { "   (left out by -Quick)" } else { "" }
        Write-Host ("  {0}. scripts\{1} {2}{3}" -f $n, $step.Script, $step.Arguments, $left)
        $n++
    }
    exit 0
}

# ---------------------------------------------------------------------------- before anything runs
$vars = @{}
(Get-Content (Join-Path $repo $EnvironmentFile) -Raw | ConvertFrom-Json).values | ForEach-Object { $vars[$_.key] = $_.value }
try {
    $health = Invoke-RestMethod -Uri "$($vars.baseUrl)/actuator/health" -TimeoutSec 10
} catch {
    Write-Host "The gateway at $($vars.baseUrl) does not answer. Start the stack first; nothing was run." -ForegroundColor Red
    exit 2
}
if ($health.status -ne "UP") {
    Write-Host "The gateway at $($vars.baseUrl) reports $($health.status), not UP. Nothing was run." -ForegroundColor Red
    exit 2
}

$out = Join-Path ([System.IO.Path]::GetTempPath()) ("coobs-release-checks-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Path $out | Out-Null
$commit = (git -C $repo rev-parse --short HEAD)
Write-Host "Release checks at commit $commit against $($vars.baseUrl)" -ForegroundColor Cyan
Write-Host "Output of every step: $out`n"

$results = @{}          # step id -> PASS, FAIL or SKIPPED
$e2eFailures = @()      # names of the E2E requests with a failed assertion
$e2eSummary = "not run"

# ---------------------------------------------------------------------------- 0. reset
if (-not $NoReset) {
    Write-Host "0. Resetting batch state and risk counters ..." -NoNewline
    $code = Invoke-Script "reset_demo_data.ps1" "" (Join-Path $out "00-reset.log")
    $resetFailed = $code -ne 0 -or (Select-String -Path (Join-Path $out "00-reset.log") -Pattern "\[FAIL\]" -Quiet)
    Write-Host $(if ($resetFailed) { " reported a problem (see 00-reset.log); continuing" } else { " done" })
}

# ---------------------------------------------------------------------------- 1. E2E
Write-Host "1. E2E suite ..." -NoNewline
$junit = Join-Path $out "01-e2e.xml"
$newman = "npx --yes newman run $collection -e $EnvironmentFile --delay-request 150 --reporters cli,junit --reporter-junit-export `"$junit`""
$code = Invoke-Program "cmd.exe" "/c `"$newman`"" (Join-Path $out "01-e2e.log")
if (Test-Path $junit) {
    $suites = @(([xml](Get-Content $junit -Raw)).testsuites.testsuite)
    $total = ($suites | ForEach-Object { [int]$_.tests } | Measure-Object -Sum).Sum
    $failedSuites = @($suites | Where-Object { [int]$_.failures -gt 0 -or [int]$_.errors -gt 0 })
    $failed = ($failedSuites | ForEach-Object { [int]$_.failures + [int]$_.errors } | Measure-Object -Sum).Sum
    $e2eFailures = @($failedSuites | ForEach-Object { $_.name })
    $e2eSummary = "$($total - $failed) of $total assertions"
    $results["E2E"] = if ($code -eq 0 -and -not $failedSuites) { "PASS" } else { "FAIL" }
} else {
    # Newman did not get as far as a report: it is not installed, or the collection did not load.
    $e2eSummary = "did not run (see 01-e2e.log)"
    $results["E2E"] = "FAIL"
}
Write-Host " $($results["E2E"]), $e2eSummary" -ForegroundColor $(if ($results["E2E"] -eq "PASS") { "Green" } else { "Red" })
$e2eFailures | ForEach-Object { Write-Host "     failed: $_" -ForegroundColor Red }

# ---------------------------------------------------------------------------- 2 onwards
$n = 2
foreach ($step in $steps) {
    $label = "{0}. {1} ..." -f $n, $step.Name
    $log = Join-Path $out ("{0:00}-{1}.log" -f $n, $step.Id)
    $n++
    if ($Quick -and $step.StopsAContainer) {
        $results[$step.Id] = "SKIPPED"
        Write-Host "$label left out by -Quick" -ForegroundColor DarkGray
        continue
    }
    Write-Host $label -NoNewline
    $arguments = "-EnvironmentFile `"$EnvironmentFile`" $($step.Arguments)"
    $code = Invoke-Script $step.Script $arguments $log
    $results[$step.Id] = if ($code -eq 0) { "PASS" } else { "FAIL" }
    Write-Host " $($results[$step.Id])" -ForegroundColor $(if ($code -eq 0) { "Green" } else { "Red" })
    if ($code -ne 0) {
        Get-Content $log -Tail 12 | ForEach-Object { Write-Host "     $_" -ForegroundColor DarkGray }
    }
}

# ---------------------------------------------------------------------------- the twelve scenarios
# In brackets: without them PowerShell reads -f as Write-Host's -ForegroundColor.
Write-Host ("`n{0,3}  {1,-46} {2,-34} {3}" -f "#", "Demo scenario", "Proved by", "Result")
Write-Host ("-" * 100)
$anyFailure = $false
foreach ($scenario in $scenarios) {
    if (-not $scenario.Proofs) {
        Write-Host ("{0,3}  {1,-46} {2,-34} {3}" -f $scenario.No, $scenario.Name, "JMeter, test/jmeter-load-test.jmx", "NOT RUN HERE") -ForegroundColor DarkGray
        continue
    }
    $states = @()
    foreach ($proof in $scenario.Proofs) {
        if ($proof -eq "E2E") {
            $mine = @($e2eFailures | Where-Object { (Get-ScenarioOfRequest $_) -eq $scenario.No })
            # Without a report nothing in the suite is known to have passed.
            $states += if ($e2eSummary -like "did not run*" -or $mine) { "FAIL" } else { "PASS" }
        } else {
            $states += $results[$proof]
        }
    }
    if ($states -contains "FAIL") {
        $result = "FAIL"
    } elseif (-not ($states -contains "PASS")) {
        $result = "NOT RUN"
    } elseif ($states -contains "SKIPPED") {
        $result = "PASS (one check left out)"
    } else {
        $result = "PASS"
    }
    if ($result -eq "FAIL") { $anyFailure = $true }
    $colour = if ($result -eq "FAIL") { "Red" } elseif ($result -like "PASS*") { "Green" } else { "DarkGray" }
    Write-Host ("{0,3}  {1,-46} {2,-34} {3}" -f $scenario.No, $scenario.Name, ($scenario.Proofs -join ", "), $result) -ForegroundColor $colour
}

# Failures in the suite that belong to no demo scenario: health, access rules, limits, token revocation.
$other = @($e2eFailures | Where-Object { (Get-ScenarioOfRequest $_) -eq 0 })
if ($other) {
    $anyFailure = $true
    Write-Host "`nOther checks in the E2E suite that failed (health, access rules, limits):" -ForegroundColor Red
    $other | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
}
if ($results.Values -contains "FAIL") { $anyFailure = $true }

Write-Host "`nE2E suite: $e2eSummary. Commit $commit. Output: $out"
if ($anyFailure) {
    Write-Host "NOT READY: at least one check failed." -ForegroundColor Red
    exit 1
}
$note = if ($Quick) { " Two checks were left out by -Quick, and scenario 12 is not run from here." } else { " Scenario 12 is not run from here." }
Write-Host "READY: every check that ran passed.$note" -ForegroundColor Green
exit 0
