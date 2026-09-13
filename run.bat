@echo off
setlocal

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Java ble ikke funnet i PATH. Kjører via bash...
    bash ./run.sh
    exit /b
)

echo Starter Minecraft Java Clone med Windows Native Java...
java --enable-native-access=ALL-UNNAMED -cp "target/classes;target/dependency/*" no.minecraft.Main