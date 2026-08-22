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


for i in range(4):
    adb("shell", "am", "force-stop", PKG)
    time.sleep(0.7)
    adb(
        "shell",
        "am",
        "start",
        "-n",
        f"{PKG}/com.mars.planner.MainActivity",
        "--es",
        "mars_mood",
        "supportive",
    )
    time.sleep(3.5)
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = outb("shell", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    texts = [t for t in re.findall(r'text="([^"]*)"', x) if t.strip()]
    print("try", i, " | ".join(texts[3:10]), flush=True)
    adb("shell", "screencap", "-p", "/sdcard/mars_shot.png")
    time.sleep(0.3)
    dest = OUT / "06_supportive.png"
    adb("pull", "/sdcard/mars_shot.png", str(dest))
    print("size", dest.stat().st_size, flush=True)
    if dest.stat().st_size > 50000 and any("поддерживает" in t for t in texts):
        break
