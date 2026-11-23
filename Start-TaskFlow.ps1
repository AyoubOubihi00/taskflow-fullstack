param(
    [switch]$InitializeOnly,
    [switch]$CopyDemoClaimCode
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot 'backend'
$secretsPath = $env:TASKFLOW_LOCAL_SECRETS
if (-not $secretsPath) {
    $localAppData = [Environment]::GetFolderPath('LocalApplicationData')
    $secretsPath = Join-Path $localAppData 'TaskFlow\.env.local'
}
$secretsPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($secretsPath)

function New-TaskFlowSecret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    }
    finally {
        $random.Dispose()
    }
}

if (-not (Test-Path -LiteralPath $secretsPath -PathType Leaf)) {
    $key = $env:TASKFLOW_JWT_KEY
    if (-not $key) {
        $key = New-TaskFlowSecret
    }
    try {
        if ($key -cnotmatch '\A[A-Za-z0-9+/]+={0,2}\z' -or [Convert]::FromBase64String($key).Length -lt 32) {
            throw 'Invalid signing key.'
        }
    }
    catch {
        throw 'TASKFLOW_JWT_KEY must be Base64 and contain at least 32 bytes.'
    }

    $code = $env:TASKFLOW_DEMO_CLAIM_CODE
    if ($code -cnotmatch '\A[A-Za-z0-9+/=._-]{32,128}\z') {
        $code = New-TaskFlowSecret
    }

    $secretsDirectory = Split-Path -Parent $secretsPath
    [void][IO.Directory]::CreateDirectory($secretsDirectory)
    $temporaryPath = Join-Path $secretsDirectory ('.env.local.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllLines($temporaryPath, @("TASKFLOW_JWT_KEY=$key", "TASKFLOW_DEMO_CLAIM_CODE=$code"))
        try {
            [IO.File]::Move($temporaryPath, $secretsPath)
        }
        catch [IO.IOException] {
            if (-not (Test-Path -LiteralPath $secretsPath -PathType Leaf)) {
                throw
            }
        }
    }
    finally {
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryPath
        }
    }
}

if ($CopyDemoClaimCode) {
    $code = $env:TASKFLOW_DEMO_CLAIM_CODE
    if (-not $code) {
        $setting = Select-String -LiteralPath $secretsPath -Pattern '^\s*TASKFLOW_DEMO_CLAIM_CODE\s*=\s*(\S*)\s*$' |
            Select-Object -Last 1
        if ($setting) {
            $code = $setting.Matches[0].Groups[1].Value
        }
    }
    if ($code) {
        Add-Type -AssemblyName System.Windows.Forms
        $clipboard = New-Object System.Windows.Forms.DataObject
        $clipboard.SetText($code)
        $clipboard.SetData('ExcludeClipboardContentFromMonitorProcessing', $false, '1')
        [System.Windows.Forms.Clipboard]::SetDataObject($clipboard, $true)
        Write-Host 'Demo claim code copied to the clipboard.'
    }
    else {
        Write-Warning 'No demo claim code is configured.'
    }
}

if ($InitializeOnly) {
    return
}

$previousSecretsPath = $env:TASKFLOW_LOCAL_SECRETS
Push-Location -LiteralPath $PSScriptRoot
try {
    & docker compose up -d --no-recreate postgres
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose could not start PostgreSQL.'
    }
    $deadline = (Get-Date).AddSeconds(60)
    while ($true) {
        & docker compose exec -T postgres pg_isready -h 127.0.0.1 *> $null
        if ($LASTEXITCODE -eq 0) {
            break
        }
        if ((Get-Date) -ge $deadline) {
            throw 'PostgreSQL did not become ready within 60 seconds.'
        }
        Start-Sleep -Seconds 1
    }

    Set-Location -LiteralPath $backend
    $env:TASKFLOW_LOCAL_SECRETS = $secretsPath.Replace('\', '/')
    & mvn.cmd spring-boot:run
    exit $LASTEXITCODE
}
finally {
    $env:TASKFLOW_LOCAL_SECRETS = $previousSecretsPath
    Pop-Location
}
