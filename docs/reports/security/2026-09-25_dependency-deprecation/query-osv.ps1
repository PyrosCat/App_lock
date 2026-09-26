param(
    [string]$InputPath = '.gradle/dependency-review/resolved-inventory.json',
    [string]$OutputDirectory = '.gradle/dependency-review/osv'
)
$ErrorActionPreference = 'Stop'
$inventory = Get-Content -LiteralPath $InputPath -Raw | ConvertFrom-Json
if (-not $inventory.completedAtUtc) { throw 'Resolution inventory is not complete.' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$coordinates = @($inventory.configurations | ForEach-Object { $_.components } | Select-Object group,artifact,version -Unique | Sort-Object group,artifact,version)
$queries = @($coordinates | ForEach-Object { [ordered]@{package=[ordered]@{ecosystem='Maven';name=($_.group + ':' + $_.artifact)};version=$_.version} })
$scan = [ordered]@{
    startedAtUtc = [DateTime]::UtcNow.ToString('o')
    endpoint = 'https://api.osv.dev/v1/querybatch'
    method = 'Public Maven package/version lookup; no source code or project identity sent'
    inputCount = $queries.Count
    results = @()
    fetchedAdvisoryCount = 0
}
$queries | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'queries.json') -Encoding utf8
for ($offset = 0; $offset -lt $queries.Count; $offset += 100) {
    $last = [Math]::Min($offset + 99, $queries.Count - 1)
    $batch = @($queries[$offset..$last])
    $response = Invoke-RestMethod -Method Post -Uri $scan.endpoint -ContentType 'application/json' -Body (@{queries=$batch} | ConvertTo-Json -Depth 8 -Compress) -TimeoutSec 60
    if (@($response.results).Count -ne $batch.Count) { throw 'OSV response length differs from query count.' }
    $response | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $OutputDirectory ('batch-' + $offset + '.json')) -Encoding utf8
    for ($index = 0; $index -lt $batch.Count; $index++) {
        $query = $batch[$index]
        $result = $response.results[$index]
        $vulnerabilities = @($result.vulns)
        $nextPage = $result.next_page_token
        while ($nextPage) {
            $pageQuery = [ordered]@{package=$query.package;version=$query.version;page_token=$nextPage}
            $page = Invoke-RestMethod -Method Post -Uri $scan.endpoint -ContentType 'application/json' -Body (@{queries=@($pageQuery)} | ConvertTo-Json -Depth 8 -Compress) -TimeoutSec 60
            $vulnerabilities += @($page.results[0].vulns)
            $nextPage = $page.results[0].next_page_token
        }
        $scan.results += [ordered]@{package=$query.package.name;version=$query.version;vulns=@($vulnerabilities | Where-Object { $_ } | Sort-Object id -Unique)}
    }
    Write-Output ('Queried ' + ($last + 1) + '/' + $queries.Count + ' public Maven versions')
}
$advisoryIds = @($scan.results | ForEach-Object { $_.vulns } | ForEach-Object { $_.id } | Sort-Object -Unique)
$advisories = @()
foreach ($advisoryId in $advisoryIds) {
    $advisories += Invoke-RestMethod -Uri ('https://api.osv.dev/v1/vulns/' + [Uri]::EscapeDataString($advisoryId)) -TimeoutSec 60
}
$scan.fetchedAdvisoryCount = $advisories.Count
$scan.completedAtUtc = [DateTime]::UtcNow.ToString('o')
$scan | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'scan-results.json') -Encoding utf8
$advisories | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'advisories.json') -Encoding utf8
[pscustomobject]@{queries=$queries.Count;matchedPackageVersions=@($scan.results | Where-Object { $_.vulns.Count -gt 0 }).Count;uniqueAdvisories=$advisories.Count}
