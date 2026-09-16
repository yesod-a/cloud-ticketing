. "$PSScriptRoot\..\TestSupport.ps1"
$base = Get-CloudTicketBaseUrl; $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); $email = "it-e2e-$stamp@example.com"; $password = 'IntegrationPass123!'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/register" @{} @{ email = $email; password = $password; nickname = 'e2e' }) 200 'register'
$login = Invoke-CloudTicketRequest POST "$base/api/auth/login" @{} @{ identifier = $email; password = $password }; Assert-CloudTicketStatus $login 200 'login'
$token = $login.Json.data.accessToken; $directory = Invoke-CloudTicketRequest GET "$base/api/activities"; Assert-CloudTicketStatus $directory 200 'activity directory'
if ($directory.Json.data.Count -lt 2) { throw 'expected two published activities' }
$activity = $directory.Json.data[0]; $detail = Invoke-CloudTicketRequest GET "$base/api/activities/$($activity.id)"; Assert-CloudTicketStatus $detail 200 'activity detail'
$session = $detail.Json.data.sessions[0]; $seats = Invoke-CloudTicketRequest GET "$base/api/sessions/$($session.id)/seats"; Assert-CloudTicketStatus $seats 200 'seat list'
$seat = $seats.Json.data | Where-Object { $_.status -eq 'AVAILABLE' } | Select-Object -First 1
if ($null -eq $seat) { throw 'no available database-backed seat' }
$headers = @{ Authorization = "Bearer $token"; 'Idempotency-Key' = "it-$stamp" }
$order = Invoke-CloudTicketRequest POST "$base/api/orders" $headers @{ sessionId = $session.id; seatIds = $seat.id; idempotencyKey = "it-$stamp" }; Assert-CloudTicketStatus $order 200 'order create'
$mine = Invoke-CloudTicketRequest GET "$base/api/orders/me?page=0&size=20" @{ Authorization = "Bearer $token" }; Assert-CloudTicketStatus $mine 200 'owner order list'
if ($mine.Json.items.Count -lt 1) { throw 'created order missing from owner list' }
Write-Output "PASS MultiActivityOrderFlow activities=$($directory.Json.data.Count) order=$($order.Json.id)"
