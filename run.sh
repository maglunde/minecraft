#!/bin/bash
set -e
cd "$(dirname "$0")"

# Bygg og oppdater avhengigheter
mvn compile dependency:copy-dependencies -DincludeScope=runtime -q

# Sjekk om vi kjører i WSL for å gi optimal ytelse og ekte musekontroll på Windows
if grep -qi microsoft /proc/version 2>/dev/null; then
    WIN_JAVA="/mnt/c/Users/maglu/curseforge/minecraft/Install/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon/bin/java.exe"
    if [ -f "$WIN_JAVA" ]; then
        echo "Starter Minecraft Java Clone med Windows Native Java (for perfekt mus og ytelse)..."
        exec "$WIN_JAVA" -Xms4G --enable-native-access=ALL-UNNAMED -cp 'target/classes;target/dependency/*' no.minecraft.Main
    fi
fi

echo "Starter Minecraft Java Clone..."
mvn exec:exec
