# 校验 gradle-9.7.1-bin.zip 是否完整（官方 151,433,392 字节）
# 用法：右键“使用 PowerShell 运行”，或在 Studio 终端执行：
#   powershell -ExecutionPolicy Bypass -File .\verify-gradle.ps1

$ErrorActionPreference = 'Stop'

$expectedSize = 151433392
$expectedHash = 'acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a'
$zip = Join-Path $PSScriptRoot 'gradle-9.7.1-bin.zip'

if (-not (Test-Path $zip)) {
    Write-Host "找不到 $zip" -ForegroundColor Red
    Write-Host "请先下载 gradle-9.7.1-bin.zip 并放到这个文件夹。" -ForegroundColor Yellow
    exit 1
}

$size = (Get-Item $zip).Length
Write-Host ("文件大小 : {0:N0} 字节" -f $size)
Write-Host ("官方大小 : {0:N0} 字节" -f $expectedSize)

Write-Host "正在计算 SHA-256（100 多 MB，几秒钟）..."
$hash = (Get-FileHash -Path $zip -Algorithm SHA256).Hash.ToLower()
Write-Host "实际 SHA-256 : $hash"
Write-Host "官方 SHA-256 : $expectedHash"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($zip)
$hasBin = ($z.Entries | Where-Object { $_.FullName -eq 'gradle-9.7.1/bin/gradle.bat' }).Count -gt 0
$hasLauncher = ($z.Entries | Where-Object { $_.FullName -match '^gradle-9\.7\.1/lib/gradle-launcher-.*\.jar$' }).Count -gt 0
$z.Dispose()

Write-Host "包含 bin/gradle.bat      : $hasBin"
Write-Host "包含 gradle-launcher jar : $hasLauncher"

if (($size -eq $expectedSize) -and ($hash -eq $expectedHash) -and $hasBin -and $hasLauncher) {
    Write-Host ""
    Write-Host "OK：这个 zip 是完整的，回 Studio 点 Sync 即可。" -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host "这个 zip 不完整，别用，重新下载。" -ForegroundColor Red
    Write-Host "腾讯镜像：https://mirrors.cloud.tencent.com/gradle/gradle-9.7.1-bin.zip" -ForegroundColor Yellow
    exit 1
}
