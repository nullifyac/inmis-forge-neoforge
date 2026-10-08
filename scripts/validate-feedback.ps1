<#
.SYNOPSIS
Builds and validates release JARs, retaining an immutable artifact/provenance run.
.EXAMPLE
.\scripts\validate-feedback.ps1
.NOTES
Uses the version declared in each project's gradle.properties. Existing build
outputs are retained; only the exact expected playable JAR is staged.
#>
param(
    [ValidateSet('forge-1.16.5', 'forge-1.18.2', 'forge-1.19.2', 'forge-1.20.1', 'neoforge-1.21.1', 'neoforge-26.1.2')]
    [string[]]$Versions = @('neoforge-26.1.2', 'neoforge-1.21.1', 'forge-1.20.1', 'forge-1.19.2', 'forge-1.18.2', 'forge-1.16.5'),
    [string]$Java17Home = $env:JAVA17_HOME,
    [string]$Java21Home = $env:JAVA21_HOME,
    [string]$Java25Home = $env:JAVA25_HOME
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$validationRoot = Join-Path $repositoryRoot 'temp/feedback-validation'
$runId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$runRoot = Join-Path $validationRoot $runId
$originalJavaHome = $env:JAVA_HOME
$originalDebug = $env:DEBUG
$utf8 = [Text.UTF8Encoding]::new($false)
$artifacts = [Collections.Generic.List[object]]::new()
$builds = [Collections.Generic.List[object]]::new()
$completed = $false
$failure = $null
$validationLock = $null
$sourceCommit = $null
$sourceBranch = $null
$sourceStatus = @()
$runCreated = $false
$startedUtc = [DateTime]::UtcNow.ToString('o')
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-ValidationPath([string]$Path) {
    $target = [IO.Path]::GetFullPath($Path)
    $allowed = [IO.Path]::GetFullPath($validationRoot).TrimEnd('\')
    if (-not $target.Equals($allowed, [StringComparison]::OrdinalIgnoreCase) -and
        -not $target.StartsWith($allowed + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Staging path lies outside feedback validation: $target"
    }
    $cursor = $target
    while ($cursor.Length -ge $allowed.Length) {
        if ((Test-Path -LiteralPath $cursor) -and
            ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Staging path contains a filesystem redirect: $cursor"
        }
        if ($cursor.Equals($allowed, [StringComparison]::OrdinalIgnoreCase)) { break }
        $cursor = Split-Path -Path $cursor -Parent
    }
    return $target
}
function New-ValidationDirectory([string]$Path) {
    $target = Assert-ValidationPath $Path
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    return $target
}
function Write-ValidationJson([string]$Path, [object]$Value) {
    $target = Assert-ValidationPath $Path
    New-ValidationDirectory (Split-Path -Path $target -Parent) | Out-Null
    [IO.File]::WriteAllText($target, (ConvertTo-Json -InputObject $Value -Depth 10), $utf8)
}
function Find-Jdk([int]$Major, [string]$ExplicitPath) {
    $candidates = @()
    if ($ExplicitPath) { $candidates += $ExplicitPath } else {
        if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
        $javaCommand = Get-Command java -ErrorAction SilentlyContinue
        if ($javaCommand) { $candidates += Split-Path (Split-Path $javaCommand.Source) }
        foreach ($installationRoot in @((Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Java'), (Join-Path $env:USERPROFILE '.jdks'))) {
            if (Test-Path -LiteralPath $installationRoot) {
                $candidates += Get-ChildItem -LiteralPath $installationRoot -Directory | Select-Object -ExpandProperty FullName
            }
        }
    }
    foreach ($candidate in $candidates) {
        $releasePath = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $releasePath) -and (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe'))) {
            if ((Get-Content -LiteralPath $releasePath -Raw) -match ('(?m)^JAVA_VERSION="' + $Major + '(?:\.|"|-)')) {
                return [IO.Path]::GetFullPath($candidate)
            }
        }
    }
    throw "JDK $Major was not found. Supply its Java home parameter."
}
function Read-GradleProperties([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#!\s][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2].Trim() }
    }
    foreach ($key in @('archives_base_name', 'fabric_version', 'mod_version', 'minecraft_version')) {
        if (-not $values.ContainsKey($key) -or -not $values[$key]) { throw "Missing $key in $Path" }
    }
    return $values
}
function Get-ExpectedJarName([hashtable]$Properties) {
    $name = "$($Properties.archives_base_name)-$($Properties.fabric_version)-$($Properties.mod_version)-$($Properties.minecraft_version).jar"
    if ($name -ne [IO.Path]::GetFileName($name) -or $name.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0) {
        throw 'The declared release filename is invalid.'
    }
    return $name
}
function Read-TomlString([string]$Section, [string]$Key) {
    $match = [regex]::Match($Section, '(?m)^\s*' + [regex]::Escape($Key) + '\s*=\s*"([^"]*)"\s*$')
    if (-not $match.Success) { throw "Packaged metadata is missing $Key." }
    return $match.Groups[1].Value
}
function Test-ReleaseMetadata([string]$Text, [hashtable]$Properties, [bool]$NeoForge) {
    if ($Text -match '\$\{') { throw 'Packaged mod metadata contains unexpanded placeholders.' }
    $sections = @([regex]::Matches($Text, '(?ms)^\[\[([^\]]+)\]\]\s*(.*?)(?=^\[\[|\z)'))
    $mods = @($sections | Where-Object { $_.Groups[1].Value -eq 'mods' })
    if ($mods.Count -ne 1 -or (Read-TomlString $mods[0].Groups[2].Value 'modId') -ne 'inmis') {
        throw 'Packaged mod metadata must declare only the production Inmis mod.'
    }
    $expectedVersion = "$($Properties.fabric_version)-$($Properties.mod_version)-$($Properties.minecraft_version)"
    $actualVersion = Read-TomlString $mods[0].Groups[2].Value 'version'
    if ($actualVersion -ne $expectedVersion) { throw "Packaged mod version $actualVersion differs from $expectedVersion." }
    $dependencies = @($sections | Where-Object { $_.Groups[1].Value -eq 'dependencies.inmis' })
    $minecraft = @($dependencies | Where-Object { (Read-TomlString $_.Groups[2].Value 'modId') -eq 'minecraft' })
    $expectedRange = "[$($Properties.minecraft_version)]"
    if ($minecraft.Count -ne 1 -or (Read-TomlString $minecraft[0].Groups[2].Value 'versionRange') -ne $expectedRange) {
        throw "Packaged Minecraft dependency must be exactly $expectedRange."
    }
    $optionalMods = @('curios')
    if ($NeoForge -and $Properties.ContainsKey('accessories_version')) { $optionalMods += 'accessories' }
    foreach ($modId in $optionalMods) {
        $optional = @($dependencies | Where-Object { (Read-TomlString $_.Groups[2].Value 'modId') -eq $modId })
        if ($optional.Count -ne 1) { throw "Packaged metadata must declare optional $modId integration." }
        $section = $optional[0].Groups[2].Value
        if ($NeoForge) {
            if ((Read-TomlString $section 'type') -ne 'optional') { throw "$modId must remain optional." }
        } elseif ($section -notmatch '(?m)^\s*mandatory\s*=\s*false\s*$') { throw "$modId must remain optional." }
    }
    return [ordered]@{ modId = 'inmis'; version = $actualVersion; minecraftRange = $expectedRange }
}
function Test-ReleaseJar([string]$JarPath, [hashtable]$Properties, [bool]$NeoForge) {
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $forbidden = @($entryNames | Where-Object {
            $_ -match '^draylar/inmis/(gametest|smoketest|runtime)/' -or
            $_ -match '^data/(inmis_game_tests|inmis_runtime_tests|inmis_smoke_tests)/' -or
            $_ -match '^draylar/inmis/.*(?:Test|Tests)(?:\$[^/]*)?\.class$' -or
            $_ -eq 'draylar/inmis/mixin/ItemStackMixin.class' -or
            $_ -match '^(top/theillusivec4/curios|io/wispforest/accessories)/'
        })
        if ($forbidden.Count) { throw "Nonproduction entries found in release JAR: $($forbidden -join ', ')" }
        if ($entryNames -notcontains 'draylar/inmis/util/BackpackStorage.class') {
            throw 'Release JAR does not contain the current backpack storage implementation.'
        }
        $expectedClassVersion = switch ($Properties.minecraft_version) {
            '1.16.5' { 52 }
            '26.1.2' { 69 }
            '1.21.1' { 65 }
            default { 61 }
        }
        foreach ($classEntry in @($archive.Entries | Where-Object { $_.FullName -match '^draylar/inmis/.*\.class$' })) {
            $stream = $classEntry.Open()
            $classReader = [IO.BinaryReader]::new($stream)
            try {
                $header = $classReader.ReadBytes(8)
                if ($header.Length -ne 8 -or $header[0] -ne 0xca -or $header[1] -ne 0xfe -or
                    $header[2] -ne 0xba -or $header[3] -ne 0xbe) { throw "Invalid class header: $($classEntry.FullName)" }
                $actualClassVersion = ([int]$header[6] -shl 8) -bor [int]$header[7]
                if ($actualClassVersion -ne $expectedClassVersion) {
                    throw "Class $($classEntry.FullName) uses bytecode $actualClassVersion; expected $expectedClassVersion."
                }
            } finally { $classReader.Dispose() }
        }
        $metadataName = if ($NeoForge) { 'META-INF/neoforge.mods.toml' } else { 'META-INF/mods.toml' }
        $metadataEntry = $archive.GetEntry($metadataName)
        if ($null -eq $metadataEntry) { throw "Release JAR is missing $metadataName." }
        $reader = [IO.StreamReader]::new($metadataEntry.Open())
        try { $metadataText = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $metadata = Test-ReleaseMetadata $metadataText $Properties $NeoForge
        $mixinsEntry = $archive.GetEntry('inmis.mixins.json')
        if ($null -eq $mixinsEntry) { throw 'Release JAR is missing its mixin configuration.' }
        $reader = [IO.StreamReader]::new($mixinsEntry.Open())
        try { $mixins = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        $configuredMixins = @($mixins.mixins)
        if ($mixins.PSObject.Properties.Match('client').Count) { $configuredMixins += @($mixins.client) }
        if ($configuredMixins -contains 'ItemStackMixin') {
            throw 'Release mixin configuration still references obsolete ItemStackMixin.'
        }
        if ($NeoForge) {
            foreach ($required in @('data/curios/tags/item/back.json', 'data/minecraft/tags/item/dyeable.json')) {
                if ($entryNames -notcontains $required) { throw "Release JAR is missing current resource $required." }
            }
            foreach ($obsolete in @('data/curios/tags/items/back.json', 'data/minecraft/tags/items/dyeable.json')) {
                if ($entryNames -contains $obsolete) { throw "Release JAR contains obsolete resource $obsolete." }
            }
        }
        return [pscustomobject]@{ metadataName = $metadataName; metadataText = $metadataText; metadata = $metadata; entries = $entryNames.Count }
    } finally { $archive.Dispose() }
}
function Save-Provenance {
    $record = [ordered]@{
        runId = $runId; startedUtc = $startedUtc; recordedUtc = [DateTime]::UtcNow.ToString('o')
        completed = $completed; failure = $failure
        sourceCommit = $sourceCommit; sourceBranch = $sourceBranch
        sourceDirty = ($sourceStatus.Count -gt 0); sourceStatus = @($sourceStatus)
        requestedVersions = @($Versions); builds = @($builds.ToArray()); artifacts = @($artifacts.ToArray())
        gradleArguments = @('build', '--no-daemon', '--max-workers=1', '--console=plain', '-Dorg.gradle.jvmargs=-Xmx1G')
    }
    Write-ValidationJson (Join-Path $runRoot 'provenance.json') $record
    Write-ValidationJson (Join-Path $runRoot 'artifacts.json') @($artifacts.ToArray())
    Write-ValidationJson (Join-Path $validationRoot 'latest.json') ([ordered]@{
        runId = $runId; completed = $completed; provenance = Join-Path $runRoot 'provenance.json'
        artifacts = Join-Path $runRoot 'artifacts.json'
    })
}
function Copy-TestReports([string]$ProjectPath, [string]$Version) {
    foreach ($relative in @('reports/tests/test', 'test-results/test')) {
        $source = Join-Path $ProjectPath "build/$relative"
        if (Test-Path -LiteralPath $source -PathType Container) {
            $destination = New-ValidationDirectory (Join-Path $runRoot "reports/$Version")
            $name = if ($relative.StartsWith('reports')) { 'tests' } else { 'test-results' }
            Copy-Item -LiteralPath $source -Destination (Join-Path $destination $name) -Recurse
        }
    }
}

try {
    New-ValidationDirectory $validationRoot | Out-Null
    $validationLock = [IO.File]::Open((Join-Path $validationRoot 'validation.lock'), [IO.FileMode]::OpenOrCreate,
            [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    if (Test-Path -LiteralPath $runRoot) { throw "Run directory already exists: $runRoot" }
    New-ValidationDirectory $runRoot | Out-Null
    $runCreated = $true
    $sourceCommit = ((& git -C $repositoryRoot rev-parse HEAD) -join '').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source commit.' }
    $sourceBranch = ((& git -C $repositoryRoot rev-parse --abbrev-ref HEAD) -join '').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source branch.' }
    $sourceStatus = @(& git -C $repositoryRoot status --porcelain=v1 --untracked-files=normal)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the source working-tree status.' }
    Save-Provenance
    Write-Host "Release validation reports: $runRoot"
    $env:DEBUG = ''
    foreach ($version in @($Versions | Select-Object -Unique)) {
        $neoForge = $version.StartsWith('neoforge')
        # Forge 1.16.5 compiles Java 8 bytecode through its toolchain, while ForgeGradle runs on JDK 17.
        $major = if ($version -eq 'neoforge-26.1.2') { 25 } elseif ($neoForge) { 21 } else { 17 }
        $jdkPath = if ($major -eq 25) { $Java25Home } elseif ($major -eq 21) { $Java21Home } else { $Java17Home }
        $env:JAVA_HOME = Find-Jdk $major $jdkPath
        $projectPath = Join-Path $repositoryRoot "$version/inmis"
        $properties = Read-GradleProperties (Join-Path $projectPath 'gradle.properties')
        $expectedName = Get-ExpectedJarName $properties
        $logPath = Join-Path $runRoot "$version-build.log"
        $declared = [ordered]@{}
        foreach ($key in @('minecraft_version', 'fabric_version', 'mod_version', 'archives_base_name',
                'forge_version', 'neoforge_version', 'curios_version', 'curios_version_range',
                'accessories_version', 'accessories_version_range')) {
            if ($properties.ContainsKey($key)) { $declared[$key] = $properties[$key] }
        }
        $jdkRelease = Get-Content -LiteralPath (Join-Path $env:JAVA_HOME 'release') -Raw
        $jdkVersion = [regex]::Match($jdkRelease, '(?m)^JAVA_VERSION="([^"]+)"').Groups[1].Value
        $build = [pscustomobject]@{
            version = $version; status = 'RUNNING'; javaHome = $env:JAVA_HOME
            javaVersion = $jdkVersion; properties = $declared; expectedFile = $expectedName; log = $logPath
        }
        $builds.Add($build)
        Save-Provenance
        Write-Host "Building $version with JDK $major; expected artifact: $expectedName"
        Push-Location -LiteralPath $projectPath
        try {
            # Native stderr warnings remain visible; the exit code decides build success.
            $ErrorActionPreference = 'Continue'
            & .\gradlew.bat build --no-daemon --max-workers=1 --console=plain '-Dorg.gradle.jvmargs=-Xmx1G' 2>&1 |
                ForEach-Object { $_.ToString() } | Tee-Object -FilePath $logPath
            $buildExitCode = $LASTEXITCODE
            $ErrorActionPreference = 'Stop'
            if ($buildExitCode -ne 0) { throw "$version build failed with exit code $buildExitCode. See $logPath" }
        } finally {
            $ErrorActionPreference = 'Stop'
            Pop-Location
            Copy-TestReports $projectPath $version
        }
        $sourceJar = Join-Path $projectPath "build/libs/$expectedName"
        if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
            throw "Expected playable mod JAR is missing: $sourceJar"
        }
        $verification = Test-ReleaseJar $sourceJar $properties $neoForge
        $processedMetadata = Join-Path $projectPath "build/resources/main/$($verification.metadataName)"
        if (-not (Test-Path -LiteralPath $processedMetadata -PathType Leaf) -or
            [IO.File]::ReadAllText($processedMetadata) -cne $verification.metadataText) {
            throw 'Packaged metadata differs from the processed production resource.'
        }
        $destination = New-ValidationDirectory (Join-Path $runRoot "artifacts/$version")
        $artifactPath = Join-Path $destination $expectedName
        $sourceHash = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash.ToLowerInvariant()
        Copy-Item -LiteralPath $sourceJar -Destination $artifactPath
        $stagedHash = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($sourceHash -ne $stagedHash) { throw 'The staged artifact hash differs from the built JAR.' }
        $metadataDirectory = New-ValidationDirectory (Join-Path $runRoot "metadata/$version")
        [IO.File]::WriteAllText((Join-Path $metadataDirectory ([IO.Path]::GetFileName($verification.metadataName))),
                $verification.metadataText, $utf8)
        $artifacts.Add([pscustomobject]@{
            version = $version; path = $artifactPath; sha256 = $stagedHash
            filename = $expectedName; size = (Get-Item -LiteralPath $artifactPath).Length
            modVersion = $verification.metadata.version; minecraftVersion = $properties.minecraft_version
            loader = $(if ($neoForge) { 'NeoForge' } else { 'Forge' })
            metadata = $verification.metadata; packagedEntries = $verification.entries
        })
        $build.status = 'PASS'
        Save-Provenance
    }
    $completed = $true
    Save-Provenance
    # Compatibility index: points to the latest successful immutable artifacts.
    Write-ValidationJson (Join-Path $validationRoot 'artifacts.json') @($artifacts.ToArray())
    Write-Host "Validation complete. Immutable artifacts, metadata and SHA-256 hashes: $runRoot"
} catch {
    $failure = $_.Exception.Message
    foreach ($build in $builds) { if ($build.status -eq 'RUNNING') { $build.status = 'FAIL' } }
    throw
} finally {
    if ($runCreated) { Save-Provenance }
    if ($validationLock) { $validationLock.Dispose() }
    $env:JAVA_HOME = $originalJavaHome
    $env:DEBUG = $originalDebug
}
