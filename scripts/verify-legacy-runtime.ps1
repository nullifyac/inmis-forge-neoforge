<#
.SYNOPSIS
Runs isolated legacy Forge native server regressions and real client/server persistence checks on Java 8.
.EXAMPLE
.\scripts\verify-legacy-runtime.ps1
.EXAMPLE
.\scripts\verify-legacy-runtime.ps1 -SkipClient
.NOTES
Uses only the selected legacy project's build directories. Never accesses existing Prism instances or worlds.
#>
[CmdletBinding()]
param(
    [ValidateSet('forge-1.12.2', 'forge-1.7.10')]
    [string[]]$Versions = @('forge-1.12.2', 'forge-1.7.10'),
    [ValidateSet('none', 'baubles')][string[]]$Profiles = @('none', 'baubles'),
    [ValidateSet('none', 'baubles')][string[]]$ClientProfiles = @('none', 'baubles'),
    [switch]$SkipClient,
    [switch]$SkipServerChecks,
    [string]$Java17Home = $env:JAVA17_HOME,
    [string]$Java8Home = $env:JAVA8_HOME,
    [hashtable]$GradleUserHomes = @{},
    [ValidateRange(1024,65535)][int]$Port = 25576,
    [ValidateRange(60,7200)][int]$GradleTimeoutSeconds = 1200,
    [ValidateRange(30,1800)][int]$ServerStartupTimeoutSeconds = 240,
    [ValidateRange(60,1800)][int]$ClientTimeoutSeconds = 480
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$validationRoot = Join-Path $repositoryRoot 'temp/legacy-runtime-validation'
$runId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$reportRoot = Join-Path $validationRoot $runId
$powerShellExecutable = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$utf8 = [Text.UTF8Encoding]::new($false)
$ownedProcesses = [Collections.Generic.List[Diagnostics.Process]]::new()
$checks = [Collections.Generic.List[object]]::new()
$verificationLock = $null
$completed = $false

function Assert-ContainedPath([string]$Path, [string]$AllowedRoot) {
    $fullPath = [IO.Path]::GetFullPath($Path)
    $fullRoot = [IO.Path]::GetFullPath($AllowedRoot).TrimEnd([char[]]@('\', '/'))
    if (-not $fullPath.Equals($fullRoot, [StringComparison]::OrdinalIgnoreCase) -and
        -not $fullPath.StartsWith($fullRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path lies outside the isolated verification directory: $fullPath"
    }
    $cursor = $fullPath
    while ($cursor.Length -ge $fullRoot.Length) {
        if (Test-Path -LiteralPath $cursor) {
            if ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Verification path contains a filesystem redirect: $cursor"
            }
        }
        if ($cursor.Equals($fullRoot, [StringComparison]::OrdinalIgnoreCase)) { break }
        $cursor = Split-Path -Path $cursor -Parent
    }
    return $fullPath
}
function New-IsolatedDirectory([string]$Path, [string]$AllowedRoot) {
    $target = Assert-ContainedPath $Path $AllowedRoot
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    return $target
}
function Write-IsolatedText([string]$Path, [string]$Text, [string]$AllowedRoot) {
    $target = Assert-ContainedPath $Path $AllowedRoot
    New-IsolatedDirectory (Split-Path -Path $target -Parent) $AllowedRoot | Out-Null
    [IO.File]::WriteAllText($target, $Text, $utf8)
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
            $versionPrefix = if ($Major -eq 8) { '1\.8' } else { [string]$Major }
            if ((Get-Content -LiteralPath $releasePath -Raw) -match ('(?m)^JAVA_VERSION="' + $versionPrefix + '(?:\.|"|-)')) {
                return [IO.Path]::GetFullPath($candidate)
            }
        }
    }
    throw "JDK $Major was not found. Supply the matching Java home parameter."
}
function Quote-ProcessArgument([string]$Argument) {
    if ($Argument.Contains('"')) { throw 'A process argument contains an unsupported quotation mark.' }
    return '"' + $Argument + '"'
}
function Start-OwnedHelper([string]$ScriptPath, [string[]]$Arguments, [string]$LogDirectory, [string]$Name) {
    New-IsolatedDirectory $LogDirectory $reportRoot | Out-Null
    $helperArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath) + $Arguments
    $startOptions = @{
        FilePath = $powerShellExecutable
        ArgumentList = ($helperArguments | ForEach-Object { Quote-ProcessArgument $_ }) -join ' '
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = Join-Path $LogDirectory "$Name-stdout.log"
        RedirectStandardError = Join-Path $LogDirectory "$Name-stderr.log"
    }
    $process = Start-Process @startOptions
    # Windows PowerShell can otherwise lose the redirected child's exit code
    # when the process exits before its native handle has been acquired.
    $null = $process.Handle
    $ownedProcesses.Add($process)
    return $process
}
function Stop-OwnedProcessTree([Diagnostics.Process]$Process) {
    if ($null -eq $Process -or $Process.HasExited) { return }
    $started = $Process.StartTime
    $descendants = [Collections.Generic.List[object]]::new()
    function Find-OwnedChildren([int]$ParentId) {
        foreach ($child in @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $ParentId" -ErrorAction SilentlyContinue)) {
            if ($child.CreationDate -ge $started) {
                Find-OwnedChildren ([int]$child.ProcessId)
                $descendants.Add($child)
            }
        }
    }
    Find-OwnedChildren $Process.Id
    foreach ($child in $descendants) {
        $current = Get-Process -Id ([int]$child.ProcessId) -ErrorAction SilentlyContinue
        if ($current -and $current.StartTime -ge $started) {
            Stop-Process -Id $current.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if (-not $Process.HasExited) { Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue }
}
function Wait-OwnedProcess([Diagnostics.Process]$Process, [int]$TimeoutSeconds, [string]$Description) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $nextUpdate = [DateTime]::UtcNow.AddSeconds(30)
    while (-not $Process.WaitForExit(1000)) {
        if ([DateTime]::UtcNow -ge $deadline) {
            Stop-OwnedProcessTree $Process
            throw "$Description timed out after $TimeoutSeconds seconds."
        }
        if ([DateTime]::UtcNow -ge $nextUpdate) {
            Write-Host "$Description is still running."
            $nextUpdate = [DateTime]::UtcNow.AddSeconds(30)
        }
    }
    $Process.WaitForExit()
    $exitCode = $Process.ExitCode
    if ($null -eq $exitCode) { throw "$Description exited without a readable exit code." }
    return [int]$exitCode
}
function Read-Log([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $stream = $null
    $reader = $null
    try {
        # Start-Process keeps redirected log writers open until the helper
        # exits. A reader must share both reads and writes to poll those logs.
        $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        $reader = [IO.StreamReader]::new($stream)
        return $reader.ReadToEnd()
    } catch { return '' }
    finally {
        if ($null -ne $reader) { $reader.Dispose() }
        elseif ($null -ne $stream) { $stream.Dispose() }
    }
}
function Save-Summary {
    $summary = [ordered]@{
        runId = $runId; completed = $completed
        versions = $Versions; profiles = $Profiles; clientProfiles = $ClientProfiles; port = $Port
        skipClient = [bool]$SkipClient; skipServerChecks = [bool]$SkipServerChecks
        checks = @($checks.ToArray())
        failedChecks = @($checks | Where-Object { $_.status -eq 'FAIL' }).Count
    }
    Write-IsolatedText (Join-Path $reportRoot 'summary.json') ($summary | ConvertTo-Json -Depth 8) $reportRoot
}
function Add-Check([string]$Name, [string]$Status, [string]$Detail, [string]$Artifacts, [int]$TestCount = 0) {
    $checks.Add([pscustomobject]@{
        name = $Name; status = $Status; detail = $Detail; tests = $TestCount
        artifacts = $Artifacts; recordedUtc = [DateTime]::UtcNow.ToString('o')
    })
    Save-Summary
    Write-Host "$Status $Name - $Detail"
}
function Invoke-IsolatedGradle([string]$ProjectPath, [string]$JdkPath, [string[]]$Arguments, [string]$LogDirectory, [string]$Name) {
    $requestPath = Join-Path $LogDirectory "$Name-request.json"
    $request = @{
        projectPath = $ProjectPath; javaHome = $JdkPath
        gradleUserHome = $(if ($GradleUserHomes.ContainsKey($version)) { $GradleUserHomes[$version] } else { $env:GRADLE_USER_HOME })
        arguments = @($Arguments) + @('--no-daemon', '--max-workers=1', '--console=plain', '-Dorg.gradle.jvmargs=-Xmx1G')
    }
    Write-IsolatedText $requestPath ($request | ConvertTo-Json -Depth 4) $reportRoot
    $process = Start-OwnedHelper $gradleWorker @('-RequestFile', $requestPath) $LogDirectory $Name
    $exitCode = Wait-OwnedProcess $process $GradleTimeoutSeconds "$Name Gradle invocation"
    if ($exitCode -ne 0) { throw "$Name exited with code $exitCode. Inspect its stdout/stderr logs." }
    # --no-daemon stops the single-use Gradle JVM when this invocation exits;
    # no Gradle JVM is retained while the two exported game processes run.
}
function Copy-RuntimeReports([string]$RuntimeDirectory, [string]$Destination, [DateTime]$StartedUtc) {
    New-IsolatedDirectory $Destination $reportRoot | Out-Null
    foreach ($relative in @('logs/latest.log', 'logs/debug.log', 'server.properties', 'renderer-request.txt')) {
        $source = Join-Path $RuntimeDirectory $relative
        if ((Test-Path -LiteralPath $source -PathType Leaf) -and
                ($relative -eq 'server.properties' -or (Get-Item -LiteralPath $source).LastWriteTimeUtc -ge $StartedUtc.AddSeconds(-1))) {
            $target = Join-Path $Destination $relative
            New-IsolatedDirectory (Split-Path -Path $target -Parent) $reportRoot | Out-Null
            Copy-Item -LiteralPath $source -Destination $target -Force
        }
    }
    if (Test-Path -LiteralPath $RuntimeDirectory -PathType Container) {
        foreach ($file in @(Get-ChildItem -LiteralPath $RuntimeDirectory -File -Recurse -Filter '*.xml')) {
            if ($file.LastWriteTimeUtc -lt $StartedUtc.AddSeconds(-1)) { continue }
            $relative = $file.FullName.Substring($RuntimeDirectory.TrimEnd('\').Length).TrimStart('\')
            $target = Assert-ContainedPath (Join-Path $Destination $relative) $reportRoot
            New-IsolatedDirectory (Split-Path -Path $target -Parent) $reportRoot | Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $target -Force
        }
        $crashDirectory = Join-Path $RuntimeDirectory 'crash-reports'
        if (Test-Path -LiteralPath $crashDirectory -PathType Container) {
            foreach ($file in @(Get-ChildItem -LiteralPath $crashDirectory -File -Filter '*.txt')) {
                if ($file.LastWriteTimeUtc -lt $StartedUtc.AddSeconds(-1)) { continue }
                $targetDirectory = New-IsolatedDirectory (Join-Path $Destination 'crash-reports') $reportRoot
                Copy-Item -LiteralPath $file.FullName -Destination $targetDirectory -Force
            }
        }
    }
}
function Get-SmokeConfiguration([int]$WitheredWidth, [int]$WitheredRows) {
    $configuration = @'
{
  "backpacks": [
    {"name":"baby","rowWidth":3,"numberOfRows":1,"isFireImmune":false,"dyeable":false,"openSound":"minecraft:item.armor.equip_leather"},
    {"name":"frayed","rowWidth":9,"numberOfRows":1,"isFireImmune":false,"dyeable":true,"openSound":"minecraft:item.armor.equip_leather"},
    {"name":"plated","rowWidth":9,"numberOfRows":2,"isFireImmune":false,"dyeable":false,"openSound":"minecraft:item.armor.equip_iron"},
    {"name":"gilded","rowWidth":9,"numberOfRows":3,"isFireImmune":false,"dyeable":false,"openSound":"minecraft:item.armor.equip_gold"},
    {"name":"bejeweled","rowWidth":9,"numberOfRows":5,"isFireImmune":false,"dyeable":false,"openSound":"minecraft:item.armor.equip_diamond"},
    {"name":"blazing","rowWidth":9,"numberOfRows":6,"isFireImmune":true,"dyeable":false,"openSound":"minecraft:item.armor.equip_leather"},
    {"name":"withered","rowWidth":11,"numberOfRows":6,"isFireImmune":false,"dyeable":false,"openSound":"minecraft:item.armor.equip_leather"},
    {"name":"endless","rowWidth":15,"numberOfRows":6,"isFireImmune":false,"dyeable":false,"openSound":"minecraft:item.armor.equip_leather"}
  ],
  "unstackablesOnly":false,"disableShulkers":true,"blacklist":[],"playSound":true,
  "requireArmorTrinketToOpen":false,"allowBackpacksInChestplate":true,"enableTrinketCompatibility":true,
  "requireEmptyForUnequip":false,"spillArmorBackpacksOnDeath":false,"spillMainBackpacksOnDeath":false,
  "importBackpackedItems":false,"autoBackpackedTier":"bejeweled","autoBackpackedColumns":9,
  "autoBackpackedRows":5,"autoBackpackedAllowSmaller":true,"trinketRendering":true,"guiTitleColor":"0x404040"
}
'@ | ConvertFrom-Json
    $withered = $configuration.backpacks | Where-Object { $_.name -eq 'withered' }
    $withered.rowWidth = $WitheredWidth
    $withered.numberOfRows = $WitheredRows
    if ($version -eq 'forge-1.7.10') { foreach ($backpack in $configuration.backpacks) { $backpack.openSound = 'random.pop' } }
    return $configuration | ConvertTo-Json -Depth 8
}
function Assert-LoopbackPortAvailable {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } catch { throw "Loopback port $Port is already in use. Stop the other isolated test server first." }
    finally { $listener.Stop() }
}
function Clear-ExactResult([string]$Path, [string]$RuntimeRoot) {
    $target = Assert-ContainedPath $Path $RuntimeRoot
    if ([IO.Path]::GetFileName($target) -notmatch '^(?:(?:result|server-result)-(?:write|read)|renderer-request)\.txt$') {
        throw "Refusing to remove an unrecognized verification file: $target"
    }
    if (Test-Path -LiteralPath $target -PathType Leaf) { Remove-Item -LiteralPath $target -Force }
}
function Wait-ServerReady([Diagnostics.Process]$Process, [string]$OutputPath) {
    $deadline = [DateTime]::UtcNow.AddSeconds($ServerStartupTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) { throw "Smoke server exited before becoming ready, code $($Process.ExitCode)." }
        if ((Read-Log $OutputPath) -match 'Done \([0-9.,]+s\)!') { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Smoke server did not become ready within $ServerStartupTimeoutSeconds seconds."
}
function Invoke-SmokePhase([string]$Version, [string]$Profile, [string]$Phase, [string]$RuntimeRoot) {
    $serverDirectory = Join-Path $RuntimeRoot 'server'
    $clientDirectory = Join-Path $RuntimeRoot 'client'
    $logDirectory = Join-Path $reportRoot "$Version/$Profile/$Phase"
    $clientResult = Join-Path $clientDirectory "result-$Phase.txt"
    $serverResult = Join-Path $serverDirectory "server-result-$Phase.txt"
    $serverProcess = $null
    $clientProcess = $null
    $passed = $false
    $phaseStartedUtc = [DateTime]::UtcNow
    try {
        Assert-LoopbackPortAvailable
        Clear-ExactResult $clientResult $RuntimeRoot
        Clear-ExactResult $serverResult $RuntimeRoot
        Clear-ExactResult (Join-Path $serverDirectory 'renderer-request.txt') $RuntimeRoot
        Write-IsolatedText (Join-Path $serverDirectory 'phase.txt') $Phase $RuntimeRoot
        $serverRows = if ($Phase -eq 'write') { 6 } else { 3 }
        Write-IsolatedText (Join-Path $serverDirectory 'config/inmis.json') (Get-SmokeConfiguration 9 $serverRows) $RuntimeRoot
        Write-IsolatedText (Join-Path $clientDirectory 'config/inmis.json') (Get-SmokeConfiguration 11 6) $RuntimeRoot
        New-IsolatedDirectory $logDirectory $reportRoot | Out-Null
        Copy-Item -LiteralPath (Join-Path $serverDirectory 'config/inmis.json') -Destination (Join-Path $logDirectory 'server-inmis.json')
        Copy-Item -LiteralPath (Join-Path $clientDirectory 'config/inmis.json') -Destination (Join-Path $logDirectory 'client-inmis.json')
        Write-Host "Starting native $Version smoke $Profile/$Phase."
        $serverProcess = Start-OwnedHelper $runtimeLauncher @('-LaunchFile', (Join-Path $RuntimeRoot 'RuntimeServer-launch.json')) $logDirectory 'server'
        Wait-ServerReady $serverProcess (Join-Path $logDirectory 'server-stdout.log')
        $clientProcess = Start-OwnedHelper $runtimeLauncher @('-LaunchFile', (Join-Path $RuntimeRoot 'RuntimeClient-launch.json')) $logDirectory 'client'
        $deadline = [DateTime]::UtcNow.AddSeconds($ClientTimeoutSeconds)
        $nextUpdate = [DateTime]::UtcNow.AddSeconds(30)
        $clientText = ''
        $serverText = ''
        while ($true) {
            $clientText = (Read-Log $clientResult).Trim()
            $serverText = (Read-Log $serverResult).Trim()
            # File creation can precede the small result write, and the JVM
            # may still hold the file briefly. Retry until both contain data.
            if ($clientText -and $serverText) { break }
            if ($clientProcess.HasExited -and -not $clientText) {
                throw "Smoke client exited without a result, code $($clientProcess.ExitCode)."
            }
            if ($serverProcess.HasExited -and -not $serverText) {
                throw "Smoke server exited without a result, code $($serverProcess.ExitCode)."
            }
            if ([DateTime]::UtcNow -ge $deadline) { throw "Smoke $Profile/$Phase timed out." }
            if ([DateTime]::UtcNow -ge $nextUpdate) {
                Write-Host "Native smoke $Profile/$Phase is still running."
                $nextUpdate = [DateTime]::UtcNow.AddSeconds(30)
            }
            Start-Sleep -Milliseconds 500
        }
        if (-not $clientText.StartsWith("PASS $Phase") -or -not $serverText.StartsWith("PASS $Phase")) {
            throw "Client result: $clientText; server result: $serverText"
        }
        $clientExit = Wait-OwnedProcess $clientProcess 45 "$Profile/$Phase client shutdown"
        $serverExit = Wait-OwnedProcess $serverProcess 45 "$Profile/$Phase server save and shutdown"
        if ($clientExit -ne 0 -or $serverExit -ne 0) {
            throw "Native processes did not exit cleanly: client=$clientExit, server=$serverExit."
        }
        $screenshot = Join-Path $clientDirectory "screenshots/inmis-$Phase-menu.png"
        if (-not (Test-Path -LiteralPath $screenshot -PathType Leaf)) { throw 'Client verification screenshot is missing.' }
        if ((Get-Item -LiteralPath $screenshot).LastWriteTimeUtc -lt $phaseStartedUtc.AddSeconds(-1)) {
            throw 'Client verification screenshot was not written during this phase.'
        }
        Copy-Item -LiteralPath $screenshot -Destination (Join-Path $logDirectory "inmis-$Phase-menu.png") -Force
        $settingsScreenshot = Join-Path $clientDirectory "screenshots/inmis-$Phase-settings.png"
        if ($Version -in @('forge-1.12.2','forge-1.7.10')) {
            if (-not (Test-Path -LiteralPath $settingsScreenshot -PathType Leaf) -or
                    (Get-Item -LiteralPath $settingsScreenshot).LastWriteTimeUtc -lt $phaseStartedUtc.AddSeconds(-1)) {
                throw 'Upgrades panel screenshot was not written during this phase.'
            }
            Copy-Item -LiteralPath $settingsScreenshot -Destination $logDirectory -Force
        }
        foreach ($suffix in @('chest-render') + $(if ($Profile -eq 'baubles') { @('baubles-render') } else { @() })) {
            $renderScreenshot = Join-Path $clientDirectory "screenshots/inmis-$Phase-$suffix.png"
            if (-not (Test-Path -LiteralPath $renderScreenshot -PathType Leaf) -or
                    (Get-Item -LiteralPath $renderScreenshot).LastWriteTimeUtc -lt $phaseStartedUtc.AddSeconds(-1)) {
                throw "$suffix screenshot was not written during this phase."
            }
            Copy-Item -LiteralPath $renderScreenshot -Destination $logDirectory -Force
        }
        Add-Check "$Version-$Profile-$Phase" 'PASS' $serverText $logDirectory
        $passed = $true
    } catch {
        Add-Check "$Version-$Profile-$Phase" 'FAIL' $_.Exception.Message $logDirectory
    } finally {
        Stop-OwnedProcessTree $clientProcess
        Stop-OwnedProcessTree $serverProcess
        New-IsolatedDirectory $logDirectory $reportRoot | Out-Null
        foreach ($resultPath in @($clientResult, $serverResult)) {
            if (Test-Path -LiteralPath $resultPath -PathType Leaf) { Copy-Item -LiteralPath $resultPath -Destination $logDirectory -Force }
        }
        Copy-RuntimeReports $serverDirectory (Join-Path $logDirectory 'server-runtime') $phaseStartedUtc
        Copy-RuntimeReports $clientDirectory (Join-Path $logDirectory 'client-runtime') $phaseStartedUtc
    }
    return $passed
}

function Write-TestServerFiles([string]$Directory, [string]$AllowedRoot, [string]$WorldName) {
    Write-IsolatedText (Join-Path $Directory 'eula.txt') 'eula=true' $AllowedRoot
    $properties = @('server-ip=127.0.0.1', "server-port=$Port", 'online-mode=false',
        "level-name=$WorldName", 'gamemode=0', 'difficulty=0', 'spawn-protection=0',
        'level-type=FLAT', 'generate-structures=false',
        'max-players=1', 'view-distance=4', 'enable-rcon=false', 'enable-query=false', 'sync-chunk-writes=true') -join [Environment]::NewLine
    Write-IsolatedText (Join-Path $Directory 'server.properties') $properties $AllowedRoot
}
function Invoke-ServerChecks([string]$Version, [string]$Profile, [string]$ProjectPath, [string]$RuntimeRoot) {
    $directory = New-IsolatedDirectory (Join-Path $ProjectPath "build/smokeTest/$Profile") (Join-Path $ProjectPath 'build')
    $logs = Join-Path $reportRoot "$Version/$Profile/server-checks"
    $resultPath = Assert-ContainedPath (Join-Path $directory 'server-checks.json') $directory
    $process = $null
    $checksStartedUtc = [DateTime]::UtcNow
    try {
        Assert-LoopbackPortAvailable
        if (Test-Path -LiteralPath $resultPath -PathType Leaf) { Remove-Item -LiteralPath $resultPath -Force }
        Write-TestServerFiles $directory $directory "inmis-checks-$runId-$Profile"
        Write-IsolatedText (Join-Path $directory 'config/inmis.json') (Get-SmokeConfiguration 11 6) $directory
        $process = Start-OwnedHelper $runtimeLauncher @('-LaunchFile', (Join-Path $RuntimeRoot 'SmokeTestServer-launch.json')) $logs 'server-checks'
        $exitCode = Wait-OwnedProcess $process $ClientTimeoutSeconds "$Profile native server assertions"
        if ($exitCode -ne 0) { throw "Native server assertions exited with code $exitCode." }
        if (-not (Test-Path -LiteralPath $resultPath -PathType Leaf)) { throw 'Native server exited without its assertion report.' }
        $result = Read-Log $resultPath | ConvertFrom-Json
        $expected = if ($Version -eq 'forge-1.7.10') { if ($Profile -eq 'baubles') { 31 } else { 26 } } else { if ($Profile -eq 'baubles') { 37 } else { 32 } }
        if (-not $result.completed -or $result.failed -ne 0 -or $result.passed -ne $expected) {
            throw "Native assertions failed or incomplete: passed=$($result.passed), failed=$($result.failed), expected=$expected."
        }
        if ($result.javaVersion -notmatch '^1\.8\.' -or $result.profile -ne $Profile -or
                [bool]$result.baublesLoaded -ne ($Profile -eq 'baubles')) { throw 'Native report has incorrect Java or optional-mod profile.' }
        Add-Check "$Version-$Profile-server-checks" 'PASS' 'Actual Java 8 server, player damage, menu, item pickup, recipes, persistence and optional equipment callbacks passed.' $logs $result.passed
    } catch {
        Add-Check "$Version-$Profile-server-checks" 'FAIL' $_.Exception.Message $logs
    } finally {
        Stop-OwnedProcessTree $process
        New-IsolatedDirectory $logs $reportRoot | Out-Null
        if (Test-Path -LiteralPath $resultPath -PathType Leaf) { Copy-Item -LiteralPath $resultPath -Destination $logs -Force }
        Copy-RuntimeReports $directory (Join-Path $logs 'runtime-reports') $checksStartedUtc
    }
}

try {
    New-IsolatedDirectory $validationRoot $repositoryRoot | Out-Null
    $verificationLock = [IO.File]::Open((Join-Path $validationRoot 'verification.lock'), [IO.FileMode]::OpenOrCreate,
        [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    New-IsolatedDirectory $reportRoot $repositoryRoot | Out-Null
    Write-Host "legacy Forge verification reports: $reportRoot"
    if (($SkipClient -or $ClientProfiles.Count -eq 0) -and
            ($SkipServerChecks -or $Profiles.Count -eq 0)) {
        throw 'Select at least one native server or client verification profile.'
    }
    $jdk17 = if ($Versions -contains 'forge-1.7.10') { Find-Jdk 17 $Java17Home } else { $null }
    $jdk8 = Find-Jdk 8 $Java8Home
    foreach ($version in @($Versions | Select-Object -Unique)) {
    $projectPath = Join-Path $repositoryRoot "$version/inmis"
    $buildJdk = if ($version -eq 'forge-1.12.2') { $jdk8 } else { $jdk17 }
    $runtimeRoot = New-IsolatedDirectory (Join-Path $projectPath 'build/runtime') (Join-Path $projectPath 'build')
    $gradleWorker = Join-Path $reportRoot 'gradle-worker.ps1'
    $runtimeLauncher = Join-Path $reportRoot 'java8-worker.ps1'
    $workerContent = @'
param([Parameter(Mandatory=$true)][string]$RequestFile)
$ErrorActionPreference = 'Stop'
$request = Get-Content -LiteralPath $RequestFile -Raw | ConvertFrom-Json
$env:JAVA_HOME = $request.javaHome
$env:GRADLE_USER_HOME = $request.gradleUserHome
$env:DEBUG = ''
Push-Location -LiteralPath $request.projectPath
try {
    $ErrorActionPreference = 'Continue'
    $arguments = @($request.arguments)
    & (Join-Path $request.projectPath 'gradlew.bat') @arguments
    exit $LASTEXITCODE
} finally { Pop-Location }
'@
    Write-IsolatedText $gradleWorker $workerContent $reportRoot
    $java8Content = @'
param([Parameter(Mandatory=$true)][string]$LaunchFile)
$ErrorActionPreference = 'Stop'
$launch = Get-Content -LiteralPath $LaunchFile -Raw | ConvertFrom-Json
foreach ($entry in $launch.environment.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process')
}
# Legacy FML discovers mods from direct classpath URLs. A manifest-only wrapper
# hides those URLs even though Java itself can still resolve their classes.
$javaArguments = @($launch.jvmArgs) + @('-classpath', [string]$launch.classpath, $launch.mainClass) + @($launch.args)
if (($javaArguments | ForEach-Object { ([string]$_).Length + 3 } | Measure-Object -Sum).Sum -gt 30000) {
    throw 'The Java 8 launch exceeds the supported Windows command-line length.'
}
Push-Location -LiteralPath $launch.workingDirectory
try {
    $ErrorActionPreference = 'Continue'
    & $launch.java @javaArguments
    exit $LASTEXITCODE
} finally { Pop-Location }
'@
    Write-IsolatedText $runtimeLauncher $java8Content $reportRoot
    foreach ($profile in @(@($Profiles) + @($ClientProfiles) | Select-Object -Unique)) {
        if ($SkipServerChecks -and ($SkipClient -or $ClientProfiles -notcontains $profile)) { continue }
        $exportDirectory = Join-Path $reportRoot "$version/$profile/export"
        try {
            Invoke-IsolatedGradle $projectPath $buildJdk @('exportLegacyRuntimeLaunch',
                "-PcompatProfile=$profile") $exportDirectory "export-$profile"
            foreach ($suffix in @('SmokeTestServer','RuntimeServer','RuntimeClient')) {
                $path = Join-Path $runtimeRoot "$suffix-launch.json"
                $launch = Read-Log $path | ConvertFrom-Json
                $expectedDirectory = if ($suffix -eq 'SmokeTestServer') {
                    Join-Path $projectPath "build/smokeTest/$profile"
                } elseif ($suffix -eq 'RuntimeServer') {
                    Join-Path $runtimeRoot 'server'
                } else {
                    Join-Path $runtimeRoot 'client'
                }
                $launchDirectory = Assert-ContainedPath $launch.workingDirectory (Join-Path $projectPath 'build')
                if (-not $launchDirectory.Equals([IO.Path]::GetFullPath($expectedDirectory), [StringComparison]::OrdinalIgnoreCase)) {
                    throw "Exported launch has an unexpected working directory: $launchDirectory"
                }
                $javaDirectory = Split-Path (Split-Path $launch.java)
                $releasePath = Join-Path $javaDirectory 'release'
                if (-not (Test-Path -LiteralPath $releasePath -PathType Leaf) -and
                        (Split-Path $javaDirectory -Leaf) -eq 'jre') {
                    $releasePath = Join-Path (Split-Path $javaDirectory) 'release'
                }
                if ((Read-Log $releasePath) -notmatch '(?m)^JAVA_VERSION="1\.8\.') { throw 'Exported game JVM is not Java 8.' }
                $launch.java = Join-Path $jdk8 'bin/java.exe'
                if ($suffix -eq 'RuntimeClient') {
                    # Keep the legacy Forge progress window out of the renderer smoke check.
                    $launch.jvmArgs = @($launch.jvmArgs) + @('-Dfml.earlyprogresswindow=false', "-Dinmis.smokePort=$Port")
                }
                Write-IsolatedText $path ($launch | ConvertTo-Json -Depth 6) $runtimeRoot
                Copy-Item -LiteralPath $path -Destination $exportDirectory -Force
            }
            Add-Check "$version-$profile-export" 'PASS' 'ForgeGradle launch exported; Java 8 runtime verified and Gradle JVM stopped.' $exportDirectory
        } catch {
            Add-Check "$version-$profile-export" 'FAIL' $_.Exception.Message $exportDirectory
            continue
        }
        if (-not $SkipServerChecks -and $Profiles -contains $profile) { Invoke-ServerChecks $version $profile $projectPath $runtimeRoot }
        if (-not $SkipClient -and $ClientProfiles -contains $profile) {
            New-IsolatedDirectory (Join-Path $runtimeRoot 'client') $runtimeRoot | Out-Null
            New-IsolatedDirectory (Join-Path $runtimeRoot 'server') $runtimeRoot | Out-Null
            $options = @('fullscreen:false','guiScale:2','renderDistance:4','overrideWidth:1280','overrideHeight:720',
                'tutorialStep:none','pauseOnLostFocus:false','enableVsync:false','maxFps:30','gamma:1.0') -join [Environment]::NewLine
            Write-IsolatedText (Join-Path $runtimeRoot 'client/options.txt') $options $runtimeRoot
            Write-TestServerFiles (Join-Path $runtimeRoot 'server') $runtimeRoot "inmis-runtime-$runId-$profile"
            if (Invoke-SmokePhase $version $profile 'write' $runtimeRoot) {
                Invoke-SmokePhase $version $profile 'read' $runtimeRoot | Out-Null
            } else { Add-Check "$version-$profile-read" 'SKIP' 'Write failed; no valid saved fixture exists.' $exportDirectory }
        }
    }
    }
    $completed = $true
} catch {
    Add-Check 'legacy-runner' 'FAIL' $_.Exception.Message $reportRoot
} finally {
    foreach ($process in $ownedProcesses) { Stop-OwnedProcessTree $process }
    if ($verificationLock) { $verificationLock.Dispose() }
    if (Test-Path -LiteralPath $reportRoot -PathType Container) { Save-Summary }
}
Write-Host "legacy Forge verification finished. Summary: $(Join-Path $reportRoot 'summary.json')"
if (@($checks | Where-Object { $_.status -eq 'FAIL' }).Count -gt 0 -or -not $completed) { exit 1 }
