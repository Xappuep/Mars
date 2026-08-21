@ECHO OFF
cd /d "%~dp0"
python -m pip install -r requirements.txt
python server.py
