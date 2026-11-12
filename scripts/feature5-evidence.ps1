<#
.SYNOPSIS
  Runs the three scenarios of Feature 5 (Spec 5, 6.6) against the running stack and records the evidence of Spec 6.7
  in docs/evidence/feature5/: Loki query results, Jaeger traces with their span links, payment states and fired alerts.

  1. trace-order        : full order (scripts/dev-flow.ps1); every log line of the workflow found in Loki by orderId,
                          every trace found in Jaeger by eatsnow.correlation_id, span links of later actions.
  2. payment-ambiguous  : a lost provider response (UNKNOWN, reconciled to AUTHORIZED) next to a definitive refusal
                          (FAILED), investigated from the Payments log lines.
  3. messaging-alerts   : RabbitMQ stopped while an order is accepted (OutboxStalled fires, the outbox drains after the
                          restart) and a poison command quarantined in a DLQ (MessagesInDLQ fires; the DLQ log line keeps
                          the order's correlation context). The DLQ is purged at the end.

  Uses only synthetic users and payment tokens. Scenario 3 stops RabbitMQ for about three minutes.

.EXAMPLE
  ./scripts/feature5-evidence.ps1
  ./scripts/feature5-evidence.ps1 -Scenario payment-ambiguous
#>
param(
    [ValidateSet("all", "trace-order", "payment-ambiguous", "messaging-alerts")][string]$Scenario = "all",
    [string]$Gateway = "http://localhost:8090",
    [string]$Password = "password123"
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $root "docs/evidence/feature5"
New-Item -ItemType Directory -Force $outDir | Out-Null

$Loki = "http://localhost:3100"
$Jaeger = "http://localhost:16686"
$Prometheus = "http://localhost:9090"
$RabbitApi = "http://localhost:15672/api"
$RabbitAuth = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("eatsnow_admin:admin_dev_pw")) }

# Seed data: Elena Bistro and its Pasta Carbonara (Catalog V1/V2)
$BistroId = "00000000-0000-4000-8000-000000000050"
$PastaId  = "00000000-0000-4000-8000-000000000051"

. "$PSScriptRoot/oidc-login.ps1"

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }

function Login($email) {
    $r = Get-EatsNowToken -Gateway $Gateway -Email $email -Password $Password
    return @{ Authorization = "Bearer $($r.access_token)" }
}

function Api($method, $path, $auth, $body = $null) {
    $headers = @{} + $auth
    $headers["X-Correlation-ID"] = [guid]::NewGuid().ToString()
    if ($method -ne "Get") { $headers["Idempotency-Key"] = [guid]::NewGuid().ToString() }
    $request = @{ Method = $method; Uri = "$Gateway$path"; Headers = $headers; ContentType = "application/json" }
    if ($body -ne $null) { $request["Body"] = ($body | ConvertTo-Json -Depth 6 -Compress) }
    return Invoke-RestMethod @request
}

function WaitUntil($description, [scriptblock]$check, $seconds = 90) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $result = & $check
        if ($result) { return $result }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for: $description"
}

function Invoke-Compose([string[]]$arguments) {
    $ErrorActionPreference = "Continue"
    & docker compose -f "$root/docker-compose.yml" @arguments 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($arguments -join ' ') failed" }
}

# Quote + order; returns the 202 receipt with the HTTP status and the X-Trace-Id of the response.
function PlaceOrder($auth, $paymentToken) {
    $quote = Api Post "/api/v1/quotes" $auth @{ restaurantId = $BistroId; items = @(@{ menuItemId = $PastaId; quantity = 1 }) }
    $response = Invoke-WebRequest -Method Post -Uri "$Gateway/api/v1/orders" -UseBasicParsing -ContentType "application/json" `
        -Headers ($auth + @{ "Idempotency-Key" = [guid]::NewGuid().ToString(); "X-Correlation-ID" = [guid]::NewGuid().ToString() }) `
        -Body (@{
            quoteToken = $quote.quoteToken
            deliveryAddress = @{ line1 = "123 Example Street"; city = "Medellin" }
            paymentMethodToken = $paymentToken
        } | ConvertTo-Json -Depth 6 -Compress)
    $receipt = $response.Content | ConvertFrom-Json
    $receipt | Add-Member -NotePropertyName httpStatus -NotePropertyValue ([int]$response.StatusCode)
    $receipt | Add-Member -NotePropertyName traceId -NotePropertyValue $response.Headers["X-Trace-Id"]
    return $receipt
}

