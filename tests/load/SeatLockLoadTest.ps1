. "$PSScriptRoot\..\TestSupport.ps1"
$base = Get-CloudTicketBaseUrl
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "it-load-$stamp@example.com"
$password = 'IntegrationPass123!'

Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/register" @{} @{ email = $email; password = $password; nickname = 'seat-load' }) 200 'register'
$login = Invoke-CloudTicketRequest POST "$base/api/auth/login" @{} @{ identifier = $email; password = $password }
Assert-CloudTicketStatus $login 200 'login'
$token = $login.Json.data.accessToken
$directory = Invoke-CloudTicketRequest GET "$base/api/activities"
Assert-CloudTicketStatus $directory 200 'activity directory'
$detail = Invoke-CloudTicketRequest GET "$base/api/activities/$($directory.Json.data[0].id)"
Assert-CloudTicketStatus $detail 200 'activity detail'
$session = $detail.Json.data.sessions[0]
$seats = Invoke-CloudTicketRequest GET "$base/api/sessions/$($session.id)/seats"
Assert-CloudTicketStatus $seats 200 'seat list'
$seat = $seats.Json.data | Where-Object { $_.status -eq 'AVAILABLE' } | Select-Object -First 1
if ($null -eq $seat) { throw 'no available database-backed seat' }

$sessionId = $session.id
$seatId = $seat.id
$results = 1..100 | ForEach-Object -Parallel {
  $started = [Diagnostics.Stopwatch]::StartNew()
  $key = "load-$using:stamp-$_"
  $payload = @{ sessionId = $using:sessionId; seatIds = $using:seatId; idempotencyKey = $key }
  try {
    $response = Invoke-WebRequest -Method POST -Uri "$using:base/api/orders" -Headers @{ Authorization = "Bearer $using:token"; 'Idempotency-Key' = $key } -ContentType 'application/json' -Body ($payload | ConvertTo-Json -Compress) -ErrorAction Stop
    $json = $response.Content | ConvertFrom-Json
    [pscustomobject]@{ Status = [int]$response.StatusCode; OrderId = [string]$json.id; ElapsedMs = $started.Elapsed.TotalMilliseconds }
  } catch {
    $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    [pscustomobject]@{ Status = $status; OrderId = ''; ElapsedMs = $started.Elapsed.TotalMilliseconds }
  }
} -ThrottleLimit 100

$successes = @($results | Where-Object Status -eq 200)
$conflicts = @($results | Where-Object Status -eq 409)
if ($successes.Count -ne 1) { throw "expected exactly one successful lock, got $($successes.Count)" }
if ($conflicts.Count -ne 99) { throw "expected 99 conflicts, got $($conflicts.Count)" }
$ordered = @($results | Sort-Object ElapsedMs)
$p50 = [Math]::Round($ordered[[Math]::Floor(($ordered.Count - 1) * 0.50)].ElapsedMs, 2)
$p95 = [Math]::Round($ordered[[Math]::Floor(($ordered.Count - 1) * 0.95)].ElapsedMs, 2)
$cancel = Invoke-CloudTicketRequest POST "$base/api/orders/$($successes[0].OrderId)/cancel" @{ Authorization = "Bearer $token" }
Assert-CloudTicketStatus $cancel 200 'cleanup winner order'
Write-Output "PASS SeatLockLoadTest attempts=100 success=$($successes.Count) conflicts=$($conflicts.Count) p50Ms=$p50 p95Ms=$p95 seat=$($seat.id)"
