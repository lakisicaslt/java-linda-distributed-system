param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$distributionPath = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "dist"))
$targetPath = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "target"))

if (-not $distributionPath.StartsWith(
        $projectRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Distribution path is outside the project directory"
}

Push-Location $projectRoot
try {
    $mavenArguments = if ($SkipTests) {
        @("package", "-DskipTests")
    } else {
        @("clean", "package")
    }
    & mvn @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed"
    }

    if (Test-Path -LiteralPath $distributionPath) {
        Remove-Item -LiteralPath $distributionPath -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $distributionPath | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $distributionPath "lib") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $distributionPath "jobs") | Out-Null

    $moduleJars = @{
        "kdp-common\target\kdp-common-1.0-SNAPSHOT.jar" = "kdp-common.jar"
        "kdp-linda\target\kdp-linda-1.0-SNAPSHOT.jar" = "kdp-linda.jar"
        "kdp-runner\target\kdp-runner-1.0-SNAPSHOT.jar" = "kdp-runner.jar"
        "kdp-server\target\kdp-server-1.0-SNAPSHOT.jar" = "kdp-server.jar"
        "kdp-worker\target\kdp-worker-1.0-SNAPSHOT.jar" = "kdp-worker.jar"
        "kdp-client\target\kdp-client-1.0-SNAPSHOT.jar" = "kdp-client.jar"
    }
    foreach ($entry in $moduleJars.GetEnumerator()) {
        Copy-Item -LiteralPath (Join-Path $projectRoot $entry.Key) `
            -Destination (Join-Path $distributionPath "lib\$($entry.Value)")
    }
    Copy-Item -LiteralPath (Join-Path $projectRoot "kdp-linda\target\kdp-linda-1.0-SNAPSHOT.jar") `
        -Destination (Join-Path $distributionPath "kdp-linda.jar")

    Copy-Item -Path (Join-Path $projectRoot "distribution\windows\*") `
        -Destination $distributionPath -Recurse
    Copy-Item -LiteralPath (Join-Path $projectRoot "distribution\configurations") `
        -Destination $distributionPath -Recurse
    Copy-Item -LiteralPath (Join-Path $projectRoot "distribution\test-data") `
        -Destination $distributionPath -Recurse

    $demoClasses = Join-Path $projectRoot "kdp-demo-jobs\target\classes"
    $demoJobs = @{
        "file-job.jar" = "rs.ac.bg.etf.kdp.demo.FileProcessingJob"
        "linda-job.jar" = "rs.ac.bg.etf.kdp.demo.LindaJob"
        "eval-job.jar" = "rs.ac.bg.etf.kdp.demo.EvalJob"
        "large-files-job.jar" = "rs.ac.bg.etf.kdp.demo.LargeFilesJob"
    }
    foreach ($entry in $demoJobs.GetEnumerator()) {
        $jobJar = Join-Path $distributionPath "jobs\$($entry.Key)"
        & jar --create --file $jobJar --main-class $entry.Value -C $demoClasses .
        if ($LASTEXITCODE -ne 0) {
            throw "Could not create $($entry.Key)"
        }
    }

    New-Item -ItemType Directory -Force -Path $targetPath | Out-Null
    $zipPath = Join-Path $targetPath "java-linda-lab.zip"
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $distributionPath "*") `
        -DestinationPath $zipPath -CompressionLevel Optimal

    Write-Host ""
    Write-Host "Distribution created: $distributionPath"
    Write-Host "ZIP package created: $zipPath"
} finally {
    Pop-Location
}
