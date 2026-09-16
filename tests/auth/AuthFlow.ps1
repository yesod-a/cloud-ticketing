. "$PSScriptRoot\..\TestSupport.ps1"
$base = Get-CloudTicketBaseUrl
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "it-auth-$stamp@example.com"
$password = 'IntegrationPass123!'

$registered = Invoke-CloudTicketRequest POST "$base/api/auth/register" @{} @{ email = $email; password = $password; nickname = 'integration' }
Assert-CloudTicketStatus $registered 200 'register'
$login = Invoke-CloudTicketRequest POST "$base/api/auth/login" @{} @{ identifier = $email; password = $password }
Assert-CloudTicketStatus $login 200 'login'
$access = $login.Json.data.accessToken; $refresh = $login.Json.data.refreshToken
if ([string]::IsNullOrWhiteSpace($access) -or [string]::IsNullOrWhiteSpace($refresh)) { throw 'login did not return both tokens' }
Assert-CloudTicketStatus (Invoke-CloudTicketRequest GET "$base/api/auth/me" @{ Authorization = "Bearer $access" }) 200 'me'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/refresh" @{} @{ refreshToken = $refresh }) 200 'refresh rotation'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/refresh" @{} @{ refreshToken = $refresh }) 401 'refresh replay rejection'
Write-Output "PASS AuthFlow ($email)"
