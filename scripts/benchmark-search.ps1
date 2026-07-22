param([string]$BaseUrl = "http://localhost:8080", [int]$Requests = 100, [int]$Warmup = 5)
$ErrorActionPreference = "Stop"
1..$Warmup | ForEach-Object {
  Invoke-RestMethod "$BaseUrl/api/v1/search/vendors?q=photografer&city=Kathmandu&sort=RELEVANCE" | Out-Null
}
$times = 1..$Requests | ForEach-Object {
  $watch = [Diagnostics.Stopwatch]::StartNew()
  Invoke-RestMethod "$BaseUrl/api/v1/search/vendors?q=photografer&city=Kathmandu&sort=RELEVANCE" | Out-Null
  $watch.Stop(); $watch.Elapsed.TotalMilliseconds
}
$ordered = $times | Sort-Object
$p95 = $ordered[[Math]::Min($ordered.Count - 1, [Math]::Ceiling($ordered.Count * 0.95) - 1)]
Write-Output ("search p95: {0:N1} ms across {1} requests" -f $p95, $Requests)
if ($p95 -ge 300) { exit 1 }
