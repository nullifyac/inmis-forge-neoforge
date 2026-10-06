param([Parameter(Mandatory=$true)][string]$LaunchFile)
$ErrorActionPreference = 'Stop'
$launch = Get-Content -LiteralPath $LaunchFile -Raw | ConvertFrom-Json
foreach ($entry in $launch.environment.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process')
}
# Java argument files avoid Windows' command-line length limit. These values
# are arguments, never shell commands.
$launchArguments = @($launch.jvmArgs) + @('-classpath', $launch.classpath, $launch.mainClass) + @($launch.args)
$quoted = foreach ($argument in $launchArguments) {
    if (([string]$argument).StartsWith('@')) {
        # Java does not expand nested @files, so merge Gradle's argument files.
        Get-Content -LiteralPath ([string]$argument).Substring(1)
    } else {
        '"' + ([string]$argument).Replace('\', '\\').Replace('"', '\"') + '"'
    }
}
$argumentsFile = Join-Path $launch.workingDirectory 'java-launch-args.txt'
[IO.File]::WriteAllLines($argumentsFile, [string[]]$quoted, [Text.UTF8Encoding]::new($false))
Push-Location -LiteralPath $launch.workingDirectory
try {
    # Native JVM warnings on stderr should remain in the launch log.
    $ErrorActionPreference = 'Continue'
    & $launch.java "@$argumentsFile"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
