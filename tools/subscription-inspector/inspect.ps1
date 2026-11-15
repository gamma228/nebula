# Subscription Inspector CLI
param(
    [string]$UrlFile = "tools/local/sub_url.txt",
    [string]$UserAgent = "Nebula/1.0.0 (Android; sing-box/1.14.4)"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Resolve-Path "$scriptDir\..\.."
Set-Location $projectRoot

$env:JAVA_HOME = "C:\Users\User\tools\jdk-17"
.\gradlew.bat :core:inspectSubscription -PurlFile="$UrlFile" -Pua="$UserAgent" -q --console=plain
