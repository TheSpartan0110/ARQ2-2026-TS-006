<#
.SYNOPSIS
  Full end-to-end demo through the API Gateway: customer, restaurant and driver all take part.

  Happy path : order -> payment authorized -> restaurant accepts -> preparation -> delivery assigned -> pickup ->
               completion -> DELIVERED (and emails in Mailpit).
  Reject path: the restaurant rejects the paid order -> REJECTED, refund requested, paymentStatus REFUNDED.

.PARAMETER Scenario
  Happy (default) or Reject.

.PARAMETER PaymentToken
  Synthetic token for the payment test double (tok_ok, tok_declined, tok_lost_response, tok_timeout; for refunds also
  tok_refund_lost, tok_refund_timeout, tok_refund_refused).

.EXAMPLE
  ./scripts/dev-flow.ps1
  ./scripts/dev-flow.ps1 -Scenario Reject
  ./scripts/dev-flow.ps1 -Scenario Reject -PaymentToken tok_refund_lost
#>
param(
    [ValidateSet("Happy", "Reject")][string]$Scenario = "Happy",
    [string]$PaymentToken = "tok_ok",
    [string]$Gateway = "http://localhost:8090",
    [string]$Password = "password123"
)
$ErrorActionPreference = "Stop"

# Seed data: Identity V1 (users) and Catalog V1/V2 (restaurants). Elena owns restaurant ...0050.
$BistroId = "00000000-0000-4000-8000-000000000050"
$PastaId  = "00000000-0000-4000-8000-000000000051"

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }

. "$PSScriptRoot/oidc-login.ps1"

# OIDC authorization code + PKCE on the Identity login page (ADR 005)
function Login($email) {
    $r = Get-EatsNowToken -Gateway $Gateway -Email $email -Password $Password
    return @{ Authorization = "Bearer $($r.access_token)" }
}

function Api($method, $path, $auth, $body = $null, [switch]$Idempotent) {
    $headers = @{} + $auth
    $headers["X-Correlation-ID"] = [guid]::NewGuid().ToString()
    if ($Idempotent) { $headers["Idempotency-Key"] = [guid]::NewGuid().ToString() }
    $request = @{ Method = $method; Uri = "$Gateway$path"; Headers = $headers; ContentType = "application/json" }
    if ($body -ne $null) { $request["Body"] = ($body | ConvertTo-Json -Depth 6 -Compress) }
    return Invoke-RestMethod @request
}

function WaitUntil($description, [scriptblock]$check, $seconds = 60) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $result = & $check
        if ($result) { return $result }
        Start-Sleep -Milliseconds 700
    }
    throw "Timed out waiting for: $description"
}

function ShowOrder($auth, $orderId) {
    $o = Api Get "/api/v1/orders/$orderId" $auth
    Write-Host ("   order status={0} paymentStatus={1} version={2}" -f $o.status, $o.paymentStatus, $o.version)
    return $o
}

Step "1. Log in as customer (Alex), restaurant (Elena) and driver (Sam)"
$customer   = Login "alex@example.test"
$restaurant = Login "elena@example.test"
$driver     = Login "sam@example.test"

if ($Scenario -eq "Happy") {
    Step "2. Driver goes on duty (the Driver record is created on the first request)"
    $me = Api Get "/api/v1/drivers/me" $driver
    if (-not $me.onDuty) { $me = Api Put "/api/v1/drivers/me/duty" $driver @{ onDuty = $true; expectedVersion = $me.version } -Idempotent }
    Write-Host "   driver $($me.id) onDuty=$($me.onDuty) version=$($me.version)"
}

Step "3. Customer: quote (signed by Catalog) and order (payment token: $PaymentToken)"
$quote = Api Post "/api/v1/quotes" $customer @{ restaurantId = $BistroId; items = @(@{ menuItemId = $PastaId; quantity = 1 }) } -Idempotent
# Invoke-WebRequest (not Api) to read the X-Trace-Id response header as well.
$orderResponse = Invoke-WebRequest -Method Post -Uri "$Gateway/api/v1/orders" -UseBasicParsing -ContentType "application/json" `
    -Headers ($customer + @{ "Idempotency-Key" = [guid]::NewGuid().ToString(); "X-Correlation-ID" = [guid]::NewGuid().ToString() }) `
    -Body (@{
        quoteToken = $quote.quoteToken
        deliveryAddress = @{ line1 = "123 Example Street"; city = "Medellin" }
        paymentMethodToken = $PaymentToken
    } | ConvertTo-Json -Depth 6 -Compress)
