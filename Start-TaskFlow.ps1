param(
    [switch]$InitializeOnly,
    [switch]$CopyDemoClaimCode,
    [switch]$ConfigureDatabasePassword
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot 'backend'
$secretsPath = $env:TASKFLOW_LOCAL_SECRETS
if (-not $secretsPath) {
    $localAppData = [Environment]::GetFolderPath('LocalApplicationData')
    $secretsPath = Join-Path $localAppData 'TaskFlow\.env.local'
}
$secretsPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($secretsPath)

$privateLocation = @($PSScriptRoot, $env:OneDrive, $env:OneDriveConsumer, $env:OneDriveCommercial)
if ($env:USERPROFILE) {
    $privateLocation += Join-Path $env:USERPROFILE 'OneDrive'
}
foreach ($directory in $privateLocation) {
    if ($directory) {
        $prefix = [IO.Path]::GetFullPath($directory).TrimEnd('\', '/') + '\'
        if ($secretsPath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Keep the secrets file outside the TaskFlow checkout and OneDrive.'
        }
    }
}
for ($entry = $secretsPath; $entry; $entry = Split-Path -Parent $entry) {
    if ((Test-Path -LiteralPath $entry) -and
        ((Get-Item -LiteralPath $entry -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Select a secrets path without symbolic links or junctions.'
    }
}

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

function Write-TaskFlowSecrets([string]$content) {
    $temporaryPath = Join-Path (Split-Path -Parent $secretsPath) ('.env.local.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($temporaryPath, $content)
        if (Test-Path -LiteralPath $secretsPath -PathType Leaf) {
            [IO.File]::Replace($temporaryPath, $secretsPath, [NullString]::Value)
        }
        else {
            [IO.File]::Move($temporaryPath, $secretsPath)
        }
    }
    finally {
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryPath
        }
    }
}

function Get-TaskFlowSetting([string]$name) {
    $content = [IO.File]::ReadAllText($secretsPath)
    $pattern = '(?m)^[ \t]*' + [regex]::Escape($name) + '[ \t]*[=:][ \t]*([^\r\n]*)'
    $matches = [regex]::Matches($content, $pattern)
    if ($matches.Count) {
        return $matches[$matches.Count - 1].Groups[1].Value
    }
}

function Save-DatabasePassword([string]$password) {
    # Java properties escapes preserve the value when Spring reads the file directly.
    $encoded = [regex]::Replace($password, '[\\\x00-\x20\x7f-\uffff]', {
        param($character)
        '\u{0:X4}' -f ([int][char]$character.Value[0])
    })
    $content = [IO.File]::ReadAllText($secretsPath)
    $content = [regex]::Replace($content, '(?m)^[ \t]*DATABASE_PASSWORD[ \t]*[=:][^\r\n]*(?:\r?\n|$)', '')
    $content = $content.TrimEnd([char[]]"`r`n") + [Environment]::NewLine
    Write-TaskFlowSecrets ($content + "DATABASE_PASSWORD=$encoded" + [Environment]::NewLine)
}

function Read-DatabasePassword {
    $masked = Read-Host 'PostgreSQL role password (current value or value already rotated in PostgreSQL)' -AsSecureString
    if ($masked.Length -eq 0) {
        throw 'DATABASE_PASSWORD cannot be empty.'
    }
    $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($masked)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer)
        $masked.Dispose()
    }
}

function Test-ExistingDatabaseVolume {
    # Compose resolves the actual name, including any project name override.
    $config = @(& docker compose config --format json 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose could not resolve the PostgreSQL volume.'
    }
    try {
        $volumeName = ($config -join [Environment]::NewLine | ConvertFrom-Json).volumes.pgdata.name
    }
    catch {
        throw 'Docker Compose returned an unreadable configuration.'
    }
    if (-not $volumeName) {
        throw 'The pgdata volume was missing from the Docker Compose configuration.'
    }

    $volumes = @(& docker volume ls --format '{{.Name}}')
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker could not list volumes. Start Docker Desktop and retry.'
    }
    return $volumes -contains $volumeName
}

$previousSecretsPath = $env:TASKFLOW_LOCAL_SECRETS
$previousDatabasePassword = $env:DATABASE_PASSWORD
Push-Location -LiteralPath $PSScriptRoot
try {
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $secretsPath))
    $settingsLock = [IO.File]::Open($secretsPath + '.lock', [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    try {
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
            Write-TaskFlowSecrets ("TASKFLOW_JWT_KEY=$key`r`nTASKFLOW_DEMO_CLAIM_CODE=$code`r`n")
        }

        if ($ConfigureDatabasePassword) {
            $password = Read-DatabasePassword
            Save-DatabasePassword $password
            Write-Host 'PostgreSQL password saved in the local secrets file.'
        }

        if ($CopyDemoClaimCode) {
            $code = $env:TASKFLOW_DEMO_CLAIM_CODE
            if (-not $code) {
                $code = Get-TaskFlowSetting 'TASKFLOW_DEMO_CLAIM_CODE'
            }
            if ($code) {
                Add-Type -AssemblyName System.Windows.Forms
                $clipboard = New-Object System.Windows.Forms.DataObject
                $clipboard.SetText($code.Trim())
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

        if (-not $ConfigureDatabasePassword) {
            $password = $env:DATABASE_PASSWORD
            if (-not $password) {
                $setting = Get-TaskFlowSetting 'DATABASE_PASSWORD'
                if ($setting) {
                    $password = [regex]::Replace($setting, '\\u([0-9a-fA-F]{4})', {
                        param($escape)
                        [string][char][Convert]::ToInt32($escape.Groups[1].Value, 16)
                    })
                }
            }
        }
        if (-not $password) {
            $candidate = New-TaskFlowSecret
            $env:DATABASE_PASSWORD = $candidate
            if (Test-ExistingDatabaseVolume) {
                throw 'An existing PostgreSQL volume has no configured password. Use -InitializeOnly -ConfigureDatabasePassword to store its current password, or follow the rotation procedure in README.md.'
            }
            Save-DatabasePassword $candidate
            $password = $candidate
        }
    }
    finally {
        $settingsLock.Dispose()
    }

    $env:DATABASE_PASSWORD = $password
    & docker compose up -d postgres
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
    $env:DATABASE_PASSWORD = $previousDatabasePassword
    Pop-Location
}
