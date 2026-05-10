@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6"
set "MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd"

if exist "%MAVEN_BIN%" goto :run

echo Downloading Apache Maven 3.9.6 to %MAVEN_HOME% ...
set "DIST_DIR=%USERPROFILE%\.m2\wrapper\dists"
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%" 2>nul

set "ZIP=%TEMP%\apache-maven-3.9.6-bin-%RANDOM%.zip"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%ZIP%' -UseBasicParsing"

if errorlevel 1 (
  echo Failed to download Maven.
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Expand-Archive -Path '%ZIP%' -DestinationPath '%DIST_DIR%' -Force"

if not exist "%MAVEN_BIN%" (
  echo Maven binary not found after extract: %MAVEN_BIN%
  exit /b 1
)

:run
call "%MAVEN_BIN%" %*
