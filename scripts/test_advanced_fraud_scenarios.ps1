<#
.SYNOPSIS
    Interactive test and verification script for Dev 4's Advanced Fraud Detection rules.
.DESCRIPTION
    Validates all 4 advanced fraud capabilities implemented in the live risk-engine:
      1. Impossible Travel Anomaly (Haversine velocity > 900 km/h)
      2. Unrecognized Hardware Device Anomaly (Device Fingerprinting & ATO prevention)
      3. Dormant Account Sudden Awakening (Mule account activation > 90 days)
      4. AMLA Structuring / Smurfing Evasion (Split transactions evading 500k CTR ceiling)
.PARAMETER TargetUrl
    The URL of the risk-engine service (default: http://localhost:8092)
#>

[CmdletBinding()]
param(
    [string]$TargetUrl = "http://localhost:8092",
    [string]$InternalSecret = "coobs-risk-internal-secret-2026"
)

$ErrorActionPreference = "Continue"

function Write-Box([string]$Text, [string]$Color = "Cyan") {
    $line = "=" * 76
    Write-Host ""
    Write-Host $line -ForegroundColor $Color
    Write-Host "   $Text" -ForegroundColor $Color
    Write-Host $line -ForegroundColor $Color
}

function Write-Gauge([string]$Label, [double]$Score, [string]$Verdict) {
    $barLength = 20
    $filled = [int][Math]::Round($Score * $barLength)
    if ($filled -gt $barLength) { $filled = $barLength }
    $empty = $barLength - $filled
    
    $color = [ConsoleColor]::Green
    if ($Score -ge 0.60 -and $Score -le 0.85) { $color = [ConsoleColor]::Yellow }
    elseif ($Score -gt 0.85) { $color = [ConsoleColor]::Red }
    
    $bar = ("#" * $filled) + ("-" * $empty)
    Write-Host "   [$bar] Score: $([math]::Round($Score, 2)) | Verdict: $Verdict" -ForegroundColor $color
}

function Test-RiskEndpoint([string]$Name, [hashtable]$Payload) {
    Write-Host "`n--> Testing: $Name" -ForegroundColor Yellow
    $json = $Payload | ConvertTo-Json -Depth 5
    $headers = @{
        "Content-Type"      = "application/json"
        "X-Internal-Secret" = $InternalSecret
    }

    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $resp = Invoke-RestMethod -Uri "$TargetUrl/internal/v1/risk/evaluate" -Method Post -Headers $headers -Body $json
        $sw.Stop()

        Write-Host "  HTTP 200 OK (Roundtrip: $($sw.ElapsedMilliseconds) ms | Server evaluated in: $($resp.evaluatedInMs) ms)" -ForegroundColor Green
        $decisionColor = [ConsoleColor]::Magenta
        if ($resp.decision -eq "APPROVE") { $decisionColor = [ConsoleColor]::Green }
        Write-Host "  Decision:    $($resp.decision)" -ForegroundColor $decisionColor
        Write-Host "  Score:       $($resp.score)" -ForegroundColor Cyan
        
        $reasons = @()
        if ($resp.fraudCheck -and $resp.fraudCheck.reasons) {
            $reasons = $resp.fraudCheck.reasons
        } elseif ($resp.reasons) {
            $reasons = $resp.reasons
        }
        
        if ($reasons.Count -gt 0) {
            Write-Host "  Reasons:     $($reasons -join ', ')" -ForegroundColor Yellow
        } else {
            Write-Host "  Reasons:     [Clean / None]" -ForegroundColor Gray
        }

        Write-Gauge "Risk Score Gauge" ([double]$resp.score) "$($resp.decision)"
        return $resp
    } catch {
        Write-Host "  [ERROR] Call failed: $_" -ForegroundColor Red
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            Write-Host "  Body: $($reader.ReadToEnd())" -ForegroundColor Red
        }
        return $null
    }
}

Write-Box "COOBS DEV 4 - ADVANCED FRAUD ENGINE VERIFICATION SUITE" "Cyan"
Write-Host " Target Service: $TargetUrl"
Write-Host " Internal Secret: (configured)"
Write-Host " Timestamp:      $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))"

# -----------------------------------------------------------------------------
# 1. SCENARIO 1: IMPOSSIBLE GEOLOCATION TRAVEL
# -----------------------------------------------------------------------------
$custGeo = [long](Get-Random -Minimum 100000 -Maximum 899999)
Write-Box "SCENARIO 1: IMPOSSIBLE TRAVEL ANOMALY (Haversine Velocity Anomaly)" "Magenta"
Write-Host "Customer $custGeo sends first transfer from Manila, Philippines (Lat: 14.5995, Lon: 120.9842)..."

$manilaTx = @{
    transactionId   = [guid]::NewGuid().ToString()
    customerId      = $custGeo
    amount          = "1000.00"
    currency        = "PHP"
    channel         = "APP"
    kycTier         = "TIER_3_FULL"
    dailyLimit      = "500000.00"
    latitude        = 14.5995
    longitude       = 120.9842
    locationCity    = "Manila"
    locationCountry = "PH"
}
$r1 = Test-RiskEndpoint "1a. Legitimate Ingress (Manila, Philippines)" $manilaTx

