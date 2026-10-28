# k6 부하 테스트를 같은 조건으로 여러 번 돌려 평균과 표준편차를 계산한다.
#
# 사용법 (PowerShell, 프로젝트 폴더에서):
#   앱을 측정할 방식으로 먼저 띄운다.  예) --spring.profiles.active=oracle --reservation.strategy=pessimistic
#   powershell -ExecutionPolicy Bypass -File .\k6\repeat.ps1 -Strategy pessimistic
#   powershell -ExecutionPolicy Bypass -File .\k6\repeat.ps1 -Strategy none -Runs 10
#   (윈도우 기본 설정에서는 .ps1 실행이 막혀 있어서 -ExecutionPolicy Bypass 를 붙인다)
#
# 첫 1회(-Warmup)는 워밍업으로 버리고, 그다음 -Runs 회를 집계한다. 결과는 k6\results\<Strategy>.csv 에도 저장된다.
param(
    [Parameter(Mandatory = $true)][string]$Strategy,
    [int]$Runs = 5,
    [int]$Warmup = 1,
    [int]$Users = 1000,
    [int]$Stock = 100
)

$k6Script = Join-Path $PSScriptRoot 'reserve.js'
$resultDir = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null

function Get-Stat($values) {
    $n = @($values).Count
    $mean = ($values | Measure-Object -Average).Average
    $sd = 0
    if ($n -gt 1) {
        $sum = 0
        foreach ($x in $values) { $sum += [math]::Pow($x - $mean, 2) }
        $sd = [math]::Sqrt($sum / ($n - 1))
    }
    return [pscustomobject]@{ Mean = [math]::Round($mean, 1); Sd = [math]::Round($sd, 1) }
}

$rows = @()
$total = $Warmup + $Runs
for ($i = 1; $i -le $total; $i++) {
    $isWarmup = $i -le $Warmup
    $label = if ($isWarmup) { "워밍업 $i/$Warmup" } else { "측정 $($i - $Warmup)/$Runs" }
    Write-Host "[$Strategy] $label 실행 중... (회원 로그인 때문에 수십 초 걸릴 수 있습니다)"

    # k6 는 로그를 stderr 로 내보내므로 cmd 로 합쳐서 문자열로 받는다.
    $out = cmd /c "k6 run -e USERS=$Users -e STOCK=$Stock `"$k6Script`" 2>&1" | Out-String

    $result = [regex]::Match($out, 'K6_RESULT (\{.*\})')
    $integrity = [regex]::Match($out, 'K6_INTEGRITY (\{[^}]*\})')
    if (-not $result.Success -or -not $integrity.Success) {
        Write-Warning "결과를 읽지 못했습니다. 앱이 떠 있는지, 관리자 계정/주소를 확인하세요. 출력 마지막 줄:"
        ($out -split "`n" | Select-Object -Last 8) | ForEach-Object { Write-Host $_ }
        continue
    }
    if ($isWarmup) { continue }

    $r = $result.Groups[1].Value | ConvertFrom-Json
    $g = $integrity.Groups[1].Value | ConvertFrom-Json
    $rows += [pscustomobject]@{
        Strategy = $Strategy; Run = $i - $Warmup; Oversold = [int]$g.oversold; Sold = [int]$g.sold
        Tps = $r.tps; AvgMs = $r.avg; MedMs = $r.med; P95Ms = $r.p95; MaxMs = $r.max
        FailPct = $r.failPct; Unexpected = [int]$r.unexpected
    }
}

if ($rows.Count -eq 0) { Write-Error "집계할 결과가 없습니다."; exit 1 }

Write-Host ""
Write-Host "===== $Strategy : 회차별 결과 ====="
$rows | Format-Table -AutoSize

$csv = Join-Path $resultDir "$Strategy.csv"
$rows | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $csv

Write-Host "===== $Strategy : 평균 ± 표준편차 ($($rows.Count)회) ====="
foreach ($m in 'Oversold', 'Tps', 'MedMs', 'P95Ms', 'FailPct') {
    $s = Get-Stat ($rows | ForEach-Object { [double]$_.$m })
    '{0,-9} {1,10} ± {2}' -f $m, $s.Mean, $s.Sd | Write-Host
}
Write-Host ""
Write-Host "저장: $csv"
Write-Host "마지막으로 정리: DELETE FROM members WHERE email LIKE 'bench%@test.com'; COMMIT;"
