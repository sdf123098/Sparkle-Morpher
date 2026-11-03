param(
    [Parameter(Mandatory=$true)][string]$LogPath,
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [Parameter(Mandatory=$true)][string]$Branch,
    [Parameter(Mandatory=$true)][ValidateSet('native','curseforge')][string]$Dist,
    [Parameter(Mandatory=$true)][string]$Task,
    [Parameter(Mandatory=$true)][string]$SnapshotPath,
    [Parameter(Mandatory=$true)][string]$BaselinePath
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$comparison = if ([IO.Path]::DirectorySeparatorChar -eq '\') { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
$rows = [System.Collections.Generic.List[object]]::new()
$pattern = '^(?<path>(?:[A-Za-z]:[\\/]|/).+?\.java):(?<line>\d+): (?:(?:警告|warning): )(?<message>.+)$'
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $LogPath).Path)) {
    $match = [regex]::Match($line, $pattern)
    if (-not $match.Success) { continue }
    $absolute = [IO.Path]::GetFullPath($match.Groups['path'].Value)
    $relative = [IO.Path]::GetRelativePath($root, $absolute)
    if ($relative -eq '..' -or $relative.StartsWith('..' + [IO.Path]::DirectorySeparatorChar, $comparison) -or
        [IO.Path]::IsPathRooted($relative)) {
        throw "诊断路径超出仓库根目录: $absolute"
    }
    $source = $relative.Replace('\','/')
    $lineNumber = [int]$match.Groups['line'].Value
    $message = ($match.Groups['message'].Value -replace '\s+', ' ').Trim()
    $categoryMatch = [regex]::Match($message, '^\[(?<kind>[^\]]+)\]')
    $category = if ($categoryMatch.Success) { $categoryMatch.Groups['kind'].Value } elseif ($message -match 'internal proprietary API|内部专用 API') { 'internal-api' } else { 'javac' }
    $identity = @($Branch, $Dist, $Task, $category, $source, $message) -join "`n"
    $fingerprint = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($identity)))
    $rows.Add([pscustomobject]@{ fingerprint=$fingerprint; category=$category; source=$source; message=$message; line=$lineNumber })
}
$unique = @($rows | Group-Object -Property { $_.fingerprint } | ForEach-Object {
    $first = $_.Group[0]
    [pscustomobject]@{ fingerprint=$first.fingerprint; category=$first.category; source=$first.source; message=$first.message; occurrences=$_.Count; lines=@($_.Group.line | Sort-Object -Unique) }
} | Sort-Object source, category, message)
$snapshot = [ordered]@{ formatVersion=1; branch=$Branch; dist=$Dist; task=$Task; javacDiagnosticCount=$rows.Count; uniqueFingerprintCount=$unique.Count; fingerprints=$unique }
$snapshotJson = ConvertTo-Json -InputObject $snapshot -Depth 8
$snapshotFullPath = [IO.Path]::GetFullPath($SnapshotPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($snapshotFullPath)) | Out-Null
[IO.File]::WriteAllText($snapshotFullPath, $snapshotJson + "`n", [Text.UTF8Encoding]::new($false))

$baselineIndex = Get-Content -LiteralPath $BaselinePath -Raw | ConvertFrom-Json
$baseline = @($baselineIndex.baselines | Where-Object { $_.branch -eq $Branch -and $_.dist -eq $Dist -and $_.task -eq $Task })
if ($baseline.Count -ne 1) { throw "诊断基线必须唯一匹配 $Branch/$Dist/$Task，实际匹配 $($baseline.Count) 项" }
$expected = @($baseline[0].fingerprints)
$actual = @($unique | ForEach-Object { $_.fingerprint })
$unexpected = @($actual | Where-Object { $_ -notin $expected })
$resolved = @($expected | Where-Object { $_ -notin $actual })
"$Branch/$Dist $Task : javac=$($rows.Count), unique=$($unique.Count), new=$($unexpected.Count), resolved=$($resolved.Count)"
if ($unexpected.Count -gt 0) {
    foreach ($id in $unexpected) {
        $row = $unique | Where-Object fingerprint -eq $id | Select-Object -First 1
        "NEW $($row.category) $($row.source): $($row.message)"
    }
    exit 1
}