$receipt = $orderResponse.Content | ConvertFrom-Json
$orderId = $receipt.orderId
$correlationId = $receipt.correlationId
$traceId = $orderResponse.Headers["X-Trace-Id"]
Write-Host "   202 Accepted  orderId=$orderId  correlationId=$correlationId  traceId=$traceId"

Step "4. Payments authorizes the charge; the restaurant inbox receives the order"
WaitUntil "payment AUTHORIZED" { $o = ShowOrder $customer $orderId; if ($o.paymentStatus -eq "AUTHORIZED") { $o } } 90 | Out-Null
$inbox = WaitUntil "restaurant inbox entry" {
    $page = Api Get "/api/v1/restaurant-orders?status=PENDING" $restaurant
    $page.items | Where-Object { $_.orderId -eq $orderId } | Select-Object -First 1
}
Write-Host "   restaurant sees orderId=$($inbox.orderId) status=$($inbox.status) version=$($inbox.version)"

if ($Scenario -eq "Reject") {
    Step "5. Restaurant REJECTS the paid order -> REJECTED, one refund, paymentStatus REFUNDED"
    Api Post "/api/v1/restaurant-orders/$orderId/reject" $restaurant @{ expectedVersion = $inbox.version; reason = "Kitchen closed due to a gas outage" } -Idempotent | Out-Null
    WaitUntil "order REJECTED with the refund confirmed" {
        $o = ShowOrder $customer $orderId
        if ($o.status -eq "REJECTED" -and $o.paymentStatus -eq "REFUNDED") { $o }
    } 120 | Out-Null
} else {
    Step "5. Restaurant accepts, then starts preparation"
    $accepted = Api Post "/api/v1/restaurant-orders/$orderId/accept" $restaurant @{ expectedVersion = $inbox.version } -Idempotent
    WaitUntil "order ACCEPTED" { $o = ShowOrder $customer $orderId; if ($o.status -eq "ACCEPTED") { $o } } | Out-Null
    Api Post "/api/v1/restaurant-orders/$orderId/preparation" $restaurant @{ expectedVersion = $accepted.version } -Idempotent | Out-Null
    WaitUntil "order PREPARING" { $o = ShowOrder $customer $orderId; if ($o.status -eq "PREPARING") { $o } } | Out-Null

    Step "6. Delivery assigns the driver automatically; the order stays PREPARING until the pickup"
    $delivery = WaitUntil "delivery assigned to the driver" {
        $page = Api Get "/api/v1/deliveries?status=ASSIGNED" $driver
        $page.items | Where-Object { $_.orderId -eq $orderId } | Select-Object -First 1
    }
    Write-Host "   delivery $($delivery.id) ASSIGNED to driver $($delivery.driverId)"
    ShowOrder $customer $orderId | Out-Null

    Step "7. Driver confirms pickup (order -> ON_THE_WAY) and completion (order -> DELIVERED)"
    $picked = Api Post "/api/v1/deliveries/$($delivery.id)/pickup" $driver @{ expectedVersion = $delivery.version } -Idempotent
    WaitUntil "order ON_THE_WAY" { $o = ShowOrder $customer $orderId; if ($o.status -eq "ON_THE_WAY") { $o } } | Out-Null
    Api Post "/api/v1/deliveries/$($delivery.id)/completion" $driver @{ expectedVersion = $picked.version } -Idempotent | Out-Null
    WaitUntil "order DELIVERED" { $o = ShowOrder $customer $orderId; if ($o.status -eq "DELIVERED") { $o } } | Out-Null
}

Step "Done"
# Every trace of this order (creation, restaurant decisions, driver actions) carries eatsnow.correlation_id.
$tags = [uri]::EscapeDataString("{`"eatsnow.correlation_id`":`"$correlationId`"}")
Write-Host "Order creation trace (Jaeger):  http://localhost:16686/trace/$traceId"
Write-Host "Whole order lifecycle (Jaeger): http://localhost:16686/search?service=orders&tags=$tags&limit=50"
Write-Host "Emails sent to the customer:    http://localhost:8025   (Mailpit)"
Write-Host "Investigate in Grafana:         http://localhost:3000 -> Explore -> Loki"
Write-Host "  {service=~`".+`"} | json | orderId=`"$orderId`""

# Pipeline output for callers such as scripts/feature5-evidence.ps1
[pscustomobject]@{ OrderId = $orderId; CorrelationId = $correlationId; TraceId = $traceId }
