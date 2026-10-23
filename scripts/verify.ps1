# =============================================================================
#  RBAC 后端启动 + API 验证脚本  (PowerShell 5+)
#  用法：  .\scripts\verify.ps1                 # 自动探测 Docker / 本地 MySQL / 无 DB
#         .\scripts\verify.ps1 -Mode docker     # 强制用 docker compose
#         .\scripts\verify.ps1 -Mode local-mysql # 强制用本机 MySQL
#         .\scripts\verify.ps1 -Mode no-db      # 强制用 local profile（仅测 health）
#         .\scripts\verify.ps1 -SkipStart       # 跳过启动，只跑 API 验证
# =============================================================================

param(
    [ValidateSet("auto","docker","local-mysql","no-db")]
    [string]$Mode = "auto",
    [switch]$SkipStart
)

$ErrorActionPreference = "Stop"

# ---------- 工具函数 ----------
function Write-Step($n, $title) { Write-Host ""; Write-Host "[$n] $title" -ForegroundColor Cyan }
function Write-Pass($msg)      { Write-Host "  PASS   $msg" -ForegroundColor Green }
function Write-Fail($msg)      { Write-Host "  FAIL   $msg" -ForegroundColor Red }
function Write-Info($msg)      { Write-Host "  INFO   $msg" -ForegroundColor Gray }

function Test-Cmd($name) { return [bool](Get-Command $name -ErrorAction SilentlyContinue) }

function Wait-Port($port, $maxSec=60) {
    Write-Info "wait port $port up to ${maxSec}s ..."
    for ($i=0; $i -lt $maxSec; $i++) {
        try {
            $ok = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue -InformationLevel Quiet
            if ($ok) { Write-Pass "port $port listening"; return $true }
        } catch {}
        Start-Sleep -Seconds 1
    }
    Write-Fail "port $port not listening after ${maxSec}s"; return $false
}

function Wait-Http($url, $expectedStatus=200, $maxSec=60) {
    Write-Info "wait HTTP $url -> $expectedStatus up to ${maxSec}s ..."
    for ($i=0; $i -lt $maxSec; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($resp.StatusCode -eq $expectedStatus) { Write-Pass "$url => $expectedStatus"; return $resp }
        } catch {
            $status = $_.Exception.Response.StatusCode.value__
            if ($status -eq $expectedStatus) { Write-Pass "$url => $expectedStatus"; return $null }
        }
        Start-Sleep -Seconds 1
    }
    Write-Fail "$url did not return $expectedStatus"; return $null
}

function Invoke-Api($method, $path, $token=$null, $body=$null, $expectedStatus=200) {
    $url   = "$BASE$path"
    $hdrs  = @{}
    if ($token) { $hdrs["Authorization"] = "Bearer $token" }
    try {
        if ($body) {
            $hdrs["Content-Type"] = "application/json"
            $json = ($body | ConvertTo-Json -Compress)
            $resp = Invoke-WebRequest -Method $method -Uri $url -Headers $hdrs -Body $json -UseBasicParsing -TimeoutSec 10
        } else {
            $resp = Invoke-WebRequest -Method $method -Uri $url -Headers $hdrs -UseBasicParsing -TimeoutSec 10
        }
        if ($resp.StatusCode -eq $expectedStatus) {
            Write-Pass "$method $path => $($resp.StatusCode)"
            return $resp
        }
        Write-Fail "$method $path => expected $expectedStatus but got $($resp.StatusCode)"
        return $null
    } catch {
        $got = $_.Exception.Response.StatusCode.value__
        if ($got -eq $expectedStatus) {
            Write-Pass "$method $path => $got"
            return $null
        }
        Write-Fail "$method $path => expected $expectedStatus but got $got"
        return $null
    }
}

# ---------- 全局 ----------
$BASE = "http://127.0.0.1:8080/api"
$DEFAULT_ADMIN_PASSWORD = "Admin@123"
$allPass = 0; $allFail = 0

# ---------- Step 1: 环境 ----------
Write-Step 1 "Environment probe"

if (-not (Test-Cmd java))  { Write-Fail "java not on PATH, need JDK 21"; exit 1 }
java -version 2>&1 | Select-Object -First 1 | ForEach-Object { Write-Info "  $_" }

$mvn = Test-Cmd mvn
$docker = Test-Cmd docker
$dockerStatus = if ($docker) { docker info --format '{{.ServerVersion}}' 2>$null } else { $null }

Write-Info "  mvn    = $mvn"
Write-Info "  docker = $docker / ${dockerStatus:-down}"

# ---------- Step 2: 启动 ----------
Write-Step 2 "Start application (mode = $Mode)"

$actMode = $Mode
if ($Mode -eq "auto") {
    if ($docker -and $dockerStatus)             { $actMode = "docker" }
    elseif (Get-Service MySQL84 -ErrorAction SilentlyContinue) { $actMode = "local-mysql" }
    else                                        { $actMode = "no-db" }
}
Write-Info "  resolved mode = $actMode"

