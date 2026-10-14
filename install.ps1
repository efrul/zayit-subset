# Builds the app and installs it per user in %LOCALAPPDATA%\Programs\Zayit Subset, with a Start-menu shortcut.
# The app: Kotlin + Compose + Jewel (Zayit 0.6.4's stack), run on JetBrains Runtime 25 from ~\.jdks\jbr-25.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
$runtime = Join-Path $env:USERPROFILE '.jdks\jbr-25'
if (-not (Test-Path "$runtime\bin\javaw.exe")) { throw "JetBrains Runtime 25 is missing at $runtime" }

Push-Location $root
try { & .\gradlew.bat --console=plain -q appLibs; if ($LASTEXITCODE) { throw 'gradle appLibs failed' } } finally { Pop-Location }

$dest = Join-Path $env:LOCALAPPDATA 'Programs\Zayit Subset'
Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($dest, 'OrdinalIgnoreCase') } | Stop-Process -Force
Start-Sleep -Milliseconds 500
if (Test-Path $dest) { Get-ChildItem $dest | Where-Object { $_.Name -ne 'runtime' } | Remove-Item -Recurse -Force }
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item "$root\build\app\lib" "$dest\lib" -Recurse -Force
# The runtime is copied only when it changed (it is the big part).
$stamp = Join-Path $dest 'runtime\release'
if (-not (Test-Path $stamp) -or ((Get-Content $stamp -Raw) -ne (Get-Content "$runtime\release" -Raw))) {
    if (Test-Path "$dest\runtime") { Remove-Item "$dest\runtime" -Recurse -Force }
    Copy-Item $runtime "$dest\runtime" -Recurse -Force
}
$fw = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319"
& "$fw\csc.exe" /nologo /codepage:65001 /optimize+ /target:winexe /platform:x64 "/win32icon:$root\src\main\resources\drawable\app.ico" "/out:$dest\ZayitSubset.exe" "$root\launcher\Launcher.cs"
if ($LASTEXITCODE) { throw 'launcher build failed' }

$lnk = Join-Path ([Environment]::GetFolderPath('Programs')) 'ספרייה מוקטנת לזית.lnk'
$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath = "$dest\ZayitSubset.exe"; $s.WorkingDirectory = $dest; $s.IconLocation = "$dest\ZayitSubset.exe,0"
$s.Description = 'יצירת ספרייה מוקטנת של זית לטלפון'
$s.Save()
"installed: $dest ({0:N0} MB)" -f ((Get-ChildItem $dest -Recurse -File | Measure-Object Length -Sum).Sum / 1MB)
