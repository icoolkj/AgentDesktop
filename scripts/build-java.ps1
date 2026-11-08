$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$LocalEnv = Join-Path $Root "scripts\env.local.ps1"

if (Test-Path $LocalEnv) {
    . $LocalEnv
    Write-Host "[build-java] loaded scripts/env.local.ps1"
} else {
    # Override stale system MAVEN_HOME / JAVA_HOME when known good paths exist
    if (Test-Path "D:\Maven\apache-maven-3.9.9") {
        $env:MAVEN_HOME = "D:\Maven\apache-maven-3.9.9"
    }
    if (Test-Path "D:\Program Files\Java\jdk-21.0.6") {
        $env:JAVA_HOME = "D:\Program Files\Java\jdk-21.0.6"
    }
}

if ($env:MAVEN_HOME) {
    $Mvn = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
} else {
    $Mvn = "mvn"
}

if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if ($env:MAVEN_HOME) {
    $env:Path = "$env:MAVEN_HOME\bin;$env:Path"
}

Write-Host "[build-java] JAVA_HOME=$env:JAVA_HOME"
Write-Host "[build-java] MAVEN_HOME=$env:MAVEN_HOME"

Push-Location $Root
try {
    & $Mvn -f server package -DskipTests
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