if ($SkipStart) {
    Write-Info "  SKIP_START flag set, assuming app already running at $BASE"
} else {
    if ($actMode -eq "docker") {
        Write-Info "docker compose up -d"
        docker compose up -d 2>&1 | ForEach-Object { Write-Info "  $_" }
        Wait-Port 3306 120 | Out-Null
        Wait-Port 6379  30 | Out-Null
        Wait-Port 8080 120 | Out-Null
    }
    elseif ($actMode -eq "local-mysql") {
        Write-Info "ensure MySQL84 service is running"
        Start-Service MySQL84 -ErrorAction SilentlyContinue
        Wait-Port 3306 60 | Out-Null
        if (-not $mvn) { Write-Fail "mvn not on PATH, cannot build jar"; exit 1 }
        Write-Info "mvn clean package -DskipTests"
        mvn -B clean package -DskipTests 2>&1 | Select-Object -Last 5 | ForEach-Object { Write-Info "  $_" }
        jar cf target/app.jar -C target/classes . | Out-Null  # no-op; we boot directly
        Write-Info "start jar: java -jar rbac-bootstrap-*.jar --spring.profiles.active=container"
        $jar = (Get-ChildItem rbac-bootstrap/target/rbac-bootstrap-*.jar -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
        if (-not $jar) { Write-Fail "no jar built"; exit 1 }
        Start-Process -FilePath java -ArgumentList "-jar",$jar,"--spring.profiles.active=container" -WorkingDirectory . -NoNewWindow
        Wait-Port 8080 120 | Out-Null
    }
    else { # no-db
        if (-not $mvn) { Write-Fail "mvn not on PATH, cannot build jar"; exit 1 }
        Write-Info "mvn clean package -DskipTests"
        mvn -B clean package -DskipTests 2>&1 | Select-Object -Last 5 | ForEach-Object { Write-Info "  $_" }
        $jar = (Get-ChildItem rbac-bootstrap/target/rbac-bootstrap-*.jar -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
        if (-not $jar) { Write-Fail "no jar built"; exit 1 }
        Write-Info "start jar with local profile (no DB, no Redis)"
        Start-Process -FilePath java -ArgumentList "-jar",$jar,"--spring.profiles.active=local" -WorkingDirectory . -NoNewWindow
        Wait-Port 8080 60 | Out-Null
    }
}

# ---------- Step 3: 健康检查 ----------
Write-Step 3 "Health & Actuator"
$health = Wait-Http "$BASE/actuator/health" 200 60
if ($health) { $allPass++ } else { $allFail++ }

$envBody = Wait-Http "$BASE/actuator/info" 200 15
$metrics = Wait-Http "$BASE/actuator/metrics" 200 15
if ($envBody) { $allPass++ } else { $allFail++ }
if ($metrics) { $allPass++ } else { $allFail++ }

# ---------- Step 4: API 链路（有 DB 才跑） ----------
$skipApi = ($actMode -eq "no-db")
if ($skipApi) {
    Write-Step 4 "API chain (SKIPPED - no DB, use docker or local-mysql mode)"
} else {
    Write-Step 4 "API chain (admin = admin / $DEFAULT_ADMIN_PASSWORD)"

    # 4.1 错误密码 -> 401
    $r = Invoke-Api "Post" "/auth/login" $null @{ username="admin"; password="wrong" } 401
    if ($r) { $allPass++ } else { $allFail++ }

    # 4.2 正确密码 -> 200 + token
    $login = Invoke-Api "Post" "/auth/login" $null @{ username="admin"; password=$DEFAULT_ADMIN_PASSWORD } 200
    if ($login -and $login.Content) {
        $body = $login.Content | ConvertFrom-Json
        $tok  = $body.data.token
        Write-Info "  token captured (len=$($tok.Length))"
        $allPass++
    } else {
        $tok = $null; $allFail++
    }

    if ($tok) {
        # 4.3 用 token GET /users -> 200
        $r = Invoke-Api "Get" "/users" $tok $null 200
        if ($r) { $allPass++ } else { $allFail++ }

        # 4.4 不带 token GET /users -> 401
        $r = Invoke-Api "Get" "/users" $null $null 401
        if ($r) { $allPass++ } else { $allFail++ }

        # 4.5 调 /audit/read（admin 有 *，应该能）-> 200
        $r = Invoke-Api "Get" "/audit" $tok $null 200
        if ($r) { $allPass++ } else { $allFail++ }

        # 4.6 登出 -> 200
        $r = Invoke-Api "Post" "/auth/logout" $tok $null 200
        if ($r) { $allPass++ } else { $allFail++ }

        # 4.7 用旧 token 再访问 -> 40101（令牌作废）或 401
        $r = Invoke-Api "Get" "/users" $tok $null 401
        if ($r) { $allPass++ } else { $allFail++ }

        # 4.8 新密码登录 -> 失败（默认密码还没改），跳过

        # 4.9 刷新令牌 -> 200
        $login2 = Invoke-Api "Post" "/auth/login" $null @{ username="admin"; password=$DEFAULT_ADMIN_PASSWORD } 200
        if ($login2) {
            $tok2 = ($login2.Content | ConvertFrom-Json).data.token
            $r = Invoke-Api "Post" "/auth/refresh" $tok2 $null 200
            if ($r) { $allPass++ } else { $allFail++ }
        }
    }
}

# ---------- Step 5: 汇总 ----------
Write-Host ""
Write-Host "================ RESULT ================" -ForegroundColor White
Write-Host "  PASS: $allPass    FAIL: $allFail" -ForegroundColor $(if ($allFail -eq 0) {"Green"} else {"Red"})
Write-Host "========================================" -ForegroundColor White

if ($allFail -gt 0) { exit 1 } else { exit 0 }