# --- Telemetry backends -------------------------------------------------------------------------------------------

function UnixNanos([datetime]$time) {
    return ([DateTimeOffset]$time.ToUniversalTime()).ToUnixTimeMilliseconds().ToString() + "000000"
}

# Loki query_range in time order; returns the parsed JSON log lines.
function LokiLines($query, [datetime]$since) {
    $uri = "$Loki/loki/api/v1/query_range?query=$([uri]::EscapeDataString($query))&start=$(UnixNanos $since)" +
        "&end=$(UnixNanos (Get-Date).AddMinutes(1))&limit=1000&direction=forward"
    $result = Invoke-RestMethod $uri
    $lines = foreach ($stream in $result.data.result) { foreach ($value in $stream.values) { $value[1] | ConvertFrom-Json } }
    return @($lines | Sort-Object timestamp)
}

# Waits until Loki has ingested the log lines matched by $check (Alloy ships them every few seconds).
function WaitForLogs($query, [datetime]$since, [scriptblock]$complete) {
    # $complete, not $check: scriptblocks resolve variables dynamically and WaitUntil has its own $check
    return WaitUntil "log lines in Loki" { $lines = LokiLines $query $since; if (& $complete $lines) { , $lines } } 60
}

function LogTable($lines, [string[]]$extra = @()) {
    $columns = @("timestamp", "service", "operation", "outcome") + $extra + @("traceId")
    $rows = @(("| " + ($columns -join " | ") + " |"), ("|" + ("---|" * $columns.Count)))
    foreach ($line in $lines) {
        $rows += "| " + (($columns | ForEach-Object { $v = $line.$_; if ($null -eq $v) { "" } else { "$v" } }) -join " | ") + " |"
    }
    return $rows -join "`n"
}

# Jaeger API v3: every span of the services that tag the workflow, grouped by trace.
function JaegerTraces($correlationId, [datetime]$since) {
    $traces = @{}
    foreach ($service in "orders", "restaurants", "delivery") {
        $query = "query.service_name=$service" +
            "&query.attributes=$([uri]::EscapeDataString((@{ 'eatsnow.correlation_id' = $correlationId } | ConvertTo-Json -Compress)))" +
            "&query.start_time_min=$($since.ToUniversalTime().AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ssZ'))" +
            "&query.start_time_max=$((Get-Date).ToUniversalTime().AddMinutes(1).ToString('yyyy-MM-ddTHH:mm:ssZ'))" +
            "&query.search_depth=50"
        try { $result = Invoke-RestMethod "$Jaeger/api/v3/traces?$query" } catch { continue }   # 404: no trace for this service
        foreach ($resourceSpans in $result.result.resourceSpans) {
            $serviceName = ($resourceSpans.resource.attributes | Where-Object key -eq "service.name").value.stringValue
            foreach ($span in $resourceSpans.scopeSpans.spans) {
                if (-not $traces.ContainsKey($span.traceId)) { $traces[$span.traceId] = @{} }
                $traces[$span.traceId][$span.spanId] = [pscustomobject]@{ Service = $serviceName; Span = $span }
            }
        }
    }
    return $traces
}

function TraceTable($traces) {
    $rows = @("| Trace | Started by | Spans | Services | Span links |", "|---|---|---|---|---|")
    $ordered = $traces.GetEnumerator() | Sort-Object { ($_.Value.Values | ForEach-Object { [decimal]$_.Span.startTimeUnixNano } | Measure-Object -Minimum).Minimum }
    foreach ($trace in $ordered) {
        $spans = @($trace.Value.Values)
        $first = $spans | Sort-Object { [decimal]$_.Span.startTimeUnixNano } | Select-Object -First 1
        $server = $spans | Where-Object { $_.Service -ne "gateway" -and $_.Span.name -like "http *" } | Select-Object -First 1
        $label = if ($server) { "$($server.Service): $($server.Span.name)" } else { "$($first.Service): $($first.Span.name)" }
        $services = ($spans.Service | Sort-Object -Unique) -join ", "
        $links = @($spans | ForEach-Object { foreach ($l in $_.Span.links) { "-> ``$($l.traceId)``" } }) -join "<br>"
        $rows += "| ``$($trace.Key)`` | $label | $($spans.Count) | $services | $links |"
    }
    return $rows -join "`n"
}

