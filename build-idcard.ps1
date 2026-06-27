# Build id-card.html with embedded logo
$root  = Split-Path -Parent $MyInvocation.MyCommand.Path
$bytes = [System.IO.File]::ReadAllBytes("$root\user-service\src\main\resources\logo.png")
$LOGO  = "data:image/png;base64," + [Convert]::ToBase64String($bytes)

$html  = [System.IO.File]::ReadAllText("$root\id-card-template.html", [System.Text.Encoding]::UTF8)
$html  = $html.Replace("%%LOGO%%", $LOGO)

[System.IO.File]::WriteAllText("$root\id-card.html", $html, [System.Text.Encoding]::UTF8)
$kb = [math]::Round((Get-Item "$root\id-card.html").Length / 1KB, 0)
Write-Host "Built id-card.html -- $kb KB"
