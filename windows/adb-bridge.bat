@echo off
rem Tiny file-based command bridge (optional).
rem Lets a sandboxed assistant/script run commands on this PC by writing them
rem to bridge\cmd.txt and reading bridge\out.txt. Close the window to stop it.
rem SECURITY: anything written to cmd.txt is executed. Only run it while you need it.
title FireReader bridge - close to stop
set "B=%~dp0bridge"
if not exist "%B%" mkdir "%B%"
echo Bridge active in %B%
:loop
if exist "%B%\cmd.txt" call :run
timeout /t 1 /nobreak >nul
goto loop
:run
set C=
set /p C=<"%B%\cmd.txt"
del "%B%\cmd.txt"
echo ^> %C%
> "%B%\job.bat" echo @%C%
cmd /c ""%B%\job.bat" > "%B%\out.tmp" 2>&1 <nul"
copy /y "%B%\out.tmp" "%B%\out.txt" >nul
echo done> "%B%\done.txt"
goto :eof
