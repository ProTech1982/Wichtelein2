# Setzt die vier Signatur-Secrets im GitHub-Repository Wichtelein2 (nur lokal ausfuehren, nichts wird angezeigt oder gespeichert).
#
# Aufruf (Beispiel, Ordner des Schluessel-Generators, der release.keystore und die Datei mit den Passwoertern enthaelt):
#   powershell -ExecutionPolicy Bypass -File tools\secrets-setzen.ps1 -Ordner "C:\Users\keine\Downloads\apk-signing-generator\dist" -Passwortdatei liesmich_nicht.txt
#
# Die Passwortdatei hat Zeilen wie  keystore_password = ...,  key_alias = ...,  key_password = ...  (Ausgabe von gen_signing.py).
param(
    [Parameter(Mandatory = $true)][string]$Ordner,
    [string]$Passwortdatei = "secrets.txt",
    [string]$Keystore = "release.keystore",
    [string]$Repo = "ProTech1982/Wichtelein2"
)
$ErrorActionPreference = "Stop"
$ks = Join-Path $Ordner $Keystore
$pw = Join-Path $Ordner $Passwortdatei
if (-not (Test-Path $ks)) { throw "Keystore nicht gefunden: $ks" }
if (-not (Test-Path $pw)) { throw "Passwortdatei nicht gefunden: $pw" }

$werte = @{}
foreach ($zeile in Get-Content $pw) {
    if ($zeile -match '^\s*([A-Za-z_]+)\s*=\s*(.+?)\s*$') { $werte[$Matches[1].ToLower()] = $Matches[2] }
}
foreach ($k in "keystore_password", "key_alias", "key_password") {
    if (-not $werte.ContainsKey($k)) { throw "In der Passwortdatei fehlt: $k" }
}
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($ks))

$b64 | gh secret set ANDROID_KEYSTORE_BASE64 -R $Repo
$werte["keystore_password"] | gh secret set ANDROID_KEYSTORE_PASSWORD -R $Repo
$werte["key_alias"] | gh secret set ANDROID_KEY_ALIAS -R $Repo
$werte["key_password"] | gh secret set ANDROID_KEY_PASSWORD -R $Repo
Write-Host "Fertig: vier Secrets in $Repo gesetzt. Danach: Push auf main baut die signierte APK."
