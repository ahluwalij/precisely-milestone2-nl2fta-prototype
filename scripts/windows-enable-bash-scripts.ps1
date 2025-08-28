Param(
    [switch]$NoCRLFConvert
)

$ErrorActionPreference = "Stop"

function Write-Info($m){ Write-Host "[*] $m" -ForegroundColor Cyan }
function Write-Ok($m){ Write-Host "[+] $m" -ForegroundColor Green }
function Write-Warn($m){ Write-Host "[!] $m" -ForegroundColor Yellow }
function Write-Err($m){ Write-Host "[-] $m" -ForegroundColor Red }

# Resolve repo root from this script
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..")
Set-Location $RepoRoot

Write-Info "Enabling native execution of .sh scripts via Git Bash (per-user)"

# 1) Ensure Git Bash is installed
$git = Get-Command git -ErrorAction SilentlyContinue
if (-not $git) {
  Write-Warn "Git not found on PATH. Attempting to install Git for Windows via winget..."
  $winget = Get-Command winget -ErrorAction SilentlyContinue
  if ($winget) {
    winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements | Out-Null
  } else {
    throw "winget not available. Install Git for Windows manually from https://git-scm.com/download/win and re-run."
  }
}

# Locate bash.exe from Git for Windows
$bashCandidates = @(
  "$Env:ProgramFiles\Git\bin\bash.exe",
  "$Env:ProgramFiles\Git\usr\bin\bash.exe",
  "$Env:ProgramFiles(x86)\Git\bin\bash.exe",
  "$Env:ProgramFiles(x86)\Git\usr\bin\bash.exe"
)
$bashPath = $bashCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $bashPath) {
  # Fallback: try PATH
  $bashCmd = Get-Command bash -ErrorAction SilentlyContinue
  if ($bashCmd) { $bashPath = $bashCmd.Path }
}
if (-not $bashPath) { throw "bash.exe not found. Ensure Git for Windows is installed and adds bash to PATH." }
Write-Ok "Found Git Bash: $bashPath"

# 2) Add bash to PATH for current session (idempotent)
if ($Env:Path -notmatch [regex]::Escape((Split-Path -Parent $bashPath))) {
  $Env:Path = (Split-Path -Parent $bashPath) + ";" + $Env:Path
  Write-Info "Added bash directory to PATH for this session"
}

# 3) Associate .sh with Git Bash (per-user, no admin)
#    HKCU\Software\Classes\.sh => sh_auto_file
#    HKCU\Software\Classes\sh_auto_file\shell\open\command => "bash.exe" "%1" %*
Write-Info "Configuring per-user file association for .sh"
$hkcu = [Microsoft.Win32.Registry]::CurrentUser
$classesKey = $hkcu.CreateSubKey('Software\\Classes')
$extKey = $classesKey.CreateSubKey('.sh')
$null = $extKey.SetValue('', 'sh_auto_file', 'String')
$typeKey = $classesKey.CreateSubKey('sh_auto_file')
$openCmdKey = $typeKey.CreateSubKey('shell\\open\\command')
$command = '"' + $bashPath + '" "%1" %*'
$null = $openCmdKey.SetValue('', $command, 'String')
Write-Ok ".sh files associated with Git Bash (per-user)"

# Strengthen association: ensure Explorer uses our ProgID and clear stale user choice
try {
  $fileExtsPath = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.sh'
  $fileExtsKey = $hkcu.CreateSubKey($fileExtsPath)
  $owpKey = $fileExtsKey.CreateSubKey('OpenWithProgids')
  # Add our ProgID as an allowed handler (REG_NONE value)
  $null = $owpKey.SetValue('sh_auto_file', ([byte[]]@()), [Microsoft.Win32.RegistryValueKind]::None)
  # Remove UserChoice so HKCU\Software\Classes mapping is honored (Windows will rebuild this if user changes association)
  try { $fileExtsKey.DeleteSubKey('UserChoice', $false) } catch { }
  Write-Ok "Explorer OpenWithProgids updated for .sh and UserChoice cleared"
} catch {
  Write-Warn "Could not update Explorer FileExts for .sh: $_"
}

