@echo off
setlocal enabledelayedexpansion

set "JAVA_EXE="
if exist "%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\java.exe" (
    set "JAVA_EXE=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\java.exe"
) else (
    where java >nul 2>nul
    if !errorlevel! equ 0 (
        set "JAVA_EXE=java"
    )
)

if "%JAVA_EXE%"=="" (
    echo Kjører via bash...
    bash ./run.sh
    exit /b
)

echo Starter Minecraft Java Clone med Windows Native Java...
"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -cp "target/classes;target/dependency/*" no.minecraft.Main
