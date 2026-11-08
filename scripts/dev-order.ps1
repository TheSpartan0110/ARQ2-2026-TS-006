<#
.SYNOPSIS
  End-to-end demo of the first order flow through the API Gateway (Step 2):
  login (Identity) -> quote (Catalog) -> order (Orders) -> payment (Payments, test double) -> status.

.PARAMETER PaymentToken
  Synthetic payment token; it selects the behaviour of the payment test double:
    tok_ok              authorized                      -> paymentStatus AUTHORIZED
    tok_declined        definitive refusal              -> status PAYMENT_FAILED
    tok_lost_response   charged, response lost          -> UNKNOWN, then AUTHORIZED after reconciliation (~30 s)
    tok_timeout         timeout, never recorded         -> stays UNKNOWN (PaymentUnresolved alert after 5 min)

.PARAMETER Replay
  Sends the very same request again (same Idempotency-Key): the original 202 is replayed, no second order.

.EXAMPLE
  ./scripts/dev-order.ps1
  ./scripts/dev-order.ps1 -PaymentToken tok_declined
  ./scripts/dev-order.ps1 -Replay
#>
param(
    [string]$PaymentToken = "tok_ok",
    [string]$Gateway = "http://localhost:8090",
    [string]$Email = "alex@example.test",     # seeded demo customer (Identity V1 migration)
    [string]$Password = "password123",
    [int]$WaitSeconds = 45,
    [switch]$Replay
)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot/oidc-login.ps1"

# 1. Login: OIDC authorization code + PKCE on the Identity login page, through the gateway
$login = Get-EatsNowToken -Gateway $Gateway -Email $Email -Password $Password
$auth = @{ Authorization = "Bearer $($login.access_token)" }
Write-Host "Logged in as $Email (token expires in $($login.expires_in) s)"

# 2. Quote: signed by Catalog, valid for 120 s (seed data: Burger House / Classic Burger)
$quoteBody = @{
    restaurantId = "00000000-0000-4000-8000-000000000002"
    items        = @(@{ menuItemId = "00000000-0000-4000-8000-000000000003"; quantity = 1 })
} | ConvertTo-Json -Depth 4
$quote = Invoke-RestMethod -Method Post -Uri "$Gateway/api/v1/quotes" -ContentType "application/json" `
    -Headers ($auth + @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }) -Body $quoteBody
Write-Host "Quote $($quote.id): total $($quote.total.amountMinor) $($quote.total.currency)"

# 3. Order
$orderBody = [ordered]@{
    quoteToken         = $quote.quoteToken
    deliveryAddress    = [ordered]@{ line1 = "123 Example Street"; city = "Medellin" }
    paymentMethodToken = $PaymentToken
} | ConvertTo-Json -Depth 4 -Compress
$orderHeaders = $auth + @{
    "Idempotency-Key"  = [guid]::NewGuid().ToString()
    "X-Correlation-ID" = [guid]::NewGuid().ToString()
}
Write-Host "POST $Gateway/api/v1/orders  (payment token: $PaymentToken)"
$response = Invoke-WebRequest -Method Post -Uri "$Gateway/api/v1/orders" -ContentType "application/json" `
    -Headers $orderHeaders -Body $orderBody -UseBasicParsing
$receipt = $response.Content | ConvertFrom-Json
Write-Host "HTTP $($response.StatusCode)  orderId=$($receipt.orderId)  correlationId=$($receipt.correlationId)"

if ($Replay) {
    $again = Invoke-WebRequest -Method Post -Uri "$Gateway/api/v1/orders" -ContentType "application/json" `
        -Headers $orderHeaders -Body $orderBody -UseBasicParsing
    $replayed = $again.Content | ConvertFrom-Json
    Write-Host "Replay -> HTTP $($again.StatusCode)  orderId=$($replayed.orderId)  (same order: $($replayed.orderId -eq $receipt.orderId))"
}

# 4. Follow the order until the payment has a definitive outcome
$deadline = (Get-Date).AddSeconds($WaitSeconds)
$last = ""
while ((Get-Date) -lt $deadline) {
    $order = Invoke-RestMethod -Uri "$Gateway/api/v1/orders/$($receipt.orderId)" -Headers $auth
    $state = "status=$($order.status) paymentStatus=$($order.paymentStatus) version=$($order.version)"
    if ($state -ne $last) { Write-Host "  $((Get-Date).ToString('HH:mm:ss'))  $state"; $last = $state }
    if ($order.paymentStatus -in @("AUTHORIZED", "FAILED")) { break }
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "Investigate in Grafana (http://localhost:3000 -> Explore -> Loki):"
Write-Host "  {service=~`".+`"} | json | orderId=`"$($receipt.orderId)`""