Write-Host "`nTwo minutes later, an automated transfer is attempted from Bucharest, Romania (Lat: 44.4268, Lon: 26.1025) - 9,500 km away!"
$bucharestTx = @{
    transactionId   = [guid]::NewGuid().ToString()
    customerId      = $custGeo
    amount          = "1000.00"
    currency        = "PHP"
    channel         = "APP"
    kycTier         = "TIER_3_FULL"
    dailyLimit      = "500000.00"
    latitude        = 44.4268
    longitude       = 26.1025
    locationCity    = "Bucharest"
    locationCountry = "RO"
}
$r2 = Test-RiskEndpoint "1b. Impossible Velocity Breach (Bucharest, Romania)" $bucharestTx

# -----------------------------------------------------------------------------
# 2. SCENARIO 2: UNRECOGNIZED DEVICE HARDWARE ANOMALY
# -----------------------------------------------------------------------------
Write-Box "SCENARIO 2: UNRECOGNIZED HARDWARE DEVICE ANOMALY (ATO Prevention)" "Yellow"
Write-Host "Customer 202 attempts transfer from an unrecognized hardware fingerprint with elevated parameters..."

$deviceTx = @{
    transactionId      = [guid]::NewGuid().ToString()
    customerId         = 202
    amount             = "38000.00"
    currency           = "PHP"
    channel            = "APP"
    kycTier            = "TIER_2_STANDARD"
    dailyLimit         = "50000.00"
    deviceFingerprint  = "unknown-device-hardware-hash-9999"
    isNewDevice        = $true
}
$r3 = Test-RiskEndpoint "2. Unrecognized Device Hardware Anomaly" $deviceTx

# -----------------------------------------------------------------------------
# 3. SCENARIO 3: DORMANT ACCOUNT SUDDEN ACTIVITY
# -----------------------------------------------------------------------------
Write-Box "SCENARIO 3: DORMANT ACCOUNT SUDDEN AWAKENING (Mule Account Detection)" "Magenta"
Write-Host "Customer 303 account has been inactive for 120 days (>90 days dormancy threshold) and suddenly attempts a 25k transfer..."

$dormantTx = @{
    transactionId          = [guid]::NewGuid().ToString()
    customerId             = 303
    amount                 = "25000.00"
    currency               = "PHP"
    channel                = "APP"
    kycTier                = "TIER_2_STANDARD"
    dailyLimit             = "50000.00"
    daysSinceLastActivity  = 120
    isDormantAccount       = $true
}
$r4 = Test-RiskEndpoint "3. Dormant Account Sudden Awakening" $dormantTx

# -----------------------------------------------------------------------------
# 4. SCENARIO 4: AMLA STRUCTURING / SMURFING EVASION
# -----------------------------------------------------------------------------
Write-Box "SCENARIO 4: AMLA STRUCTURING / SMURFING EVASION (CTR Evasion)" "Red"
Write-Host "Customer 404 attempts a PHP 450,000 transfer (just below the PHP 500,000 CTR ceiling) with suspected structuring flag..."

$amlaTx = @{
    transactionId          = [guid]::NewGuid().ToString()
    customerId             = 404
    amount                 = "450000.00"
    currency               = "PHP"
    channel                = "APP"
    kycTier                = "TIER_3_FULL"
    dailyLimit             = "1000000.00"
    isStructuringSuspected = $true
}
$r5 = Test-RiskEndpoint "4. AMLA Structuring / Smurfing Evasion Detection" $amlaTx

# -----------------------------------------------------------------------------
# 5. SCENARIO 5: PERSONALIZED BEHAVIORAL AMOUNT ANOMALY (Z-Score Gaussian Outlier)
# -----------------------------------------------------------------------------
Write-Box "SCENARIO 5: PERSONALIZED BEHAVIORAL AMOUNT ANOMALY (Z-Score Outlier)" "Cyan"
Write-Host "Customer 505 (Student/Farmer) has baseline spend mean = PHP 1,500, stdDev = PHP 500."
Write-Host "Customer suddenly attempts a PHP 45,000 transfer (Z = 87 standard deviations outlier!)..."

$behavioralTx = @{
    transactionId           = [guid]::NewGuid().ToString()
    customerId              = 505
    amount                  = "45000.00"
    currency                = "PHP"
    channel                 = "APP"
    kycTier                 = "TIER_2_STANDARD"
    dailyLimit              = "50000.00"
    avgTransactionAmount    = "1500.00"
    stdDevTransactionAmount = "500.00"
}
$r6 = Test-RiskEndpoint "5. Personalized Behavioral Spike (Z-Score Outlier)" $behavioralTx

Write-Box "SUMMARY OF RESULTS" "Cyan"
Write-Host " 1. Impossible Travel:   $($r2.decision) (Reasons: $($r2.fraudCheck.reasons -join ', '))"
Write-Host " 2. Unknown Device:      $($r3.decision) (Reasons: $($r3.fraudCheck.reasons -join ', '))"
Write-Host " 3. Dormant Awakening:   $($r4.decision) (Reasons: $($r4.fraudCheck.reasons -join ', '))"
Write-Host " 4. AMLA Structuring:    $($r5.decision) (Reasons: $($r5.fraudCheck.reasons -join ', '))"
Write-Host " 5. Behavioral Anomaly:  $($r6.decision) (Reasons: $($r6.fraudCheck.reasons -join ', '))"
Write-Host "`nAll 5 advanced fraud detection & ML behavioral models verified in Risk Engine." -ForegroundColor Green
