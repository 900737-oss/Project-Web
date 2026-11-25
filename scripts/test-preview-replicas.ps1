param(
    [int]$TimeoutSeconds = 25
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot

function Invoke-ComposeCurl {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Service,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $response = & docker compose exec -T $Service curl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed in Compose service '$Service' (exit code $LASTEXITCODE). Is the stack healthy?"
    }

    return (($response -join "`n").Trim())
}

try {
    $target = "http://127.0.0.1:9/unreachable"
    $createResponse = Invoke-ComposeCurl -Service "app1" -Arguments @(
        "-fsS", "--max-time", "10", "-X", "POST",
        "-H", "Content-Type: application/x-www-form-urlencoded",
        "--data-urlencode", "url=$target",
        "-w", "`n%{http_code}",
        "http://localhost:8080/api/link"
    )

    $createLines = $createResponse -split "`r?`n"
    $createStatus = $createLines[-1]
    if ($createStatus -ne "201") {
        throw "POST /api/link returned HTTP $createStatus; expected 201. Body: $createResponse"
    }

    $createBody = ($createLines[0..($createLines.Length - 2)] -join "`n")
    $created = $createBody | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($created.hash)) {
        throw "POST /api/link did not return a hash. Body: $createBody"
    }

    $hash = [string]$created.hash
    $previewUrl = "http://localhost:8080/api/preview/$hash"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $terminalPreview = $null

    do {
        $previewJson = Invoke-ComposeCurl -Service "app1" -Arguments @("-fsS", "--max-time", "5", $previewUrl)
        $terminalPreview = $previewJson | ConvertFrom-Json
        if ($terminalPreview.status -in @("READY", "FAILED")) {
            break
        }

        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    if ($terminalPreview.status -notin @("READY", "FAILED")) {
        throw "Preview $hash did not reach READY or FAILED within $TimeoutSeconds seconds; last status: $($terminalPreview.status)"
    }

    $app1Json = Invoke-ComposeCurl -Service "app1" -Arguments @("-fsS", "--max-time", "5", $previewUrl)
    $app2Json = Invoke-ComposeCurl -Service "app2" -Arguments @("-fsS", "--max-time", "5", $previewUrl)
    $app1Preview = $app1Json | ConvertFrom-Json
    $app2Preview = $app2Json | ConvertFrom-Json

    foreach ($field in @("hash", "status", "title", "image", "url", "detail")) {
        $app1Value = $app1Preview.$field
        $app2Value = $app2Preview.$field
        if ($null -eq $app1Value -and $null -eq $app2Value) {
            continue
        }
        if ([string]$app1Value -cne [string]$app2Value) {
            throw "Replicas returned different '$field' values for preview $hash. app1='$app1Value'; app2='$app2Value'."
        }
    }

    [PSCustomObject]@{
        Hash              = $hash
        Status            = $terminalPreview.status
        ReplicasIdentical = $true
        App1              = $app1Preview
        App2              = $app2Preview
    } | ConvertTo-Json -Depth 10
}
finally {
    Pop-Location
}
