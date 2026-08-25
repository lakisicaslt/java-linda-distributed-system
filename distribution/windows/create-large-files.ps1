$ErrorActionPreference = "Stop"

$dataDirectory = Join-Path $PSScriptRoot "test-data\large"
New-Item -ItemType Directory -Force -Path $dataDirectory | Out-Null

$size = 64MB
foreach ($name in @("big-a.bin", "big-b.bin")) {
    $path = Join-Path $dataDirectory $name
    $stream = [System.IO.File]::Open(
        $path,
        [System.IO.FileMode]::Create,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None)
    try {
        $stream.SetLength($size)
    } finally {
        $stream.Dispose()
    }
    Write-Host "Created $path ($size bytes)"
}
