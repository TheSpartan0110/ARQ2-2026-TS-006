<#
.SYNOPSIS
  Runs the EatsNow performance scenarios of Spec 5 with k6 (Docker) and records the evidence of Spec 6.7.

.DESCRIPTION
  - catalog-baseline: Feature 2 read-only baseline, 50 concurrent catalog readers for 60 s.
  - catalog-mixed:    Feature 2, the same readers while 10 orders/s are submitted for 60 s.
  - order-create:     Feature 3, 10 orders/s for 60 s with Payments, Restaurant, Delivery and Notifications
                      stopped; afterwards checks in the Orders database that every accepted order has its outbox
                      entry, then starts the stopped services again (unless -KeepStopped).
  - all:              the three scenarios in that order.

  The access token comes from the real OIDC login (scripts/oidc-login.ps1). k6 runs inside the compose network,
  calls the Gateway (http://gateway:8080) and streams its metrics to Prometheus (remote write), so the run shows
  up in the Grafana dashboard "EatsNow - Load tests". Results land in load/results/ (Markdown + raw JSON) together
  with the environment description (commit, machine, container versions, dataset size).

.EXAMPLE
  ./load/run-load.ps1 -Scenario all
  ./load/run-load.ps1 -Scenario catalog-mixed -Duration 30s
#>
param(
    [ValidateSet("catalog-baseline", "catalog-mixed", "order-create", "all")]
    [string]$Scenario = "all",
    [string]$Duration = "60s",
    [string]$Email = "loadtest@example.test",     # synthetic customer of the load runs (Identity V3), not the demo one
    [string]$Password = "password123",
    [switch]$KeepStopped
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. "$root/scripts/oidc-login.ps1"

$K6_IMAGE = "grafana/k6:0.54.0"
$NETWORK = "eatsnow_eatsnow"
$DOWNSTREAM = @("payments", "restaurants", "delivery", "notifications")
$resultsDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force $resultsDir | Out-Null

# docker compose reports progress on stderr, which Windows PowerShell 5.1 turns into errors under "Stop".
function Invoke-Compose([string[]]$arguments) {
    $ErrorActionPreference = "Continue"
    & docker compose -f "$root/docker-compose.yml" @arguments 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($arguments -join ' ') failed" }
}

function Invoke-Sql([string]$sql) {
    return (docker exec eatsnow-postgres-1 psql -U postgres -d eatsnow -tAc $sql).Trim()
}

function Write-Environment {
    $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
    $os = Get-CimInstance Win32_OperatingSystem
    $memoryGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
    $engine = (docker info --format "{{.NCPU}}|{{.MemTotal}}|{{.ServerVersion}}") -split '\|'
    $dockerInfo = "$($engine[0]) CPUs, $([math]::Round([double]$engine[1] / 1GB, 1)) GB, Docker $($engine[2])"
    $images = docker ps -a --filter "name=eatsnow-" --format "| {{.Names}} | {{.Image}} |" | Sort-Object
    $lines = @(
        "# Load test environment",
        "",
        "- Recorded (UTC): $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))",
        "- Tested commit: $(git -C $root rev-parse HEAD) (branch $(git -C $root rev-parse --abbrev-ref HEAD))$(if (git -C $root status --porcelain) { ', with uncommitted changes' })",
        "- Compose file: docker-compose.yml at that commit",
        "- Machine: $($cpu.Name.Trim()), $($cpu.NumberOfCores) cores / $($cpu.NumberOfLogicalProcessors) threads, $memoryGb GB RAM",
        "- Operating system: $($os.Caption) $($os.Version)",
        "- Docker engine (all containers share it): $dockerInfo",
        "- Load generator: k6 ($K6_IMAGE) in the compose network, calling the Gateway",
        "- Dataset: $(Invoke-Sql 'select count(*) from catalog.restaurants') restaurants, $(Invoke-Sql 'select count(*) from catalog.menu_items') menu items, $(Invoke-Sql 'select count(*) from deliveries.drivers') drivers, $(Invoke-Sql 'select count(*) from orders.orders') orders before the run",
        "- Users and payments: synthetic customer $Email, synthetic payment token tok_ok (payment test double)",
        "",
        "| Container | Image |",
        "| --- | --- |"
    ) + $images
    $lines | Set-Content -Encoding utf8 (Join-Path $resultsDir "environment.md")
}

function Invoke-K6([string]$script, [hashtable]$envVars) {
    # The token is fetched per scenario: an access token lives 15 minutes.
    $token = (Get-EatsNowToken -Gateway "http://localhost:8090" -Email $Email -Password $Password).access_token
    $testId = "$((Get-Date).ToString('yyyyMMdd-HHmmss'))-$($envVars['SCENARIO'])"
    $dockerArgs = @(
        "run", "--rm", "--network", $NETWORK,
        "-v", "$($PSScriptRoot)\k6:/scripts:ro",
        "-v", "$($resultsDir):/results",
        "-e", "TOKEN=$token",
        "-e", "DURATION=$Duration",
        "-e", "K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write",
        "-e", "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),max",
        "-e", "K6_PROMETHEUS_RW_PUSH_INTERVAL=5s"
    )
    foreach ($key in $envVars.Keys) { $dockerArgs += @("-e", "$key=$($envVars[$key])") }
    $dockerArgs += @($K6_IMAGE, "run", "--quiet", "-o", "experimental-prometheus-rw", "--tag", "testid=$testId", "/scripts/$script")
    Write-Host "`n=== k6 $script ($($envVars['SCENARIO']), testid $testId) ===" -ForegroundColor Cyan
    $ErrorActionPreference = "Continue"                     # k6 logs on stderr
    & docker @dockerArgs
    # k6 exits 99 when a threshold fails: the run is still evidence, so it is reported, not thrown.
    if ($LASTEXITCODE -eq 99) { Write-Host "Thresholds failed (see the summary above)" -ForegroundColor Yellow }
    elseif ($LASTEXITCODE -ne 0) { throw "k6 failed with exit code $LASTEXITCODE" }
}

function Invoke-OrderCreate {
    Write-Host "Stopping downstream services: $($DOWNSTREAM -join ', ')" -ForegroundColor Yellow
    Invoke-Compose (@("stop") + $DOWNSTREAM)
    $since = Invoke-Sql "select now()"
    try {
        Invoke-K6 "order-create.js" @{ SCENARIO = "order-create" }

        # Spec 5: no accepted order lacks a durable outbox entry (OrderCreated and ChargePayment, same transaction).
        $accepted = Invoke-Sql "select count(*) from orders.orders where created_at >= '$since'"
        $missing = Invoke-Sql ("select count(*) from orders.orders o where o.created_at >= '$since' and not exists " +
                "(select 1 from orders.outbox x where x.order_id = o.id and x.routing_key = 'OrderCreated')")
        $pending = Invoke-Sql "select count(*) from orders.outbox where published_at is null"
        $check = @(
            "",
            "## Outbox durability check (Orders database, after the run)",
            "",
            "- Orders accepted during the run: $accepted",
            "- Accepted orders without an OrderCreated outbox entry: $missing",
            "- Unpublished outbox entries right after the run (downstream still stopped): $pending",
            "",
            "**Durability: $(if ($missing -eq '0' -and [int]$accepted -gt 0) { 'PASS' } else { 'FAIL' })**"
        )
        $latest = Get-ChildItem $resultsDir -Filter "*-order-create.md" | Sort-Object LastWriteTime | Select-Object -Last 1
        Add-Content -Encoding utf8 $latest.FullName ($check -join "`n")
        $check | ForEach-Object { Write-Host $_ }
    }
    finally {
        if (-not $KeepStopped) {
            Write-Host "Starting downstream services again: they consume the queued work" -ForegroundColor Yellow
            Invoke-Compose (@("start") + $DOWNSTREAM)
        }
    }
}

Write-Environment
switch ($Scenario) {
    "catalog-baseline" { Invoke-K6 "catalog-read.js" @{ SCENARIO = "catalog-baseline"; MODE = "baseline" } }
    "catalog-mixed" { Invoke-K6 "catalog-read.js" @{ SCENARIO = "catalog-mixed"; MODE = "mixed" } }
    "order-create" { Invoke-OrderCreate }
    "all" {
        Invoke-K6 "catalog-read.js" @{ SCENARIO = "catalog-baseline"; MODE = "baseline" }
        Invoke-K6 "catalog-read.js" @{ SCENARIO = "catalog-mixed"; MODE = "mixed" }
        Invoke-OrderCreate
    }
}
Write-Host "`nResults: $resultsDir" -ForegroundColor Green
