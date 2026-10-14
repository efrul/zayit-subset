<#
Copies the library of desktop Zayit to Zayit for Android over adb, so the phone needs no 5.5 GB download.
העתקת הספרייה של זית למחשב אל זית לאנדרואיד דרך adb, ללא הורדה של 5.5 ג״ב בטלפון.

  powershell -ExecutionPolicy Bypass -File push-desktop-library.ps1 [-Source DIR] [-Serial SERIAL] [-WithNotes] [-DryRun]

  -Source DIR      desktop library folder (default: the folder of %SEFORIMAPP_DATABASE_PATH%, else the desktop
                   install, %APPDATA%\io.github.kdroidfilter.seforimapp\databases)
  -Serial SERIAL   device to use when several are connected (see `adb devices`)
  -WithNotes       also copy notes and highlights (settings\user_settings.db); they replace the phone's own
  -DryRun          print the adb commands instead of running them

Copies seforim.db, the search indexes seforim.db.lucene\ and seforim.db.lookup.lucene\, lexical.db, catalog.pb and
release_info.txt (~12.2 GB) into /sdcard/Android/data/<app>/files/library/. Needs USB debugging on the phone, the app
installed, and Zayit closed on the computer. No root. Without root, adb cannot push into another app's folder (its
fchown fails there), so each item is pushed to a staging folder in Download and moved in; on the same volume the move
is a rename. Pushed files belong to adb's `shell` user, so they are made writable for the app (SQLite opens the
database read-write).
#>
param([string]$Source = '', [string]$Serial = '', [switch]$WithNotes, [switch]$DryRun, [switch]$Help)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
if ($Help) { Get-Content -LiteralPath $PSCommandPath -Encoding UTF8 | Select-Object -Skip 1 -First 15; exit 0 }
$AppId = 'io.github.kdroidfilter.seforimapp'
$Pkg = 'io.github.kdroidfilter.seforimapp.androidApp'
$Files = "/sdcard/Android/data/$Pkg/files"
$Lib = "$Files/library"
$Stage = '/sdcard/Download/.zayit-push'
# Indexes and small files first, the database last: the app treats the library as installed once seforim.db is there.
$Items = 'seforim.db.lookup.lucene', 'seforim.db.lucene', 'lexical.db', 'catalog.pb', 'release_info.txt', 'seforim.db'
function Say($he, $en) { Write-Host "==> $en"; Write-Host "    $he" }
function Die($he, $en) { Write-Host "ERROR: $en" -ForegroundColor Red; Write-Host "       $he" -ForegroundColor Red; exit 1 }
function SizeOf($p) {
    $item = Get-Item -LiteralPath $p
    if (-not $item.PSIsContainer) { return [long]$item.Length }
    return [long](Get-ChildItem -LiteralPath $p -Recurse -File | Measure-Object Length -Sum).Sum
}

# --- desktop library (FileKit.databasesDir of the desktop app; the desktop installer always unpacks there)
$Default = if ($env:APPDATA) { Join-Path $env:APPDATA "$AppId\databases" }
           elseif ($IsMacOS) { "$HOME/Library/Application Support/$AppId/databases" }
           else { $(if ($env:XDG_DATA_HOME) { $env:XDG_DATA_HOME } else { "$HOME/.local/share" }) + "/$AppId/databases" }
