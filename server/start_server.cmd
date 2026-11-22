@echo off
setlocal
set "DRIVENEXT_JAVA=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
if exist "%DRIVENEXT_JAVA%" goto run
set "DRIVENEXT_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%DRIVENEXT_JAVA%" goto run
set "DRIVENEXT_JAVA=java"

:run
"%DRIVENEXT_JAVA%" -Dfile.encoding=UTF-8 "%~dp0CarServer.java"
if errorlevel 1 pause
