# Set JDK 21 environment
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"

# Target output directories
$DistDir = Join-Path $PSScriptRoot "libs_dist"
$BuildLibsDir = Join-Path $PSScriptRoot "build\libs"

if (-not (Test-Path $DistDir)) {
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Journey Mode Batch Compile Utility " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Compiling 10 Minecraft version JARs (1.21.1 to 1.21.10)..." -ForegroundColor Yellow

# Clean once at the start to ensure a clean build workspace
Write-Host "`nInitializing clean build space..." -ForegroundColor Yellow
.\gradlew.bat clean

# Loop from 1 to 10 to compile and generate files for all target versions
for ($i = 1; $i -le 10; $i++) {
    $mcVer = "1.21.$i"
    
    # Determine correct matching NeoForge version preset
    if ($i -eq 1) {
        $neoVer = "21.1.72"
        $neoRange = "[21.1,21.2)"
    } elseif ($i -eq 2) {
        $neoVer = "21.2.1-beta"
        $neoRange = "[21.2,21.3)"
    } elseif ($i -eq 10) {
        $neoVer = "21.10.64"
        $neoRange = "[21.10,21.11)"
    } else {
        $neoVer = "21.$i.1"
        $neoRange = "[21.$i,21.$($i + 1))"
    }

    Write-Host "`n----------------------------------------" -ForegroundColor DarkGray
    Write-Host "Compiling for Minecraft $mcVer (NeoForge $neoVer)..." -ForegroundColor Cyan
    Write-Host "----------------------------------------" -ForegroundColor DarkGray
    
    # 1. Create/write the version-specific gradle.properties.<version> file
    $PropsContent = @"
org.gradle.jvmargs=-Xmx4G
org.gradle.daemon=true
org.gradle.caching=true
org.gradle.parallel=true

# Minecraft and NeoForge versions
minecraft_version=$mcVer
minecraft_version_range=[$mcVer,1.22)
neoforge_version=$neoVer
neo_version=$neoVer
neo_version_range=$neoRange
loader_version_range=[4,)

# Mod Information
mod_id=journeymode
mod_name=Journey Mode
mod_license=MIT
mod_version=1.6.0N
mod_group_id=com.aryangpt007.journeymode
mod_authors=Aryangpt007
mod_description=Unlock unlimited access to items after collecting enough of them

# Mappings
parchment_minecraft_version=1.21
parchment_mappings_version=2024.07.28
"@
    $PropsFile = Join-Path $PSScriptRoot "gradle.properties.$mcVer"
    Set-Content -Path $PropsFile -Value $PropsContent -Force

    # 2. Switch the active gradle.properties file to this version
    powershell -ExecutionPolicy Bypass -File .\switch_version.ps1 -Version $mcVer -NeoVersion $neoVer | Out-Null

    # 3. Clean processed resources to force Gradle to run ProcessResources for the new properties
    if (Test-Path (Join-Path $PSScriptRoot "build\resources")) {
        Remove-Item -Path (Join-Path $PSScriptRoot "build\resources") -Recurse -Force | Out-Null
    }

    # 4. Run Gradle build task (jar)
    .\gradlew.bat jar
    
    # 5. Copy the generated JAR to libs_dist
    $TargetJarName = "journeymode-1.6.0N-$mcVer.jar"
    $JarPath = Join-Path $BuildLibsDir $TargetJarName
    
    if (Test-Path $JarPath) {
        Copy-Item -Path $JarPath -Destination (Join-Path $DistDir $TargetJarName) -Force | Out-Null
        Write-Host "Successfully compiled and verified: $TargetJarName" -ForegroundColor Green
    } else {
        Write-Error "Failed to compile $TargetJarName!"
    }
}

# Restore workspace default properties back to 1.21.1
Write-Host "`nRestoring workspace default configuration (1.21.1)..." -ForegroundColor Yellow
powershell -ExecutionPolicy Bypass -File .\switch_version.ps1 -Version 1.21.1 -NeoVersion 21.1.72 | Out-Null

Write-Host "`n==========================================" -ForegroundColor Green
Write-Host " Batch Build Complete! " -ForegroundColor Green
Write-Host " All 10 customized JARs are located in build\libs\ and libs_dist\!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Get-ChildItem -Path $BuildLibsDir -Filter "journeymode-1.6.0N-*.jar" | Select-Object Name, Length
