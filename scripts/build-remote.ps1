$ErrorActionPreference = 'Stop'
$tvProject = Split-Path $PSScriptRoot -Parent
$tvCompiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework/v4.0.30319/csc.exe'
if (-not (Test-Path -LiteralPath $tvCompiler)) { throw '需要安装 .NET Framework 4.x 的 C# 编译器' }
$tvRemoteSource = Join-Path $PSScriptRoot 'RemoteDebug.cs'
$tvRemoteOutput = Join-Path $tvProject 'LibreTV-Remote.exe'
& $tvCompiler /nologo /target:winexe /optimize+ /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "/out:$tvRemoteOutput" $tvRemoteSource
if ($LASTEXITCODE -ne 0) { throw '遥控器工具编译失败；若 EXE 正在运行，请先关闭后重试' }
Get-Item -LiteralPath $tvRemoteOutput | Select-Object FullName, Length