# Also set classic assoc/ftype as an extra safeguard (does not require admin for HKCU overlay)
try {
  Write-Info "Configuring assoc/ftype for .sh (fallback)"
  cmd /c "ftype sh_auto_file=\"$bashPath\" \"%1\" %*" | Out-Null
  cmd /c "assoc .sh=sh_auto_file" | Out-Null
  Write-Ok "assoc/ftype configured"
} catch {
  Write-Warn "assoc/ftype configuration failed: $_"
}

# 4) Add .SH to PATHEXT so PowerShell treats .sh as executable
Write-Info "Ensuring .SH in PATHEXT (per-user)"
$pathextCurrent = [Environment]::GetEnvironmentVariable('PATHEXT','User')
if (-not $pathextCurrent) { $pathextCurrent = [Environment]::GetEnvironmentVariable('PATHEXT','Machine') }
if (-not $pathextCurrent) { $pathextCurrent = '.COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC' }
if ($pathextCurrent -notmatch '(?i)(^|;)\.SH(;|$)') {
  $newPathext = $pathextCurrent.TrimEnd(';') + ';.SH'
  [Environment]::SetEnvironmentVariable('PATHEXT', $newPathext, 'User')
  Write-Ok "Added .SH to user PATHEXT"
} else {
  Write-Ok ".SH already present in PATHEXT"
}
# Update current session too
if ($Env:PATHEXT -notmatch '(?i)(^|;)\.SH(;|$)') { $Env:PATHEXT = $Env:PATHEXT.TrimEnd(';') + ';.SH' }

# 5) Optionally normalize line endings of all repo .sh files to LF to avoid bash issues
if (-not $NoCRLFConvert) {
  Write-Info "Converting CRLF->LF for .sh files in repo (No changes if already LF)"
  Get-ChildItem -Path (Join-Path $RepoRoot '*') -Include '*.sh' -Recurse | ForEach-Object {
    try {
      $content = Get-Content $_.FullName -Raw -ErrorAction Stop
      $lf = $content -replace "`r`n", "`n"
      if ($lf -ne $content) { [IO.File]::WriteAllText($_.FullName, $lf) }
    } catch { Write-Warn "Skipping CRLF conversion for $($_.FullName): $_" }
  }
  Write-Ok "Line endings normalized (LF)"
} else {
  Write-Info "Skipping CRLF->LF conversion by request"
}

# 6) Quick self-test: run a trivial bash one-liner and try invoking a repo .sh directly if present
Write-Info "Verifying Git Bash availability"
& "$bashPath" -lc 'echo bash-ok' | Out-Null
Write-Ok "bash is callable"

# Prefer a lightweight script for direct invocation test
$candidate = Get-ChildItem -Path (Join-Path $RepoRoot 'scripts') -Filter '*.sh' -File | Select-Object -First 1
if ($candidate) {
  Write-Info "Testing direct invocation of: $($candidate.FullName)"
  # PowerShell should now treat .sh as executable via PATHEXT + file association
  & $candidate.FullName --help 2>$null | Out-Null
  Write-Ok "Direct .sh invocation appears to work (or script handled --help/no-op)"
} else {
  Write-Warn "No .sh scripts found under scripts/ to test. Skipping direct invocation test."
}

Write-Host ""
Write-Ok "All set. Close and reopen PowerShell terminals to ensure PATHEXT changes apply globally."
Write-Host "You can now run bash scripts directly, e.g.:" -ForegroundColor Gray
Write-Host "  .\scripts\run-dev.sh" -ForegroundColor Gray
Write-Host "  .\scripts\run-eval.sh" -ForegroundColor Gray
Write-Host "  .\scripts\generate-semantic-types.sh --dataset example" -ForegroundColor Gray

exit 0