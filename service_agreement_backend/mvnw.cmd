@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Startup Batch Script
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
set WRAPPER_PROPERTIES=%~dp0.mvn\wrapper\maven-wrapper.properties
set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

if not exist "%WRAPPER_JAR%" (
  echo Downloading Maven Wrapper...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$props = Get-Content '%WRAPPER_PROPERTIES%' | ConvertFrom-StringData; Invoke-WebRequest -Uri $props.wrapperUrl -OutFile '%WRAPPER_JAR%'"
  if errorlevel 1 exit /b 1
)

java "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
