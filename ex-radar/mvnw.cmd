@REM Maven Wrapper for EX Radar (distribution-only mode)
@ECHO OFF
SETLOCAL
SET "MVNW_DIR=%~dp0"
SET "MVNW_VERSION=3.9.11"
SET "MVNW_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MVNW_VERSION%"
SET "MVNW_MAVEN=%MVNW_HOME%\apache-maven-%MVNW_VERSION%\bin\mvn.cmd"
IF NOT EXIST "%MVNW_MAVEN%" (
  IF NOT EXIST "%MVNW_HOME%" MKDIR "%MVNW_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:TEMP 'apache-maven-%MVNW_VERSION%-bin.zip'; Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVNW_VERSION%/apache-maven-%MVNW_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -Force $zip '%MVNW_HOME%'; Remove-Item $zip"
  IF ERRORLEVEL 1 EXIT /B 1
)
CALL "%MVNW_MAVEN%" %*
EXIT /B %ERRORLEVEL%
