param(
    [string]$Serial = "",
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk",
    [string]$EnvPath = ".env.local",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$PackageName = "com.voiceflowkeyboard.ime"
$PrefsFileName = "voiceflow_keyboard_settings.xml"

function Resolve-Adb {
    $localAdb = Join-Path (Get-Location) ".tooling/android-sdk/platform-tools/adb.exe"
    if (Test-Path $localAdb) {
        return (Resolve-Path $localAdb).Path
    }
    return "adb"
}

function Read-DotEnv([string]$Path) {
    $values = @{}
    if (!(Test-Path $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            continue
        }

        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

function First-EnvValue($Values, [string[]]$Names) {
    foreach ($name in $Names) {
        if ($Values.ContainsKey($name) -and ![string]::IsNullOrWhiteSpace($Values[$name])) {
            return $Values[$name].Trim()
        }
    }
    return ""
}

function New-PrefsDocument([string]$RawXml) {
    if ([string]::IsNullOrWhiteSpace($RawXml)) {
        $RawXml = "<?xml version='1.0' encoding='utf-8'?><map />"
    }
    [xml]$document = $RawXml
    if ($null -eq $document.map) {
        [xml]$document = "<?xml version='1.0' encoding='utf-8'?><map />"
    }
    return $document
}

function Set-StringPreference([xml]$Document, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    $existing = $Document.SelectSingleNode("/map/string[@name='$Name']")
    if ($null -eq $existing) {
        $existing = $Document.CreateElement("string")
        $attribute = $Document.CreateAttribute("name")
        $attribute.Value = $Name
        [void]$existing.Attributes.Append($attribute)
        [void]$Document.map.AppendChild($existing)
    }
    $existing.InnerText = $Value.Trim()
    return $true
}

function Save-XmlUtf8([xml]$Document, [string]$Path) {
    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Encoding = New-Object System.Text.UTF8Encoding($false)
    $settings.Indent = $true
    $writer = [System.Xml.XmlWriter]::Create($Path, $settings)
    try {
        $Document.Save($writer)
    } finally {
        $writer.Close()
    }
}

function Assert-LastExit([string]$Action) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE."
    }
}

$adb = Resolve-Adb
$serialArgs = @()
if (![string]::IsNullOrWhiteSpace($Serial)) {
    $serialArgs = @("-s", $Serial)
}

$resolvedApk = Resolve-Path -LiteralPath $ApkPath

if (!$SkipInstall) {
    & $adb @serialArgs install -r $resolvedApk.Path | Out-Host
    Assert-LastExit "APK install"
}

$envValues = Read-DotEnv $EnvPath
$keys = @(
    @{
        Pref = "openai_api_key"
        Label = "OpenAI"
        Names = @("OpenAIAPIKey", "OPENAI_API_KEY", "OPENAI_APIKEY")
    },
    @{
        Pref = "anthropic_api_key"
        Label = "Anthropic"
        Names = @("AnthropicAPIKey", "ANTHROPIC_API_KEY", "ANTHROPIC_APIKEY")
    },
    @{
        Pref = "xai_api_key"
        Label = "xAI"
        Names = @("XAIAPIKey", "XAI_API_KEY", "XAI_APIKEY")
    },
    @{
        Pref = "deepgram_api_key"
        Label = "Deepgram"
        Names = @("DeepgramAPIKey", "DEEPGRAM_API_KEY", "DEEPGRAM_APIKEY")
    }
)

$existingXml = ""
try {
    $existingXml = (& $adb @serialArgs exec-out run-as $PackageName cat "shared_prefs/$PrefsFileName" 2>$null) -join "`n"
    $existingXml = $existingXml -replace "`0", ""
} catch {
    $existingXml = ""
}

$document = New-PrefsDocument $existingXml
$seeded = New-Object System.Collections.Generic.List[string]
foreach ($key in $keys) {
    $value = First-EnvValue $envValues $key.Names
    if (Set-StringPreference $document $key.Pref $value) {
        [void]$seeded.Add($key.Label)
    }
}

if ($seeded.Count -eq 0) {
    Write-Host "No API keys found in $EnvPath. App install completed without seeding keys."
    exit 0
}

$tempXml = Join-Path ([System.IO.Path]::GetTempPath()) ("voiceflow-keyboard-prefs-" + [System.Guid]::NewGuid().ToString("N") + ".xml")
$remoteXml = "/data/local/tmp/voiceflow-keyboard-prefs.xml"
try {
    Save-XmlUtf8 $document $tempXml
    & $adb @serialArgs shell "am force-stop $PackageName" | Out-Null
    Assert-LastExit "Force-stop app"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $adb @serialArgs push $tempXml $remoteXml 1>$null 2>$null
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    Assert-LastExit "Push temporary preferences"
    & $adb @serialArgs shell "chmod 644 $remoteXml" | Out-Null
    Assert-LastExit "Prepare temporary preferences"
    $copyCommand = "run-as $PackageName sh -c 'mkdir -p shared_prefs && cp $remoteXml shared_prefs/$PrefsFileName && chmod 600 shared_prefs/$PrefsFileName'"
    & $adb @serialArgs shell $copyCommand | Out-Null
    Assert-LastExit "Write app preferences"
    & $adb @serialArgs shell "rm $remoteXml" | Out-Null
    Assert-LastExit "Remove temporary preferences"
} finally {
    Remove-Item -LiteralPath $tempXml -ErrorAction SilentlyContinue
}

Write-Host ("Seeded API keys on device: " + ($seeded -join ", "))