if (-not $Source -and $env:SEFORIMAPP_DATABASE_PATH) {
    $Source = $env:SEFORIMAPP_DATABASE_PATH
    if (Test-Path -LiteralPath $Source -PathType Leaf) { $Source = Split-Path -Parent $Source }
}
if (-not $Source) { $Source = $Default }
$Db = Join-Path $Source 'seforim.db'
if (-not (Test-Path -LiteralPath $Db -PathType Leaf)) { Die "לא נמצא seforim.db בתיקייה $Source. התקינו את זית במחשב או ציינו -Source" "no seforim.db in $Source; install desktop Zayit or pass -Source DIR" }
$NotesDb = Join-Path $Source 'settings\user_settings.db'
if (-not (Test-Path -LiteralPath $NotesDb)) { $NotesDb = Join-Path $Default 'settings\user_settings.db' }
foreach ($wal in "$Db-wal", "$NotesDb-wal") {
    if ((Test-Path -LiteralPath $wal) -and (Get-Item -LiteralPath $wal).Length -gt 0) { Die "סגרו את זית במחשב ונסו שוב ($wal אינו ריק)" "close Zayit on the computer first ($wal is not empty)" }
}
foreach ($d in 'seforim.db.lucene', 'seforim.db.lookup.lucene') {
    if (-not (Test-Path -LiteralPath (Join-Path $Source $d))) { Say "חסר $d`: החיפוש לא יעבוד בטלפון" "warning: $d missing, search will not work on the phone" }
}
if ($WithNotes -and -not (Test-Path -LiteralPath $NotesDb)) { Die "לא נמצא קובץ ההערות $NotesDb" "no notes database at $NotesDb" }
$Present = @($Items | Where-Object { Test-Path -LiteralPath (Join-Path $Source $_) })
$TotalKb = [long](($Present | ForEach-Object { SizeOf (Join-Path $Source $_) } | Measure-Object -Sum).Sum / 1024)
Say "מקור: $Source ($([long]($TotalKb / 1024)) מ״ב)" "source: $Source ($([long]($TotalKb / 1024)) MB): $Present"

