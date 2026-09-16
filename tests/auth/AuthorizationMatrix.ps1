. "$PSScriptRoot\..\TestSupport.ps1"
$base = Get-CloudTicketBaseUrl
Assert-CloudTicketStatus (Invoke-CloudTicketRequest GET "$base/api/admin/auth/users") 401 'anonymous admin access'
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); $email = "it-matrix-$stamp@example.com"; $password = 'IntegrationPass123!'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest POST "$base/api/auth/register" @{} @{ email = $email; password = $password; nickname = 'matrix' }) 200 'register'
$login = Invoke-CloudTicketRequest POST "$base/api/auth/login" @{} @{ identifier = $email; password = $password }; Assert-CloudTicketStatus $login 200 'login'
Assert-CloudTicketStatus (Invoke-CloudTicketRequest GET "$base/api/admin/auth/users" @{ Authorization = "Bearer $($login.Json.data.accessToken)" }) 403 'ordinary user admin access'
Write-Output 'PASS AuthorizationMatrix anonymous=401 ordinary-user=403'
