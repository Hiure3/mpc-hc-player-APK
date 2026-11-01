@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === MPC-HC Player: gerar .exe e instalador ===

where python >nul 2>nul
if errorlevel 1 goto sem_python

python -m venv .venv
if errorlevel 1 goto erro
call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
if errorlevel 1 goto erro
pip install -r requirements.txt
if errorlevel 1 goto erro

pyinstaller --noconfirm --onefile --windowed --name MPC-HC-Player --collect-all webview --collect-all clr_loader --add-data "index.html;." main.py
if errorlevel 1 goto erro

set "ISCC="
if exist "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if exist "%ProgramFiles%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if exist "%LocalAppData%\Programs\Inno Setup 6\ISCC.exe" set "ISCC=%LocalAppData%\Programs\Inno Setup 6\ISCC.exe"
if not defined ISCC goto sem_inno

"%ISCC%" installer.iss
if errorlevel 1 goto erro
echo.
echo PRONTO! Instalador: installer_output\MPC-HC-Player-Setup.exe
echo Portatil:           dist\MPC-HC-Player.exe
pause
exit /b 0

:sem_inno
echo.
echo Executavel gerado em dist\MPC-HC-Player.exe
echo Para criar o instalador, instale o Inno Setup 6 (https://jrsoftware.org/isdl.php) e rode este arquivo de novo.
pause
exit /b 0

:sem_python
echo Python nao encontrado. Instale em https://www.python.org/downloads/ marcando "Add python.exe to PATH".
pause
exit /b 1

:erro
echo.
echo Ocorreu um erro. Veja as mensagens acima.
pause
exit /b 1
