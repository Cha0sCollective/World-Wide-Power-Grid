param(
    [Parameter(Mandatory=$true)][string]$Batch,
    [Parameter(Mandatory=$true)][string]$SourceSha,
    [string[]]$Profiles=@('BASE','PINOUT'),
    [string[]]$Backends=@('NATIVE','JAVA'),
    [ValidateSet('Both','Graphical','Multiplayer')][string]$Mode='Both',
    [switch]$ColdRestarts,
    [string]$Python='python'
)
$ErrorActionPreference='Stop'
if($Batch -notmatch '^[a-z0-9-]+$'){throw 'Batch must be a simple directory label'}
if($SourceSha -notmatch '^[0-9a-f]{40}$'){throw 'SourceSha must identify the frozen source commit'}
$artifacts=@{
    clientArtifact='build/libs/wwpg-0.2.0.jar'
    apiArtifact='build/libs/wwpg-api-1.0.0.jar'
    ceeApiArtifact='build/libs/wwpg-cee-api-1.0.0.jar'
    exampleArtifact='examples/extension-addon/build/libs/wwpg-api-example-1.0.0.jar'
    upstreamExampleArtifact='examples/cee-addon/build/libs/wwpg-upstream-api-example-1.0.0.jar'
}
$frozen=@{}
foreach($entry in $artifacts.GetEnumerator()) {
    if(!(Test-Path -LiteralPath $entry.Value)){throw "Build the frozen artifact first: $($entry.Value)"}
    $frozen[$entry.Key]=(Get-FileHash -LiteralPath $entry.Value -Algorithm SHA256).Hash
}
$artifactArguments=@($artifacts.GetEnumerator() | ForEach-Object { "-P$($_.Key)=$($_.Value)" })
function Confirm-Run([string]$Label) {
    if($LASTEXITCODE -ne 0){throw "$Label failed; retain its logs and use a fresh batch"}
    foreach($entry in $artifacts.GetEnumerator()) {
        if((Get-FileHash -LiteralPath $entry.Value -Algorithm SHA256).Hash -ne $frozen[$entry.Key]) {
            throw "Frozen artifact changed: $($entry.Value)"
        }
    }
}
foreach($profile in $Profiles) {
    if($profile -notin @('BASE','PINOUT')){throw 'Unknown installation profile'}
    $source="run/$Batch-workshop-$profile"
    $archive="build/distributions/$Batch-api-workshop-$profile.zip"
    if(!(Test-Path -LiteralPath $archive)) {
        if(Test-Path -LiteralPath $source){throw 'Use a fresh workshop construction run'}
        & $Python tools/run_checked.py -- ./gradlew runPackagedGameTestServer --offline --no-daemon -x build -x jar -x test '-PtestApiAddon=true' "-PtestProfile=$profile" '-PtestBackend=NATIVE' '-PtestNamespaces=wwpg_api_world' "-PtestDirectory=$source" '-PrestartPhase=SETUP' *> "build/$Batch-workshop-$profile-setup.log"
        Confirm-Run "$profile Workshop setup"
        foreach($pass in 1..3) {
            & $Python tools/run_checked.py -- ./gradlew runPackagedGameTestServer --offline --no-daemon -x build -x jar -x test '-PtestApiAddon=true' "-PtestProfile=$profile" '-PtestBackend=NATIVE' '-PtestNamespaces=wwpg_api_world' "-PtestDirectory=$source" '-PrestartPhase=VERIFY' *> "build/$Batch-workshop-$profile-source-$pass.log"
            Confirm-Run "$profile Workshop source cold restart $pass"
        }
        & $Python tools/export_api_workshop.py --run $source --jar $artifacts.clientArtifact --example $artifacts.exampleArtifact --upstream-example $artifacts.upstreamExampleArtifact --profile $profile --backend NATIVE --output $archive
        Confirm-Run "$profile exact Workshop export"
    }
    foreach($backend in $Backends) {
        if($backend -notin @('NATIVE','JAVA')){throw 'Unknown solver backend'}
        if($ColdRestarts) {
            $cold="run/$Batch-api-cold-$profile-$backend"
            & $Python tools/reopen_stationary_yard.py $archive --run $cold
            Confirm-Run "$profile/$backend exact Workshop extraction"
            foreach($pass in 1..3) {
                & $Python tools/run_checked.py -- ./gradlew runPackagedGameTestServer --offline --no-daemon -x build -x jar -x test '-PtestApiAddon=true' "-PtestProfile=$profile" "-PtestBackend=$backend" '-PtestNamespaces=wwpg_api_world' "-PtestDirectory=$cold" '-PrestartPhase=VERIFY' *> "build/$Batch-api-cold-$profile-$backend-$pass.log"
                Confirm-Run "$profile/$backend Workshop cold restart $pass"
            }
        }
        if($Mode -ne 'Graphical') {
            $tcp="run/$Batch-api-multiplayer-$profile-$backend"
            & $Python tools/reopen_stationary_yard.py $archive --run "$tcp/server"
            Confirm-Run "$profile/$backend dedicated-server extraction"
            & .\gradlew.bat prepareApiWorkshopMultiplayer --offline --no-daemon -I tools/client_acceptance/api_multiplayer.gradle @artifactArguments "-PmultiplayerDirectory=$tcp" "-PtestProfile=$profile" "-PtestBackend=$backend" "-PsourceSha=$SourceSha" '-PtestApiAddon=true' '-PtestNamespaces=wwpg_api_multiplayer' '-Pmultiplayer=true' *> "build/$Batch-api-multiplayer-$profile-$backend-prepare.log"
            Confirm-Run "$profile/$backend frozen TCP launches"
            & $Python tools/run_api_workshop_multiplayer.py "$tcp/launch.json"
            Confirm-Run "$profile/$backend two real clients"
            & $Python tools/collect_api_client_evidence.py --run $tcp --mode multiplayer --output "release/0.2.0/evidence/$Batch-api-multiplayer-$profile-$backend"
            Confirm-Run "$profile/$backend TCP evidence"
        }
        if($Mode -ne 'Multiplayer') {
            $gui="run/$Batch-api-graphical-$profile-$backend"
            & $Python tools/reopen_stationary_yard.py $archive --run $gui --client
            Confirm-Run "$profile/$backend graphical extraction"
            & $Python tools/run_checked.py --timeout 1200 -- ./gradlew runApiWorkshopClient --offline --no-daemon -I tools/client_acceptance/api_client.gradle @artifactArguments "-PclientDirectory=$gui" "-PtestBackend=$backend" "-PtestProfile=$profile" "-PsourceSha=$SourceSha" '-PtestApiAddon=true' *> "build/$Batch-api-graphical-$profile-$backend.log"
            Confirm-Run "$profile/$backend graphical acceptance"
            & $Python tools/collect_api_client_evidence.py --run $gui --mode graphical --log "build/$Batch-api-graphical-$profile-$backend.log" --output "release/0.2.0/evidence/$Batch-api-graphical-$profile-$backend"
            Confirm-Run "$profile/$backend graphical evidence"
        }
        Write-Output "$profile/$backend $Mode API Workshop acceptance passed"
    }
}
