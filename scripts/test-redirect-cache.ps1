<#
.SYNOPSIS
Acceptance checks for feature 0006 against the running Compose product.
.DESCRIPTION
Start the stack with: docker compose up --build -d --wait
Then run: pwsh -File scripts/test-redirect-cache.ps1

Uses the Docker CLI and curl already installed in the app images. Set
URLSHORTENER_ADMIN_TOKEN to the token used by the apps. The default matches the
development Compose token. Only generated test hashes are deleted. RabbitMQ is
temporarily stopped and restarted even when a check fails. Redirect assertions
never poll or wait for expiry. Each phase checks both replicas and the LB.
#>
[CmdletBinding()]
param(
    [string] $AdminToken = $(if ($env:URLSHORTENER_ADMIN_TOKEN) { $env:URLSHORTENER_ADMIN_TOKEN } else { 'test-admin-token' }),
    [string] $LoadBalancerUrl = 'http://lb',
    [int] $MinimumTtlSeconds = 300
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$workspace = Split-Path -Parent $PSScriptRoot
$cachePrefix = 'urlshortener:redirect:'

function Invoke-Compose {
    param([string[]] $Command)
    # Windows PowerShell wraps native stderr as errors, even for Compose progress.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & docker compose @Command 2>&1
    }
    finally { $ErrorActionPreference = $previousPreference }
    if ($LASTEXITCODE -ne 0) {
        # Do not print argv: curl headers include the admin token.
        throw "Docker Compose command failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join "`n").Trim()
}

function Invoke-Sql {
    param([string] $Statement)
    Invoke-Compose -Command @(
        'exec', '-T', 'db', 'psql', '-U', 'urlshortener', '-d', 'urlshortener',
        '-v', 'ON_ERROR_STOP=1', '-At', '-c', $Statement
    )
}

function Invoke-Redis {
    param([string[]] $Command)
    Invoke-Compose -Command (@('exec', '-T', 'redis', 'redis-cli', '--raw') + $Command)
}

function Assert-Condition {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

function Assert-TestHash {
    param([string] $Hash)
    Assert-Condition ($Hash -cmatch '^[A-Za-z0-9]+$') "Unexpected test hash: $Hash"
}

function Invoke-Request {
    param(
        [string] $Destination,
        [string] $Path,
        [string] $Method = 'GET',
        [string] $UrlForm = '',
        [string] $Token = ''
    )
    $source = if ($Destination -eq 'app2') { 'app2' } else { 'app1' }
    $origin = if ($Destination -eq 'lb') { $LoadBalancerUrl.TrimEnd('/') } else { 'http://localhost:8080' }
    $command = @(
        'exec', '-T', $source, 'curl', '--silent', '--show-error', '--max-time', '20',
        '--include', '--request', $Method
    )
    if ($UrlForm) { $command += @('--data-urlencode', "url=$UrlForm") }
    if ($Token) { $command += @('--header', "X-Admin-Token: $Token") }
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

function Assert-Redirect {
    param([string] $Destination, [string] $Hash, [string] $Target)
    $response = Invoke-Request -Destination $Destination -Path "/$Hash"
    Assert-Condition ($response.Status -eq 307) "$Destination expected 307, got $($response.Status): $($response.Body)"
    Assert-Condition ($response.Headers['location'] -ceq $Target) "$Destination returned the wrong Location"
}

function Assert-Problem {
    param([string] $Destination, [string] $Hash, [int] $Expected)
    $response = Invoke-Request -Destination $Destination -Path "/$Hash"
    Assert-Condition ($response.Status -eq $Expected) "$Destination expected $Expected, got $($response.Status): $($response.Body)"
    Assert-Condition ($response.Headers['content-type'] -like 'application/problem+json*') "$Destination returned no ProblemDetail content type"
    $problem = $response.Body | ConvertFrom-Json
    Assert-Condition ($problem.status -eq $Expected) "$Destination returned the wrong ProblemDetail status"
    Assert-Condition ([bool] ($problem.title -and $problem.detail)) "$Destination returned an incomplete ProblemDetail"
    Assert-Condition (-not $response.Headers.ContainsKey('location')) "$Destination rejected a redirect but returned Location"
}

function Assert-Uncached {
    param([string] $Hash)
    $exists = Invoke-Redis -Command @('EXISTS', "$cachePrefix$Hash")
    Assert-Condition ($exists -eq '0') "$Hash is missing/inactive but was cached"
}

function Assert-ActiveEverywhere {
    param([string] $Hash, [string] $Target)
    foreach ($round in 1..2) {
        # Direct requests prove both replicas; repeated LB requests exercise nginx.
        foreach ($destination in @('app1', 'app2', 'lb', 'lb')) {
            Assert-Redirect -Destination $destination -Hash $Hash -Target $Target
        }
    }
    $ttl = [int] (Invoke-Redis -Command @('TTL', "$cachePrefix$Hash"))
    Assert-Condition ($ttl -ge $MinimumTtlSeconds) "Cache TTL ${ttl}s is too short to prove immediate eviction"
    Write-Host "PASS active 307 and Location on app1, app2 and LB; Redis TTL=${ttl}s"
}

function Assert-DeactivatedEverywhere {
    param([string] $Hash)
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-Request -Destination 'app1' -Path "/api/link/$Hash/deactivate" -Method 'POST' -Token $AdminToken
    Assert-Condition ($response.Status -eq 204) "Deactivation expected 204, got $($response.Status): $($response.Body)"
    # These are the FIRST requests after the action: no retries, sleeps or TTL wait.
    foreach ($destination in @('app2', 'app1', 'lb', 'lb')) {
        Assert-Problem -Destination $destination -Hash $Hash -Expected 410
    }
    $active = Invoke-Sql -Statement "SELECT active FROM short_url WHERE hash = '$Hash'"
    Assert-Condition ($active -eq 'f') 'Deactivated row must remain persisted and inactive'
    Assert-Uncached -Hash $Hash
    Write-Host "PASS immediate 410 ProblemDetail on both replicas and LB ($([Math]::Round($timer.Elapsed.TotalSeconds, 2))s)"

    $null = Invoke-Sql -Statement "DELETE FROM short_url WHERE hash = '$Hash'"
    foreach ($destination in @('app1', 'app2', 'lb', 'lb')) {
        Assert-Problem -Destination $destination -Hash $Hash -Expected 404
    }
    Assert-Uncached -Hash $Hash
    Write-Host 'PASS deleted row returns 404 ProblemDetail on both replicas and LB'
}

$testHashes = [System.Collections.Generic.List[string]]::new()
$rabbitStopped = $false
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

    $hash = 'cachecheck' + [Guid]::NewGuid().ToString('N')
    $target = "https://example.com/cache-acceptance/$hash"
    foreach ($destination in @('app1', 'app2', 'lb', 'lb')) {
        Assert-Problem -Destination $destination -Hash $hash -Expected 404
    }
    Assert-Uncached -Hash $hash
    # Public create generates its own hash. Seed this exact hash through SQL to
    # prove that a previous miss cannot hide a subsequently persisted active row.
    $null = Invoke-Sql -Statement (
        'INSERT INTO short_url(hash, target, created_at, active) ' +
        "VALUES ('$hash', '$target', CURRENT_TIMESTAMP, TRUE)"
    )
    $testHashes.Add($hash)
    Assert-ActiveEverywhere -Hash $hash -Target $target
    Assert-DeactivatedEverywhere -Hash $hash

    # Mark before stop, so even a partial stop failure triggers restoration.
    $rabbitStopped = $true
    $null = Invoke-Compose -Command @('stop', 'rabbit')
    $running = (Invoke-Compose -Command @('ps', '--status', 'running', '--services')) -split "`n"
    Assert-Condition ('rabbit' -notin $running) 'RabbitMQ did not stop'
    Write-Host 'RabbitMQ is stopped; repeating with a link created through the public API'
    $target = 'https://example.com/cache-acceptance/' + [Guid]::NewGuid().ToString('N')
    $created = Invoke-Request -Destination 'lb' -Path '/api/link' -Method 'POST' -UrlForm $target
    Assert-Condition ($created.Status -eq 201) "Create expected 201 with broker down, got $($created.Status): $($created.Body)"
    $hash = [string] ($created.Body | ConvertFrom-Json).hash
    Assert-TestHash -Hash $hash
    $testHashes.Add($hash)
    Assert-ActiveEverywhere -Hash $hash -Target $target
    Assert-DeactivatedEverywhere -Hash $hash
}
finally {
    try {
        if ($rabbitStopped) {
            $null = Invoke-Compose -Command @('start', '--wait', '--wait-timeout', '120', 'rabbit')
            Write-Host 'RabbitMQ restored'
        }
    }
    finally {
        try {
            foreach ($hash in $testHashes) {
                Assert-TestHash -Hash $hash
                $null = Invoke-Sql -Statement "DELETE FROM short_url WHERE hash = '$hash'"
                $null = Invoke-Redis -Command @('DEL', "$cachePrefix$hash")
            }
        }
        finally { Pop-Location }
    }
}
Write-Host 'PASS feature 0006, including broker outage'
