<#
.SYNOPSIS
Cross-replica acceptance checks for feature 0008 against the running Compose product.
.DESCRIPTION
Start the stack with: docker compose up --build -d --wait
(Rabbit is in the default stack; apps use docker,broker.)
Then run: pwsh -File scripts/test-async-click-ingestion.ps1

Redirects on app1 and reads stats on app2 (shared Postgres + durable ClickLoggedEvent).
#>
[CmdletBinding()]
param(
    [string] $LoadBalancerUrl = 'http://lb'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$workspace = Split-Path -Parent $PSScriptRoot

function Invoke-Compose {
    param([string[]] $Command)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & docker compose @Command 2>&1
    }
    finally { $ErrorActionPreference = $previousPreference }
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose command failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join "`n").Trim()
}

function Assert-Condition {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

function Invoke-Request {
    param(
        [string] $Destination,
        [string] $Path,
        [string] $Method = 'GET',
        [string] $UrlForm = ''
    )
    $source = if ($Destination -eq 'app2') { 'app2' } else { 'app1' }
    $origin = if ($Destination -eq 'lb') { $LoadBalancerUrl.TrimEnd('/') } else { 'http://localhost:8080' }
    $command = @(
        'exec', '-T', $source, 'curl', '--silent', '--show-error', '--max-time', '20',
        '--include', '--request', $Method
    )
    if ($UrlForm) { $command += @('--data-urlencode', "url=$UrlForm") }
    $raw = Invoke-Compose -Command ($command + "$origin$Path")
    $parts = ($raw -replace "`r`n", "`n") -split "`n`n", 2
    $lines = $parts[0] -split "`n"
    $headers = @{}
    foreach ($line in ($lines | Select-Object -Skip 1)) {
        $header = $line -split ':', 2
        if ($header.Length -eq 2) { $headers[$header[0].ToLowerInvariant()] = $header[1].Trim() }
    }
    [pscustomobject] @{
        Status = [int] ($lines[0] -split ' ')[1]
        Headers = $headers
        Body = if ($parts.Length -eq 2) { $parts[1] } else { '' }
    }
}

function Get-TotalClicks {
    param([string] $Destination, [string] $Hash)
    $response = Invoke-Request -Destination $Destination -Path "/api/stats/$Hash"
    Assert-Condition ($response.Status -eq 200) "$Destination stats expected 200, got $($response.Status): $($response.Body)"
    return [int] ($response.Body | ConvertFrom-Json).totalClicks
}

function Wait-TotalClicks {
    param([string] $Destination, [string] $Hash, [int] $Expected, [int] $TimeoutSeconds = 30)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $last = -1
    while ([DateTime]::UtcNow -lt $deadline) {
        $last = Get-TotalClicks -Destination $Destination -Hash $Hash
        if ($last -eq $Expected) { return }
        Start-Sleep -Milliseconds 200
    }
    throw "$Destination stats for $Hash did not reach totalClicks=$Expected (last=$last)"
}

Push-Location -LiteralPath $workspace
try {
    $running = (Invoke-Compose -Command @('ps', '--status', 'running', '--services')) -split "`n"
    foreach ($service in @('db', 'redis', 'rabbit', 'app1', 'app2', 'lb')) {
        Assert-Condition ($service -in $running) "Start the Compose stack first: $service is not running"
    }
    $null = Invoke-Compose -Command @('exec', '-T', 'rabbit', 'rabbitmq-diagnostics', '-q', 'ping')
    foreach ($destination in @('app1', 'app2', 'lb')) {
        $ready = Invoke-Request -Destination $destination -Path '/actuator/health/readiness'
        Assert-Condition ($ready.Status -eq 200) "$destination is not ready"
    }

    $target = 'https://example.com/async-click/' + [Guid]::NewGuid().ToString('N')
    $created = Invoke-Request -Destination 'lb' -Path '/api/link' -Method 'POST' -UrlForm $target
    Assert-Condition ($created.Status -eq 201) "Create expected 201, got $($created.Status): $($created.Body)"
    $hash = [string] ($created.Body | ConvertFrom-Json).hash
    Assert-Condition ($hash -cmatch '^[A-Za-z0-9]+$') "Unexpected hash: $hash"
    Write-Host "Created hash=$hash"

    Wait-TotalClicks -Destination 'app1' -Hash $hash -Expected 0
    Wait-TotalClicks -Destination 'app2' -Hash $hash -Expected 0

    $redirect = Invoke-Request -Destination 'app1' -Path "/$hash"
    Assert-Condition ($redirect.Status -eq 307) "app1 redirect expected 307, got $($redirect.Status)"
    Assert-Condition ($redirect.Headers['location'] -ceq $target) "app1 returned the wrong Location"

    # Stats on the other replica prove shared ingestion (broker + Postgres).
    Wait-TotalClicks -Destination 'app2' -Hash $hash -Expected 1
    Assert-Condition ((Get-TotalClicks -Destination 'app1' -Hash $hash) -eq 1) 'app1 stats must also show totalClicks=1'
    Assert-Condition ((Get-TotalClicks -Destination 'lb' -Hash $hash) -eq 1) 'lb stats must show totalClicks=1'

    $clickRows = Invoke-Compose -Command @(
        'exec', '-T', 'db', 'psql', '-U', 'urlshortener', '-d', 'urlshortener',
        '-v', 'ON_ERROR_STOP=1', '-At', '-c', "SELECT count(*) FROM click WHERE hash = '$hash'"
    )
    Assert-Condition ($clickRows -eq '1') "Expected one click row for $hash, got $clickRows"

    $eventIds = Invoke-Compose -Command @(
        'exec', '-T', 'db', 'psql', '-U', 'urlshortener', '-d', 'urlshortener',
        '-v', 'ON_ERROR_STOP=1', '-At', '-c', "SELECT count(DISTINCT event_id) FROM click WHERE hash = '$hash'"
    )
    Assert-Condition ($eventIds -eq '1') "Expected one distinct event_id for $hash, got $eventIds"

    Write-Host 'PASS feature 0008 cross-replica: redirect on app1, totalClicks=1 on app2/app1/lb, one event_id'
}
finally {
    Pop-Location
}