# --- adb and the device
$Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
# @(...) keeps a single match an array: otherwise += below would glue the paths into one string.
$candidates = @(@($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' })) |
    Where-Object { $_ } | ForEach-Object { Join-Path $_ 'platform-tools\adb.exe' })
$candidates += (Join-Path $PSScriptRoot 'adb.exe'), (Join-Path $PSScriptRoot 'platform-tools\adb.exe')
if (-not $Adb) { $Adb = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1 }
if (-not $Adb) { if ($DryRun) { $Adb = 'adb' } else { Die 'adb לא נמצא. התקינו Android platform-tools' 'adb not found; install Android platform-tools (or set ANDROID_HOME)' } }
function Full([string[]]$a) { if ($Serial) { @('-s', $Serial) + $a } else { $a } }
# Run: changes the device (printed with -DryRun). Ask: reads from it ($fake is the -DryRun answer).
function Run([string[]]$a) {
    $f = Full $a
    if ($DryRun) { Write-Host "    `$ adb $f"; return $true }
    & $Adb @f | Out-Host
    return $LASTEXITCODE -eq 0
}
function Ask([string]$fake, [string[]]$a) {
    $f = Full $a
    if ($DryRun) { Write-Host "    `$ adb $f"; return $fake }
    return ((& $Adb @f) -join "`n") -replace "`r", ''
}
if (-not $Serial) {
    if ($DryRun) { $Serial = 'DEVICE' } else {
        $devices = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
        if ($devices.Count -eq 0) { Die 'לא נמצא מכשיר. חברו את הטלפון, הפעילו ניפוי באגים ב-USB ואשרו את המחשב בטלפון' 'no device; connect the phone, enable USB debugging and accept this computer on the phone' }
        if ($devices.Count -gt 1) { Die "מחוברים כמה מכשירים, בחרו אחד עם -Serial: $devices" "several devices, pick one with -Serial: $devices" }
        $Serial = $devices[0]
    }
}
Say "מכשיר: $Serial" "device: $Serial"
if ((Ask "package:/data/app/$Pkg/base.apk" @('shell', 'pm', 'path', $Pkg)) -notmatch '^package:') { Die 'זית לאנדרואיד אינו מותקן במכשיר' "Zayit for Android ($Pkg) is not installed on the device" }

# --- the app's folder exists only once it has run
$probe = @('shell', "[ -d $Files ] && echo yes")
if ((Ask 'yes' $probe) -ne 'yes') {
    Say 'מפעיל את היישום פעם אחת ליצירת התיקייה שלו' 'starting the app once so it creates its folder'
    $null = Run @('shell', 'am', 'start', '-n', "$Pkg/io.github.kdroidfilter.seforimapp.android.MainActivity")
    for ($n = 0; $n -lt 30 -and (Ask 'yes' $probe) -ne 'yes'; $n++) { Start-Sleep -Seconds 2 }
    if ((Ask 'yes' $probe) -ne 'yes') { Die "התיקייה $Files לא נוצרה. פתחו את היישום ונסו שוב" "$Files was not created; open the app once and retry" }
}
$null = Run @('shell', 'am', 'force-stop', $Pkg)
if (-not (Run @('shell', "mkdir -p $Lib; chmod 777 $Lib 2>/dev/null; touch $Lib/.zayit-push && rm $Lib/.zayit-push"))) { Die "אין הרשאת כתיבה ל-$Lib" "cannot write to $Lib" }

# --- free space: what is free now plus what the old copies take
$df = Ask "Filesystem 1K-blocks Used Available Use% Mounted on`n/dev/fuse 0 0 52428800 0% /storage/emulated" @('shell', 'df', '-k', $Files)
$AvailKb = [long](((($df -split "`n")[1]).Trim() -split '\s+')[3])
$du = Ask '0' @('shell', "cd $Lib && du -sk $Items 2>/dev/null; true")
$OldKb = [long](($du -split "`n" | Where-Object { $_ -match '^\d+' } | ForEach-Object { [long](($_ -split '\s+')[0]) } | Measure-Object -Sum).Sum)
$NeedKb = $TotalKb + 512 * 1024
Say "פנוי במכשיר: $([long](($AvailKb + $OldKb) / 1024)) מ״ב, נדרש: $([long]($NeedKb / 1024)) מ״ב" "free on device: $([long](($AvailKb + $OldKb) / 1024)) MB (incl. the old library), needed: $([long]($NeedKb / 1024)) MB"
if ($AvailKb + $OldKb -lt $NeedKb) { Die 'אין מספיק מקום פנוי במכשיר' 'not enough free space on the device' }

# --- push. The old database goes first, with anything the source lacks, so an interrupted copy or a
# partial source never pairs an old database with new indexes (or the reverse).
$stale = @('seforim.db', 'seforim.db-wal', 'seforim.db-shm', 'seforim.db-journal', 'release_info.txt') + @($Items | Where-Object { $Present -notcontains $_ })
$null = Run @('shell', "cd $Lib && rm -rf $stale")
if (-not (Run @('shell', "rm -rf $Stage; mkdir -p $Stage"))) { Die "אין הרשאת כתיבה ל-$Stage" "cannot write to $Stage" }
foreach ($i in $Present) {
    Say "מעתיק $i" "pushing $i"
    # Not through Run: only at the top level does adb get the console itself and show its progress (gigabytes take minutes).
    $push = Full @('push', (Join-Path $Source $i), "$Stage/$i")
    if ($DryRun) { Write-Host "    `$ adb $push" } else { & $Adb @push; if ($LASTEXITCODE -ne 0) { Die "ההעתקה של $i נכשלה" "adb push of $i failed" } }
    # Files 666 / folders 777: the app is not their owner but must read, write (SQLite WAL) and later delete them.
    # chmod after the move: Download is FUSE and ignores it, Android/data/<app> is the ext4 volume itself.
    $fix = "rm -rf $Lib/$i && mv $Stage/$i $Lib/$i && find $Lib/$i -type d -exec chmod 777 {} + && find $Lib/$i -type f -exec chmod 666 {} +"
    if (-not (Run @('shell', $fix))) { Die "לא ניתן להעביר את $i לתיקיית היישום" "could not move $i into the app's folder" }
}
if ($WithNotes) {
    Say 'מעתיק הערות והדגשות (יחליפו את אלה שבטלפון)' "pushing notes and highlights (they replace the phone's)"
    $null = Run @('shell', "mkdir -p $Stage $Files/import; chmod 777 $Files/import 2>/dev/null; rm -f $Files/import/user_settings.db*")
    if (-not (Run @('push', $NotesDb, "$Stage/user_settings.db"))) { Die 'ההעתקה של ההערות נכשלה' 'adb push of the notes failed' }
    if (-not (Run @('shell', "mv $Stage/user_settings.db $Files/import/user_settings.db && chmod 666 $Files/import/user_settings.db"))) { Die 'לא ניתן להעביר את ההערות לתיקיית היישום' "could not move the notes into the app's folder" }
}
$null = Run @('shell', "rm -rf $Stage")

Say 'הושלם. פתחו את זית בטלפון; אם יופיע מסך ההתקנה, בחרו "שימוש בספרייה הקיימת"' 'done. Open Zayit on the phone; if the setup screens appear, choose "use existing library"'
$null = Run @('shell', 'ls', '-l', $Lib)
