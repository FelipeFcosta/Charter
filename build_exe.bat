@echo off

REM Prompt for version if not provided
if "%1"=="" (
    set /p VERSION="Enter version number (e.g., 0.23.0): "
) else (
    set VERSION=%1
)

echo Removing old executable...
rmdir /s /q target\output 2>nul

echo Creating Windows executable version %VERSION%...
jpackage --input target --main-jar Charter.jar --icon src/main/resources/icon.ico --app-version "%VERSION%" --vendor Lordszynencja -n Charter -t app-image -d target/output

echo Copying resource files...
xcopy /E /I /Y resources\graphics target\output\Charter\app\graphics
xcopy /E /I /Y resources\languages target\output\Charter\app\languages
xcopy /E /I /Y resources\oggenc target\output\Charter\app\oggenc
copy /Y LICENSE target\output\Charter\app\

echo.
echo Done! Executable created at: target\output\Charter\Charter.exe
pause
