function Get-CloudTicketBaseUrl {
  if ([string]::IsNullOrWhiteSpace($env:CLOUDTICKET_BASE_URL)) { return 'http://127.0.0.1:8080' }
  return $env:CLOUDTICKET_BASE_URL.TrimEnd('/')
}

function Invoke-CloudTicketRequest([string]$method, [string]$uri, [hashtable]$headers = @{}, [object]$body = $null) {
  try {
    $params = @{ Method = $method; Uri = $uri; Headers = $headers; ErrorAction = 'Stop' }
    if ($null -ne $body) { $params.ContentType = 'application/json'; $params.Body = ($body | ConvertTo-Json -Depth 8 -Compress) }
    $response = Invoke-WebRequest @params
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Json = ($response.Content | ConvertFrom-Json) }
  } catch {
    $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $content = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { '{}' }
    return [pscustomobject]@{ Status = $status; Json = ($content | ConvertFrom-Json) }
  }
}

function Assert-CloudTicketStatus($response, [int]$expected, [string]$label) {
  if ($response.Status -ne $expected) { throw "$label expected HTTP $expected, got $($response.Status): $($response.Json | ConvertTo-Json -Compress)" }
}
