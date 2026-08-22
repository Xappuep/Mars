# -*- coding: utf-8 -*-
import re
import subprocess
import time
from pathlib import Path

ADB = r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
S = "127.0.0.1:5555"
PKG = "com.mars.planner.debug"
OUT = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\visual\moods")


def adb(*a):
    return subprocess.run([ADB, "-s", S, *a], capture_output=True)


def outb(*a):
    return subprocess.check_output([ADB, "-s", S, *a])


def labs():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = outb("shell", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    return re.findall(r'text="([^"]*)"', x)


def shot(name: str):
    remote = "/sdcard/mars_shot.png"
    adb("shell", "rm", "-f", remote)
    adb("shell", "screencap", "-p", remote)
    time.sleep(0.3)
    dest = OUT / name
    adb("pull", remote, str(dest))
    print(name, dest.stat().st_size if dest.exists() else 0, flush=True)


def launch(mood: str | None = None):
    adb("shell", "am", "force-stop", PKG)
    time.sleep(0.5)
    cmd = ["shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity"]
    if mood:
        cmd += ["--es", "mars_mood", mood]
    adb(*cmd)
    time.sleep(2.8)


moods = [
    ("mars_default", "01_default.png"),
    ("mars_done", "02_done.png"),
    ("mars_working", "03_working.png"),
    ("mars_postponed", "04_postponed.png"),
    ("mars_overdue", "05_overdue.png"),
    ("mars_supportive", "06_supportive.png"),
    ("mars_strict", "07_strict.png"),
]

for key, name in moods:
    launch(key)
    texts = [t for t in labs() if t.strip()]
    print(key, " | ".join(texts[:12]), flush=True)
    shot(name)

print("done", flush=True)
