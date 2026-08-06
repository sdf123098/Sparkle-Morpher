param(
    [string[]]$GpuName,
    [switch]$AsJson
)

$ErrorActionPreference = 'Stop'
if (-not $GpuName -or $GpuName.Count -eq 0) {
    $GpuName = @(Get-CimInstance Win32_VideoController | ForEach-Object Name)
}

$joined = $GpuName -join ' | '
if ($joined -match '(?i)RTX\s*5060\s*Ti') {
    $profile = [ordered]@{ Width = 2560; Height = 1440; Tier = 'RTX 5060 Ti / 2K' }
} elseif ($joined -match '(?i)RTX\s*3050') {
    $profile = [ordered]@{ Width = 1280; Height = 720; Tier = 'RTX 3050 / low' }
} else {
    $profile = [ordered]@{ Width = 1280; Height = 720; Tier = 'fallback / low' }
}

$profile.Gpus = $joined
if ($AsJson) {
    [pscustomobject]$profile | ConvertTo-Json -Compress
} else {
    [pscustomobject]$profile
}
