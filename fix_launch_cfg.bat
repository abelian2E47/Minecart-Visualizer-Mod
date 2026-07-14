@echo off
setlocal enabledelayedexpansion

for %%I in ("%CD%") do set "SHORT_PATH=%%~sI"
set "LONG_PATH=%CD%"

if "%LONG_PATH%"=="%SHORT_PATH%" exit /b 0

set "CFG_FILE=.gradle\loom-cache\launch.cfg"
if exist "%CFG_FILE%" (
    set "TMP_FILE=%CFG_FILE%.tmp"
    powershell -NoProfile -Command "[System.IO.File]::ReadAllText('%CFG_FILE%').Replace('%LONG_PATH%', '%SHORT_PATH%') | Set-Content '!TMP_FILE!' -NoNewline"
    if exist "!TMP_FILE!" (
        move /y "!TMP_FILE!" "%CFG_FILE%" >nul
        echo Patched launch.cfg
    )
)

for %%F in (.idea\runConfigurations\Minecraft_Client.xml .idea\runConfigurations\Minecraft_Server.xml) do (
    if exist "%%F" (
        set "IDEA_TMP=%%F.tmp"
        powershell -NoProfile -Command "$c = [System.IO.File]::ReadAllText('%%F'); $c = $c.Replace('$PROJECT_DIR$', '%SHORT_PATH%'); [System.IO.File]::WriteAllText('!IDEA_TMP!', $c)"
        if exist "!IDEA_TMP!" (
            move /y "!IDEA_TMP!" "%%F" >nul
            echo Patched %%F
        )
    )
)

echo Fix complete: %LONG_PATH% -^> %SHORT_PATH%
