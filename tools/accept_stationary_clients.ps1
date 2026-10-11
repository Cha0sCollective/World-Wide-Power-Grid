param(
    [Parameter(Mandatory=$true)][string]$Artifact,
    [Parameter(Mandatory=$true)][string]$Batch,
    [string[]]$Profiles=@('BASE','PINOUT'),
    [string[]]$Backends=@('NATIVE','JAVA'),
    [ValidateSet('Both','Graphical','Multiplayer')][string]$Mode='Both',
    [switch]$ColdRestarts,
    [string]$Python='python'
)
$ErrorActionPreference='Stop'
if($Batch -notmatch '^[a-z0-9-]+$'){throw 'Batch must be a simple directory label'}
if($Artifact -notmatch '^[a-zA-Z0-9.-]+\.jar$'){throw 'Artifact must be a jar filename in build/libs'}
$artifactPath="build/libs/$Artifact"
if(!(Test-Path -LiteralPath $artifactPath)){throw 'Build the frozen artifact before client acceptance'}
$frozenHash=(Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash
function Confirm-Run([string]$Label) {
    if($LASTEXITCODE -ne 0){throw "$Label failed; keep its logs and use a fresh batch for the correction"}
    if((Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash -ne $frozenHash){throw 'Frozen acceptance artifact changed'}
}
foreach($profile in $Profiles) {
    if($profile -notin @('BASE','PINOUT')){throw 'Unknown installation profile'}
    $source="run/$Batch-yard-$profile"
    $archive="build/distributions/$Batch-$profile.zip"
    if(!(Test-Path -LiteralPath $archive)) {
        if(Test-Path -LiteralPath $source){throw 'Do not rebuild over a previous yard attempt'}
        & $Python tools/run_checked.py -- .\gradlew.bat runPackagedGameTestServer --offline --no-daemon -x build -x jar -x test -I build/pinout-native-controls.init.gradle "-PisolatedArtifactName=$Artifact" "-PtestProfile=$profile" '-PtestBackend=NATIVE' '-PtestNamespaces=wwpg_yard' "-PtestDirectory=$source" '-PrestartPhase=SETUP' *> "build/$Batch-yard-$profile-setup.log"
        Confirm-Run "$profile yard setup"
        & $Python tools/export_stationary_yard.py --run $source --jar $artifactPath --profile $profile --output $archive
        Confirm-Run "$profile exact world export"
    }
    foreach($backend in $Backends) {
        if($backend -notin @('NATIVE','JAVA')){throw 'Unknown solver backend'}
        if($ColdRestarts) {
            $cold="run/$Batch-exported-$profile-$backend"
            & $Python tools/reopen_stationary_yard.py $archive --run $cold
            Confirm-Run "$profile/$backend cold-restart extraction"
            foreach($pass in 1..3) {
                & $Python tools/run_checked.py -- .\gradlew.bat runPackagedGameTestServer --offline --no-daemon -x build -x jar -x test -I build/pinout-native-controls.init.gradle "-PisolatedArtifactName=$Artifact" "-PtestProfile=$profile" "-PtestBackend=$backend" '-PtestNamespaces=wwpg_yard' "-PtestDirectory=$cold" '-PrestartPhase=VERIFY' *> "build/$Batch-exported-$profile-$backend-$pass.log"
                Confirm-Run "$profile/$backend exact ZIP cold restart $pass"
            }
        }
        if($Mode -ne 'Graphical') {
            $tcp="run/$Batch-multiplayer-$profile-$backend"
            & $Python tools/reopen_stationary_yard.py $archive --run "$tcp/server"
            Confirm-Run "$profile/$backend dedicated-server extraction"
            & .\gradlew.bat prepareStationaryMultiplayer --offline --no-daemon -I tools/client_acceptance/multiplayer.gradle "-PclientArtifact=$artifactPath" "-PmultiplayerDirectory=$tcp" "-PtestProfile=$profile" "-PtestBackend=$backend" '-PtestNamespaces=wwpg_multiplayer' '-Pmultiplayer=true' '-PstationaryMultiplayer=true' *> "build/$Batch-multiplayer-$profile-$backend-prepare.log"
            Confirm-Run "$profile/$backend frozen multiplayer launch preparation"
            & $Python tools/run_stationary_multiplayer.py "$tcp/launch.json"
            Confirm-Run "$profile/$backend two real clients"
            & $Python tools/collect_stationary_multiplayer_evidence.py --run $tcp --output "release/0.1.0-beta.5/evidence/$Batch-multiplayer-$profile-$backend"
            Confirm-Run "$profile/$backend multiplayer evidence collection"
        }
        if($Mode -ne 'Multiplayer') {
            $gui="run/$Batch-graphical-$profile-$backend"
            & $Python tools/reopen_stationary_yard.py $archive --run $gui --client
            Confirm-Run "$profile/$backend graphical extraction"
            & $Python tools/run_checked.py --timeout 1200 -- .\gradlew.bat runStationaryWorldClient --offline --no-daemon -I tools/client_acceptance/client.gradle "-PclientArtifact=$artifactPath" "-PclientDirectory=$gui" "-PtestBackend=$backend" "-PtestProfile=$profile" *> "build/$Batch-graphical-$profile-$backend.log"
            Confirm-Run "$profile/$backend graphical acceptance"
            & $Python tools/collect_stationary_client_evidence.py --run $gui --log "build/$Batch-graphical-$profile-$backend.log" --jar $artifactPath --backend $backend --output "release/0.1.0-beta.5/evidence/$Batch-graphical-$profile-$backend"
            Confirm-Run "$profile/$backend graphical evidence collection"
        }
        Write-Output "$profile/$backend $Mode acceptance passed"
    }
}