function SpanNames($traces, $traceId) {
    return ($traces[$traceId].Values | ForEach-Object { "$($_.Service): $($_.Span.name)" } | Sort-Object -Unique) -join "`n"
}

function AlertState($name) {
    $alerts = (Invoke-RestMethod "$Prometheus/api/v1/alerts").data.alerts | Where-Object { $_.labels.alertname -eq $name }
    return @($alerts)
}

function PromValue($query) {
    $r = (Invoke-RestMethod "$Prometheus/api/v1/query?query=$([uri]::EscapeDataString($query))").data.result
    return @($r | ForEach-Object { [pscustomobject]@{ Labels = ($_.metric.PSObject.Properties | Where-Object Name -notin "__name__", "instance", "job" | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ", "; Value = $_.value[1] } })
}

function Save($name, [string[]]$sections) {
    $path = Join-Path $outDir "$name.md"
    [IO.File]::WriteAllText($path, ($sections -join "`n`n") + "`n", (New-Object Text.UTF8Encoding($false)))
    Write-Host "   evidence written to $path" -ForegroundColor Green
}

function Header($title, $spec) {
    $commit = (git -C $root rev-parse --short HEAD)
    $dirty = if (git -C $root status --porcelain) { " (with uncommitted changes)" } else { "" }
    return "# $title`n`n$spec`n`nRecorded $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm')) UTC on commit ``$commit``$dirty by ``scripts/feature5-evidence.ps1``."
}

# --- Scenario 1: trace an order across the participating services -------------------------------------------------

function Invoke-TraceOrder {
    Step "Scenario 1: trace an order across Payments, Restaurant Service and Delivery"
    $since = Get-Date
    $flow = & "$PSScriptRoot/dev-flow.ps1" -Gateway $Gateway -Password $Password 6>$null | Select-Object -Last 1
    Write-Host "   order $($flow.OrderId) DELIVERED, correlationId $($flow.CorrelationId)"

    $query = "{service=~`".+`"} | json | orderId=`"$($flow.OrderId)`""
    $lines = WaitForLogs $query $since { param($l) @($l | Where-Object { $_.operation -eq "consume OrderStatusChanged" -and $_.service -eq "notifications" }).Count -ge 6 }
    $services = $lines.service | Sort-Object -Unique
    $correlations = $lines.correlationId | Where-Object { $_ } | Sort-Object -Unique
    # The collector exports in batches: wait for the four restaurant and driver actions, each with its span link.
    $traces = WaitUntil "restaurant and driver traces in Jaeger" {
        $t = JaegerTraces $flow.CorrelationId $since
        if (@($t.Values | ForEach-Object { $_.Values } | Where-Object { $_.Span.links }).Count -ge 4) { $t }
    } 60
    $linked = @($traces.Values | ForEach-Object { $_.Values } | Where-Object { $_.Span.links })

    Save "1-trace-order" @(
        (Header "Feature 5 - Trace an order across participating services" "Spec 5, Feature 5, scenario *Trace an order across participating services*; procedure of Spec 6.6 steps 1-3."),
        "## Steps`n`n1. ``scripts/dev-flow.ps1``: Alex orders from Elena Bistro (``tok_ok``), Elena accepts and starts preparation, Sam picks up and completes the delivery.`n2. Loki: ``$query```n3. Jaeger: spans tagged ``eatsnow.correlation_id=$($flow.CorrelationId)`` in the services orders, restaurants and delivery.",
        "## Result`n`n| Check | Value |`n|---|---|`n| orderId | ``$($flow.OrderId)`` |`n| correlationId | ``$($flow.CorrelationId)`` |`n| X-Trace-Id of POST /orders | ``$($flow.TraceId)`` |`n| Services with log lines for the order | $($services -join ', ') |`n| Distinct correlationId values in those lines | $(@($correlations).Count) (``$($correlations -join ', ')``) |`n| Log lines | $(@($lines).Count) |`n| Traces found by correlation id | $($traces.Count) |`n| Spans with a link to an earlier workflow trace | $($linked.Count) |",
        "## Traces (Jaeger)`n`nThe order creation trace contains the HTTP spans and every ``publish``/``consume`` span until the restaurant inbox. Restaurant and driver actions start their own trace (Spec 6.3) and link back to the trace that the service received for the order.`n`n$(TraceTable $traces)",
        "### Spans of the creation trace ``$($flow.TraceId)```n`n``````text`n$(SpanNames $traces $flow.TraceId)`n``````",
        "## Log lines (Loki)`n`n$(LogTable $lines @('messageType', 'orderStatus', 'paymentStatus', 'correlationId'))"
    )
}

# --- Scenario 2: investigate an ambiguous payment outcome ---------------------------------------------------------

function Invoke-PaymentAmbiguous {
    Step "Scenario 2: investigate an ambiguous payment outcome"
    $since = Get-Date
    $customer = Login "alex@example.test"
    $lost = PlaceOrder $customer "tok_lost_response"
    $declined = PlaceOrder $customer "tok_declined"
    Write-Host "   lost response: $($lost.orderId)   declined: $($declined.orderId)"

    WaitUntil "declined payment FAILED" { (Api Get "/api/v1/orders/$($declined.orderId)" $customer).paymentStatus -eq "FAILED" } | Out-Null
    $unknown = WaitUntil "lost response UNKNOWN" { $o = Api Get "/api/v1/orders/$($lost.orderId)" $customer; if ($o.paymentStatus -eq "UNKNOWN") { $o } }
    Write-Host "   lost response is UNKNOWN; waiting for the reconciliation"
    $reconciled = WaitUntil "lost response reconciled to AUTHORIZED" { $o = Api Get "/api/v1/orders/$($lost.orderId)" $customer; if ($o.paymentStatus -eq "AUTHORIZED") { $o } } 180
    # Highest age Prometheus scraped while the charge was UNKNOWN (scrape every 15 s)
    $unresolved = PromValue "max_over_time(eatsnow_payment_unresolved_oldest_seconds{operation=`"CHARGE`"}[5m])"
    $declinedOrder = Api Get "/api/v1/orders/$($declined.orderId)" $customer

    $lostQuery = "{service=`"payments`"} | json | orderId=`"$($lost.orderId)`""
    $declinedQuery = "{service=`"payments`"} | json | orderId=`"$($declined.orderId)`""
    $lostLines = WaitForLogs $lostQuery $since { param($l) @($l | Where-Object { $_.paymentStatus -eq "AUTHORIZED" }).Count -ge 1 }
    $declinedLines = WaitForLogs $declinedQuery $since { param($l) @($l | Where-Object { $_.paymentStatus -eq "FAILED" }).Count -ge 1 }
    $ordersQuery = "{service=`"orders`"} | json | orderId=`"$($lost.orderId)`""
    $ordersLines = WaitForLogs $ordersQuery $since { param($l) @($l | Where-Object { $_.paymentStatus -eq "AUTHORIZED" }).Count -ge 1 }

    Save "2-payment-ambiguous" @(
        (Header "Feature 5 - Investigate an ambiguous payment outcome" "Spec 5, Feature 5, scenario *Investigate an ambiguous payment outcome*; procedure of Spec 6.6 step 4."),
        "## Steps`n`n1. Alex places two orders: one paid with ``tok_lost_response`` (the provider charges but its answer is lost) and one with ``tok_declined`` (definitive refusal).`n2. ``GET /api/v1/orders/{id}`` is polled for both orders.`n3. Loki: ``$lostQuery`` and ``$declinedQuery``.",
        "## Result`n`n| Order | Payment token | POST /orders | paymentStatus seen | Final order status | Final paymentStatus |`n|---|---|---|---|---|---|`n| ``$($lost.orderId)`` | ``tok_lost_response`` | $($lost.httpStatus) | UNKNOWN, then AUTHORIZED | $($reconciled.status) | $($reconciled.paymentStatus) |`n| ``$($declined.orderId)`` | ``tok_declined`` | $($declined.httpStatus) | FAILED | $($declinedOrder.status) | $($declinedOrder.paymentStatus) |",
        "While the lost response was ``UNKNOWN``, the order stayed ``$($unknown.status)``: an unknown outcome is not treated as a failed charge, and the customer was not told the payment failed. Reconciliation then confirmed the charge (``AUTHORIZED``). The declined card is a definitive ``FAILED``, so no charge exists.",
        "Highest ``eatsnow_payment_unresolved_oldest_seconds`` scraped while the charge was UNKNOWN (``max_over_time`` over 5 minutes; the ``PaymentUnresolved`` alert fires above 300 s):`n`n| Labels | Oldest unresolved (s) |`n|---|---|`n$((($unresolved | ForEach-Object { "| $($_.Labels) | $($_.Value) |" }) -join "`n"))",
        "## Payments log lines: lost response (UNKNOWN, then reconciled)`n`n$(LogTable $lostLines @('paymentStatus', 'errorCode'))",
        "## Orders log lines: lost response`n`n$(LogTable $ordersLines @('messageType', 'orderStatus', 'paymentStatus'))",
        "## Payments log lines: declined card (definitive FAILED)`n`n$(LogTable $declinedLines @('paymentStatus', 'errorCode'))"
    )
}

# --- Scenario 3: detect failed or stalled message processing ------------------------------------------------------

function Invoke-MessagingAlerts {
    Step "Scenario 3a: stop RabbitMQ, accept an order, wait for OutboxStalled"
    $since = Get-Date
    $customer = Login "alex@example.test"
    Invoke-Compose @("stop", "rabbitmq")
    try {
        $order = PlaceOrder $customer "tok_ok"
        Write-Host "   POST /orders -> $($order.httpStatus) with the broker down, order $($order.orderId)"
        $firing = WaitUntil "OutboxStalled firing" { $a = AlertState "OutboxStalled" | Where-Object state -eq "firing"; if ($a) { , $a } } 300
        $outbox = PromValue "eatsnow_outbox_oldest_pending_seconds > 0"
        Write-Host "   OutboxStalled is firing"
    } finally {
        Step "Scenario 3a: restart RabbitMQ; the relay publishes the pending entries"
        Invoke-Compose @("start", "rabbitmq")
    }
    $recovered = WaitUntil "order payment AUTHORIZED after the broker restart" { $o = Api Get "/api/v1/orders/$($order.orderId)" $customer; if ($o.paymentStatus -eq "AUTHORIZED") { $o } } 240
    $drained = WaitUntil "outbox drained" { $p = PromValue "sum(eatsnow_outbox_pending)"; if ($p -and [double]@($p)[0].Value -eq 0) { $p } } 120
    $relayQuery = "{service=`"orders`"} | json | operation=~`"publish .*`" | outcome!=`"SUCCESS`""
    $relayLines = @(WaitForLogs $relayQuery $since { param($l) @($l).Count -ge 1 } | Select-Object -First 10)

    Step "Scenario 3b: a poison command for the same order reaches restaurants.commands.dlq"
    $dlqSince = Get-Date
    $envelope = [ordered]@{
        schemaVersion = 1; messageId = [guid]::NewGuid().ToString(); messageType = "RequestRestaurantAcceptance"
        occurredAt = (Get-Date).ToUniversalTime().ToString("o"); producer = "orders"
        orderId = $order.orderId; correlationId = $order.correlationId; causationId = $null
        aggregateId = $order.orderId; aggregateVersion = 1; traceparent = $null
        payload = @{ items = @() }                       # no restaurantId and no items: retrying cannot help
    }
    $publish = @{
        properties = @{ content_type = "application/json"; delivery_mode = 2 }
        routing_key = "RequestRestaurantAcceptance"; payload = ($envelope | ConvertTo-Json -Depth 5 -Compress); payload_encoding = "string"
    }
    $routed = Invoke-RestMethod -Method Post -Uri "$RabbitApi/exchanges/%2F/eatsnow.commands.v1/publish" -Headers $RabbitAuth `
        -ContentType "application/json" -Body ($publish | ConvertTo-Json -Compress)
    Write-Host "   poison command published (routed=$($routed.routed))"
    $dlqAlert = WaitUntil "MessagesInDLQ firing" { $a = AlertState "MessagesInDLQ" | Where-Object state -eq "firing"; if ($a) { , $a } } 120
    $dlqDepth = PromValue "rabbitmq_queue_messages_ready{queue=~`".*[.]dlq`"} > 0"
    $dlqQuery = "{service=`"restaurants`"} | json | orderId=`"$($order.orderId)`" | outcome=`"FAILURE`""
    $dlqLines = WaitForLogs $dlqQuery $dlqSince { param($l) @($l).Count -ge 1 }
    Invoke-RestMethod -Method Delete -Uri "$RabbitApi/queues/%2F/restaurants.commands.dlq/contents" -Headers $RabbitAuth | Out-Null
    Write-Host "   DLQ purged after recording the evidence"

    $alertRow = { param($a) "| $($a.labels.alertname) | $($a.labels.severity) | $($a.labels.service)$($a.labels.queue) | $($a.state) | $($a.activeAt) | $($a.annotations.summary) |" }
    Save "3-messaging-alerts" @(
        (Header "Feature 5 - Detect failed or stalled message processing" "Spec 5, Feature 5, scenario *Detect failed or stalled message processing*; alerts of Spec 6.4; procedure of Spec 6.6 step 5."),
        "## Steps`n`n1. ``docker compose stop rabbitmq``.`n2. Alex places an order (``tok_ok``): the Orders Service accepts it and keeps the ``ChargePayment`` command in its outbox.`n3. Wait until Prometheus fires ``OutboxStalled`` (oldest pending entry over 60 s for 1 minute).`n4. ``docker compose start rabbitmq``: the relay publishes the pending entries and the order continues.`n5. Publish a ``RequestRestaurantAcceptance`` command for the same order without a restaurant or items (RabbitMQ management API). The Restaurant Service quarantines it in ``restaurants.commands.dlq`` without retrying.`n6. Wait until Prometheus fires ``MessagesInDLQ``, find the order's correlation context in Loki, then purge the DLQ.",
        "## Result`n`n| Check | Value |`n|---|---|`n| POST /orders with the broker down | $($order.httpStatus) Accepted, orderId ``$($order.orderId)``, correlationId ``$($order.correlationId)`` |`n| Order after the broker restart | status $($recovered.status), paymentStatus $($recovered.paymentStatus) |`n| Pending outbox entries after recovery | $(@($drained)[0].Value) |`n| Poison command routed | $($routed.routed) |",
        "## Fired alerts (Prometheus)`n`n| Alert | Severity | Service / queue | State | Active since | Summary |`n|---|---|---|---|---|---|`n$((@($firing) + @($dlqAlert) | ForEach-Object { & $alertRow $_ }) -join "`n")",
        "Outbox age while the broker was down:`n`n| Labels | Oldest pending (s) |`n|---|---|`n$((($outbox | ForEach-Object { "| $($_.Labels) | $($_.Value) |" }) -join "`n"))",
        "DLQ depth when ``MessagesInDLQ`` fired:`n`n| Labels | Messages ready |`n|---|---|`n$((($dlqDepth | ForEach-Object { "| $($_.Labels) | $($_.Value) |" }) -join "`n"))",
        "## Relay retries while the broker was down (Loki)`n`n``$relayQuery`` (first 10 lines)`n`n$(LogTable $relayLines @('messageId', 'retryInSeconds'))",
        "## DLQ log line with the order's correlation context (Loki)`n`n``$dlqQuery```n`n$(LogTable $dlqLines @('messageType', 'errorCode', 'correlationId', 'messageId'))"
    )
}

if ($Scenario -in "all", "trace-order") { Invoke-TraceOrder }
if ($Scenario -in "all", "payment-ambiguous") { Invoke-PaymentAmbiguous }
if ($Scenario -in "all", "messaging-alerts") { Invoke-MessagingAlerts }
Step "Done: docs/evidence/feature5/"
