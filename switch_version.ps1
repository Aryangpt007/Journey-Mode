param(
    [string]$Version = "",
    [string]$NeoVersion = ""
)

$ValidVersions = @("1.21.1", "1.21.10")

if ([string]::IsNullOrEmpty($Version)) {
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host " Journey Mode Minecraft/NeoForge Switcher " -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "Available presets:"
    Write-Host " 1) Minecraft 1.21.1  (NeoForge 21.1.72)"
    Write-Host " 2) Minecraft 1.21.10 (NeoForge 21.10.64)"
    Write-Host ""
    $Choice = Read-Host "Select a preset (1-2) or enter custom Minecraft version (e.g. 1.21.4)"
    
    if ($Choice -eq "1") {
        $Version = "1.21.1"
        $NeoVersion = "21.1.72"
    } elseif ($Choice -eq "2") {
        $Version = "1.21.10"
        $NeoVersion = "21.10.64"
    } else {
        $Version = $Choice
    }
}

if ([string]::IsNullOrEmpty($Version)) {
    Write-Error "No version selected or entered. Exiting."
    return
}

# Determine source properties file
$TargetFile = Join-Path $PSScriptRoot "gradle.properties"
$PresetFile = Join-Path $PSScriptRoot "gradle.properties.$Version"

Write-Host "Switching Minecraft/NeoForge target version to: $Version..." -ForegroundColor Yellow

if (Test-Path $PresetFile) {
    # If the preset properties file exists, copy it directly
    Copy-Item -Path $PresetFile -Destination $TargetFile -Force
    Write-Host "Successfully applied preset config from gradle.properties.$Version!" -ForegroundColor Green
} else {
    # Dynamic Search and Replace in active gradle.properties
    if (-not (Test-Path $TargetFile)) {
        Write-Error "Could not find active gradle.properties! Exiting."
        return
    }

    # Ask for custom NeoForge version if not provided
    if ([string]::IsNullOrEmpty($NeoVersion)) {
        $NeoVersion = Read-Host "Enter matching NeoForge version for Minecraft $Version"
        if ([string]::IsNullOrEmpty($NeoVersion)) {
            Write-Error "No NeoForge version provided. Exiting."
            return
        }
    }

    Write-Host "Generating custom config for Minecraft $Version (NeoForge $NeoVersion)..." -ForegroundColor Yellow
    
    # Dynamically compute NeoForge version range [Major.Minor, Major.NextMinor)
    $NeoParts = $NeoVersion -split '\.'
    if ($NeoParts.Count -ge 2) {
        $Major = $NeoParts[0]
        $Minor = [int]$NeoParts[1]
        $NextMinor = $Minor + 1
        $NeoRange = "[$Major.$Minor,$Major.$NextMinor)"
    } else {
        $NeoRange = "[$NeoVersion,)"
    }

    # Read gradle.properties
    $Content = Get-Content $TargetFile -Raw
    
    # Replace version strings
    $Content = $Content -replace 'minecraft_version=.*', "minecraft_version=$Version"
    $Content = $Content -replace 'minecraft_version_range=.*', "minecraft_version_range=[$Version,1.22)"
    $Content = $Content -replace 'neoforge_version=.*', "neoforge_version=$NeoVersion"
    $Content = $Content -replace 'neo_version=.*', "neo_version=$NeoVersion"
    $Content = $Content -replace 'neo_version_range=.*', "neo_version_range=$NeoRange"
    
    # Write updated config to a new version-specific file for caching, and to gradle.properties
    Set-Content -Path $PresetFile -Value $Content -Force
    Copy-Item -Path $PresetFile -Destination $TargetFile -Force
    
    Write-Host "Created new preset template gradle.properties.$Version and applied it!" -ForegroundColor Green
}

# Summary of active versions in gradle.properties
Write-Host "`nActive gradle.properties configuration:" -ForegroundColor Cyan
Get-Content $TargetFile | Where-Object { $_ -match "^minecraft_version|^neoforge_version|^neo_version" }
