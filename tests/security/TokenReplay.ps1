. "$PSScriptRoot\..\TestSupport.ps1"
$base = Get-CloudTicketBaseUrl; $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); $email = "it-replay-$stamp@example.com"; $password = 'IntegrationPass123!'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/register" @{} @{ email = $email; password = $password; nickname = 'replay' }) 200 'register'
$login = Invoke-CloudTicketRequest POST "$base/api/auth/login" @{} @{ identifier = $email; password = $password }; Assert-CloudTicketStatus $login 200 'login'
$first = Invoke-CloudTicketRequest POST "$base/api/auth/refresh" @{} @{ refreshToken = $login.Json.data.refreshToken }; Assert-CloudTicketStatus $first 200 'first refresh'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/refresh" @{} @{ refreshToken = $login.Json.data.refreshToken }) 401 'original replay'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/refresh" @{} @{ refreshToken = $first.Json.data.refreshToken }) 401 'descendant replay'
Write-Output 'PASS TokenReplay'
