<#
.SYNOPSIS
Runs native Inmis GameTests and isolated Forge/NeoForge client/server smoke tests.
.EXAMPLE
.\scripts\verify-runtime.ps1
.EXAMPLE
.\scripts\verify-runtime.ps1 -Versions neoforge-1.21.1 -ClientProfiles none,curios,accessories,both
.EXAMPLE
.\scripts\verify-runtime.ps1 -SkipClient
.EXAMPLE
.\scripts\verify-runtime.ps1 -Versions neoforge-26.1.2 -NeoProfiles none,curios -ClientProfiles none,curios
.NOTES
Run after source edits are complete. Existing Prism instances and worlds are
never accessed. Logs, results, screenshots and reports go to temp/runtime-validation.
#>
[CmdletBinding()]
param(
    [ValidateSet('forge-1.18.2', 'forge-1.19.2', 'forge-1.20.1', 'neoforge-1.21.1', 'neoforge-26.1.2')]
    [string[]]$Versions = @('forge-1.18.2', 'forge-1.19.2', 'forge-1.20.1', 'neoforge-1.21.1', 'neoforge-26.1.2'),
    [ValidateSet('none', 'curios', 'accessories', 'both')]
    [string[]]$NeoProfiles = @('none', 'curios', 'accessories', 'both'),
    [ValidateSet('none', 'curios', 'accessories', 'both')]
    [string[]]$ClientProfiles = @('none'),
    [switch]$SkipClient,
    [switch]$SkipGameTests,
    [string]$Java17Home = $env:JAVA17_HOME,
    [string]$Java21Home = $env:JAVA21_HOME,
    [string]$Java25Home = $env:JAVA25_HOME,
    [ValidateRange(60, 7200)][int]$GradleTimeoutSeconds = 1200,
    [ValidateRange(30, 1800)][int]$ServerStartupTimeoutSeconds = 240,
    [ValidateRange(60, 1800)][int]$ClientTimeoutSeconds = 480
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$validationRoot = Join-Path $repositoryRoot 'temp/runtime-validation'
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
            if ((Get-Content -LiteralPath $releasePath -Raw) -match ('(?m)^JAVA_VERSION="' + $Major + '(?:\.|"|-)')) {
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
        versions = $Versions; neoProfiles = $NeoProfiles; clientProfiles = $ClientProfiles
        skipClient = [bool]$SkipClient; skipGameTests = [bool]$SkipGameTests
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
        arguments = @($Arguments) + @('--no-daemon', '--max-workers=1', '--console=plain', '-Dorg.gradle.jvmargs=-Xmx1G')
    }
    Write-IsolatedText $requestPath ($request | ConvertTo-Json -Depth 4) $reportRoot
    $process = Start-OwnedHelper $gradleWorker @('-RequestFile', $requestPath) $LogDirectory $Name
    $exitCode = Wait-OwnedProcess $process $GradleTimeoutSeconds "$Name Gradle invocation"
    if ($exitCode -ne 0) { throw "$Name exited with code $exitCode. Inspect its stdout/stderr logs." }
    # --no-daemon stops the single-use Gradle JVM when this invocation exits;
    # no Gradle JVM is retained while the two exported game processes run.
}
function Copy-RuntimeReports([string]$RuntimeDirectory, [string]$Destination, [datetime]$SinceUtc = [datetime]::MinValue) {
    New-IsolatedDirectory $Destination $reportRoot | Out-Null
    $earliestUtc = if ($SinceUtc -eq [datetime]::MinValue) { $SinceUtc } else { $SinceUtc.AddSeconds(-1) }
    foreach ($relative in @('logs/latest.log', 'logs/debug.log', 'server.properties', 'renderer-request.txt')) {
        $source = Join-Path $RuntimeDirectory $relative
        if ((Test-Path -LiteralPath $source -PathType Leaf) -and
                (Get-Item -LiteralPath $source).LastWriteTimeUtc -ge $earliestUtc) {
            $target = Join-Path $Destination $relative
            New-IsolatedDirectory (Split-Path -Path $target -Parent) $reportRoot | Out-Null
            Copy-Item -LiteralPath $source -Destination $target -Force
        }
    }
    if (Test-Path -LiteralPath $RuntimeDirectory -PathType Container) {
        foreach ($file in @(Get-ChildItem -LiteralPath $RuntimeDirectory -File -Recurse -Filter '*.xml')) {
            if ($file.LastWriteTimeUtc -lt $earliestUtc) { continue }
            $relative = $file.FullName.Substring($RuntimeDirectory.TrimEnd('\').Length).TrimStart('\')
            $target = Assert-ContainedPath (Join-Path $Destination $relative) $reportRoot
            New-IsolatedDirectory (Split-Path -Path $target -Parent) $reportRoot | Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $target -Force
        }
        $crashDirectory = Join-Path $RuntimeDirectory 'crash-reports'
        if (Test-Path -LiteralPath $crashDirectory -PathType Container) {
            foreach ($file in @(Get-ChildItem -LiteralPath $crashDirectory -File -Filter '*.txt')) {
                if ($file.LastWriteTimeUtc -lt $earliestUtc) { continue }
                $targetDirectory = New-IsolatedDirectory (Join-Path $Destination 'crash-reports') $reportRoot
                Copy-Item -LiteralPath $file.FullName -Destination $targetDirectory -Force
            }
        }
    }
}
function Invoke-GameTests([string]$Version, [string]$Profile, [string]$JdkPath) {
    $projectPath = Join-Path $repositoryRoot "$Version/inmis"
    $logDirectory = Join-Path $reportRoot "$Version/$Profile/gametest"
    $runtimeDirectory = if ($Version.StartsWith('neoforge')) { Join-Path $projectPath "build/gameTest/$Profile" } else {
        Join-Path $projectPath 'build/gameTest'
    }
    $name = "$Version-$Profile-gametest"
    $gameTestStartedUtc = [DateTime]::UtcNow
    try {
        New-IsolatedDirectory $runtimeDirectory (Join-Path $projectPath 'build') | Out-Null
        $arguments = @('runGameTestServer')
        if ($Version.StartsWith('neoforge')) { $arguments += "-PcompatProfile=$Profile" }
        Write-Host "Running $name; reports: $logDirectory"
        Invoke-IsolatedGradle $projectPath $JdkPath $arguments $logDirectory $name
        $log = Read-Log (Join-Path $logDirectory "$Name-stdout.log")
        if ($log -match 'All ([1-9][0-9]*) required tests passed') {
            $testCount = [int]$Matches[1]
        } else {
            throw 'GameTest process exited without a nonempty required-test success summary.'
        }
        if ($log -match '[1-9][0-9]* required tests failed') { throw 'GameTest output contains required test failures.' }
        if ($Version -eq 'neoforge-26.1.2') {
            # The target also runs vanilla's always_pass; that alone cannot validate Inmis.
            $minimumTests = 33
            if ($testCount -lt $minimumTests) {
                throw "Expected at least $minimumTests native tests including Inmis, but only $testCount ran."
            }
        }
        Add-Check $name 'PASS' 'Required GameTests passed in the native game runtime.' $logDirectory $testCount
    } catch {
        Add-Check $name 'FAIL' $_.Exception.Message $logDirectory
    } finally {
        Copy-RuntimeReports $runtimeDirectory (Join-Path $logDirectory 'runtime-reports') $gameTestStartedUtc
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
    return $configuration | ConvertTo-Json -Depth 8
}
function Assert-LoopbackPortAvailable {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 25576)
    try { $listener.Start() } catch { throw 'Loopback port 25576 is already in use. Stop the other isolated test server first.' }
    finally { $listener.Stop() }
}
function Clear-ExactResult([string]$Path, [string]$RuntimeRoot) {
    $target = Assert-ContainedPath $Path $RuntimeRoot
    if ([IO.Path]::GetFileName($target) -notmatch '^(?:(?:result|server-result)-(?:write|read)|renderer-request)\.txt$') {
        throw "Refusing to remove an unrecognized verification result file: $target"
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
        if ($Version -eq 'neoforge-26.1.2' -or $Version.StartsWith('forge-')) {
            if (-not (Test-Path -LiteralPath $settingsScreenshot -PathType Leaf) -or
                    (Get-Item -LiteralPath $settingsScreenshot).LastWriteTimeUtc -lt $phaseStartedUtc.AddSeconds(-1)) {
                throw 'Upgrades panel screenshot was not written during this phase.'
            }
            Copy-Item -LiteralPath $settingsScreenshot -Destination $logDirectory -Force
            $renderKinds = @('chest-render')
            if ($Profile -eq 'curios') { $renderKinds += 'curios-render' }
            foreach ($renderKind in $renderKinds) {
                $renderScreenshot = Join-Path $clientDirectory "screenshots/inmis-$Phase-$renderKind.png"
                if (-not (Test-Path -LiteralPath $renderScreenshot -PathType Leaf) -or
                        (Get-Item -LiteralPath $renderScreenshot).LastWriteTimeUtc -lt $phaseStartedUtc.AddSeconds(-1)) {
                    throw "Backpack $renderKind screenshot was not written during this phase."
                }
                Copy-Item -LiteralPath $renderScreenshot -Destination $logDirectory -Force
            }
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

try {
    New-IsolatedDirectory $validationRoot $repositoryRoot | Out-Null
    $verificationLock = [IO.File]::Open((Join-Path $validationRoot 'verification.lock'), [IO.FileMode]::OpenOrCreate,
            [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    New-IsolatedDirectory $reportRoot $repositoryRoot | Out-Null
    Write-Host "Runtime verification reports: $reportRoot"
    $gradleWorker = Join-Path $reportRoot 'gradle-worker.ps1'
    $runtimeLauncher = Join-Path $PSScriptRoot 'launch-runtime.ps1'
    $workerContent = @'
param([Parameter(Mandatory=$true)][string]$RequestFile)
$ErrorActionPreference = 'Stop'
$request = Get-Content -LiteralPath $RequestFile -Raw | ConvertFrom-Json
$env:JAVA_HOME = $request.javaHome
$env:DEBUG = ''
Push-Location -LiteralPath $request.projectPath
try {
    # Pass native stderr warnings through without PowerShell treating them as fatal errors.
    $ErrorActionPreference = 'Continue'
    $gradleArguments = @($request.arguments)
    & (Join-Path $request.projectPath 'gradlew.bat') @gradleArguments
    exit $LASTEXITCODE
} finally { Pop-Location }
'@
    Write-IsolatedText $gradleWorker $workerContent $reportRoot
    $needsJava17 = (-not $SkipGameTests -or -not $SkipClient) -and @($Versions | Where-Object { $_.StartsWith('forge-') }).Count -gt 0
    $needsJava21 = $Versions -contains 'neoforge-1.21.1' -and (-not $SkipGameTests -or -not $SkipClient)
    $needsJava25 = $Versions -contains 'neoforge-26.1.2' -and (-not $SkipGameTests -or -not $SkipClient)
    $jdk25 = if ($needsJava25) { Find-Jdk 25 $Java25Home } else { $null }
    $jdk17 = if ($needsJava17) { Find-Jdk 17 $Java17Home } else { $null }
    $jdk21 = if ($needsJava21) { Find-Jdk 21 $Java21Home } else { $null }
    if (-not $SkipGameTests) {
        foreach ($version in $Versions) {
            if ($version.StartsWith('neoforge')) {
                $profiles = @(if ($version -eq 'neoforge-26.1.2') { @($NeoProfiles | Where-Object { $_ -in @('none', 'curios') }) } else { $NeoProfiles })
                if ($profiles.Count -eq 0) { throw "No supported compatibility profiles were selected for $version." }
                $targetJdk = if ($version -eq 'neoforge-26.1.2') { $jdk25 } else { $jdk21 }
                foreach ($profile in $profiles) { Invoke-GameTests $version $profile $targetJdk }
            } else { Invoke-GameTests $version 'curios' $jdk17 }
        }
    }
    if (-not $SkipClient) {
      foreach ($neoVersion in $Versions) {
        $targetJdk = if ($neoVersion.StartsWith('forge-')) { $jdk17 } elseif ($neoVersion -eq 'neoforge-26.1.2') { $jdk25 } else { $jdk21 }
        $supportedClientProfiles = @(if ($neoVersion -eq 'neoforge-26.1.2' -or $neoVersion.StartsWith('forge-')) { @($ClientProfiles | Where-Object { $_ -in @('none', 'curios') }) } else { $ClientProfiles })
        if ($supportedClientProfiles.Count -eq 0) { throw "No supported client profiles were selected for $neoVersion." }
        $neoProject = Join-Path $repositoryRoot "$neoVersion/inmis"
        $runtimeRoot = New-IsolatedDirectory (Join-Path $neoProject 'build/runtime') (Join-Path $neoProject 'build')
        Assert-LoopbackPortAvailable
        New-IsolatedDirectory (Join-Path $runtimeRoot 'server') $runtimeRoot | Out-Null
        New-IsolatedDirectory (Join-Path $runtimeRoot 'client') $runtimeRoot | Out-Null
        $clientOptions = @('fullscreen:false', 'pauseOnLostFocus:false', 'guiScale:2', 'renderDistance:4', 'simulationDistance:5',
                'overrideWidth:1280', 'overrideHeight:720', 'tutorialStep:none', 'enableVsync:false', 'maxFps:30') -join [Environment]::NewLine
        Write-IsolatedText (Join-Path $runtimeRoot 'client/options.txt') $clientOptions $runtimeRoot
        # EULA acceptance is confined to this generated test server.
        Write-IsolatedText (Join-Path $runtimeRoot 'server/eula.txt') 'eula=true' $runtimeRoot
        foreach ($profile in $supportedClientProfiles) {
            $exportDirectory = Join-Path $reportRoot "$neoVersion/$profile/export"
            try {
                Invoke-IsolatedGradle $neoProject $targetJdk @('-I', (Join-Path $PSScriptRoot 'export-runtime-launch.gradle'),
                        'exportRuntimeLaunch', "-PcompatProfile=$profile") $exportDirectory "export-$profile"
                $serverLaunch = Get-Content -LiteralPath (Join-Path $runtimeRoot 'RuntimeServer-launch.json') -Raw | ConvertFrom-Json
                $clientLaunch = Get-Content -LiteralPath (Join-Path $runtimeRoot 'RuntimeClient-launch.json') -Raw | ConvertFrom-Json
                if ([IO.Path]::GetFullPath($serverLaunch.workingDirectory) -ne (Join-Path $runtimeRoot 'server') -or
                    [IO.Path]::GetFullPath($clientLaunch.workingDirectory) -ne (Join-Path $runtimeRoot 'client')) {
                    throw 'Exported launch working directories do not match the isolated runtime directories.'
                }
                Add-Check "$neoVersion-$profile-export" 'PASS' 'Launch exported; single-use Gradle JVM has stopped.' $exportDirectory
            } catch {
                Add-Check "$neoVersion-$profile-export" 'FAIL' $_.Exception.Message $exportDirectory
                continue
            }
            $worldName = "inmis-runtime-verification-$runId-$profile"
            $serverProperties = @('server-ip=127.0.0.1', 'server-port=25576', 'online-mode=false',
                    "level-name=$worldName", 'gamemode=survival', 'difficulty=peaceful', 'spawn-protection=0',
                    'level-type=minecraft:flat', 'generate-structures=false',
                    'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}',
                    'max-players=1', 'view-distance=4', 'simulation-distance=4', 'enable-rcon=false',
                    'enable-query=false', 'enforce-secure-profile=false', 'sync-chunk-writes=true') -join [Environment]::NewLine
            Write-IsolatedText (Join-Path $runtimeRoot 'server/server.properties') $serverProperties $runtimeRoot
            if (Invoke-SmokePhase $neoVersion $profile 'write' $runtimeRoot) {
                Invoke-SmokePhase $neoVersion $profile 'read' $runtimeRoot | Out-Null
            } else {
                Add-Check "$neoVersion-$profile-read" 'SKIP' 'Write failed; no valid persisted fixture exists.' $exportDirectory
            }
            $playerDataDirectory = Join-Path $runtimeRoot "server/$worldName/playerdata"
            if (Test-Path -LiteralPath $playerDataDirectory -PathType Container) {
                $savedDataDirectory = New-IsolatedDirectory (Join-Path $reportRoot "$neoVersion/$profile/playerdata") $reportRoot
                foreach ($file in @(Get-ChildItem -LiteralPath $playerDataDirectory -File -Filter '*.dat')) {
                    Copy-Item -LiteralPath $file.FullName -Destination $savedDataDirectory -Force
                }
            }
        }
      }
    }
    $completed = $true
} finally {
    foreach ($process in $ownedProcesses) { Stop-OwnedProcessTree $process }
    if (Test-Path -LiteralPath $reportRoot -PathType Container) { Save-Summary }
    if ($verificationLock) { $verificationLock.Dispose() }
}
$failedCount = @($checks | Where-Object { $_.status -eq 'FAIL' }).Count
if ($failedCount -gt 0) { throw "$failedCount runtime checks failed. Inspect $(Join-Path $reportRoot 'summary.json')." }
Write-Host "Runtime verification passed. Reports: $reportRoot"
