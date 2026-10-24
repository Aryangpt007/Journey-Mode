#!/usr/bin/env bash
# Deploy the freshly built Journey Mode jars into the local test instances.
#
#   - copies the CURRENT jar for each environment into its matching instance
#   - removes any older journeymode*.jar there first, so a stale jar can never be
#     loaded alongside the new one (Forge/Fabric would either refuse to start or,
#     worse, load the wrong one)
#   - refuses to copy a jar older than that environment's newest source file
#   - never touches anything that isn't a journeymode jar
#
# The -dev / -sources jars are deliberately excluded: they are unremapped and will
# not run in a normal game.

set -u
SRC="F:/Software things/JM"
INST="F:/Minecraft/Instances"

# env|instance folder|jar glob
MAP="
Forge_1_12_2|Forge 1.12.2|journeymode-1.8.2.jar
Forge_1_16_5|Forge 1.16.5|journeymode-1.8.2-1.16.5.jar
Forge_1_19_2|Forge 1.19.2|journeymode-1.8.2-1.19.2.jar
Forge_1_20_1|Forge 1.20.1|journeymode-1.8.2-1.20.1.jar
Fabric_1_16_5|Fabric 1.16.5|journeymode-fabric-1.8.2-1.16.5.jar
Fabric_1_19_2|Fabric|journeymode-fabric-1.8.2-1.19.2.jar
Fabric_1_20_1|Fabric 1.20.1|journeymode-fabric-1.8.2-1.20.1.jar
Fabric_1_21_1|Fabric 1.21.1|journeymode-fabric-1.8.2-1.21.1.jar
NeoForge_1_21_1|Neo 1.21.1|journeymode-1.8.2-1.21.1.jar
NeoForge_26_1_2|Neo Forge 26.1.2|journeymode-1.8.2-26.1.2.jar
"

fail=0
echo "$MAP" | while IFS='|' read -r env folder jarname; do
    [ -z "$env" ] && continue
    jar="$SRC/$env/build/libs/$jarname"
    dest="$INST/$folder/mods"

    if [ ! -f "$jar" ]; then
        printf '%-18s !! jar missing: %s\n' "$env" "$jarname"; fail=1; continue
    fi
    if [ ! -d "$dest" ]; then
        printf '%-18s !! instance folder missing: %s\n' "$env" "$dest"; fail=1; continue
    fi

    # Staleness gate: never ship a jar older than the source it claims to be built from.
    newest_src=$(find "$SRC/$env/src" -name '*.java' -printf '%T@\n' 2>/dev/null | sort -rn | head -1 | cut -d. -f1)
    jar_time=$(stat -c %Y "$jar")
    if [ -n "$newest_src" ] && [ "$jar_time" -lt "$newest_src" ]; then
        printf '%-18s !! STALE jar (source is newer) - rebuild first\n' "$env"; fail=1; continue
    fi

    removed=""
    for old in "$dest"/journeymode*.jar "$dest"/JourneyMode*.jar; do
        [ -e "$old" ] || continue
        rm -f "$old" && removed="$removed $(basename "$old")"
    done

    cp "$jar" "$dest/" || { printf '%-18s !! copy failed\n' "$env"; fail=1; continue; }

    if [ -n "$removed" ]; then
        printf '%-18s -> %-24s (replaced:%s)\n' "$env" "$folder" "$removed"
    else
        printf '%-18s -> %-24s (new)\n' "$env" "$folder"
    fi
done

exit $fail
