# -*- coding: utf-8 -*-
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
S = "emulator-5554"
OUT = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\visual")
OUT.mkdir(parents=True, exist_ok=True)
PKG = "com.mars.planner.debug"


def adb(*a):
    return subprocess.run([ADB, "-s", S, *a], capture_output=True)


def out(*a):
    return subprocess.check_output([ADB, "-s", S, *a])


def shot(name: str):
    raw = out("exec-out", "screencap", "-p").replace(b"\r\n", b"\n")
    (OUT / name).write_bytes(raw)
    print("shot", name, len(raw))


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = out("shell", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    i = x.find("<?xml")
    return ET.fromstring(x[i:] if i >= 0 else x)


def labs():
    return [(n.attrib.get("text") or "").strip() for n in dump().iter("node") if (n.attrib.get("text") or "").strip()]


def tap(text, contains=True):
    for n in dump().iter("node"):
        t = (n.attrib.get("text") or "").strip()
        if (text in t) if contains else t == text:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(0.9)
            print("tap", t)
            return True
    print("MISS", text, labs()[:20])
    return False


adb("shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity")
time.sleep(2.5)
shot("01_today_mars.png")
print("today", labs()[:25])

# empty-ish: go tasks
tap("Задачи")
time.sleep(0.8)
shot("04_tasks.png")

# settings reduce + sync key
tap("Настройки")
time.sleep(0.8)
shot("05_settings_reduce.png")
tap("Синхронизация с ПК")
time.sleep(1)
shot("06_sync_key_hidden.png")
# eye toggle - content-desc
root = dump()
for n in root.iter("node"):
    d = n.attrib.get("content-desc") or ""
    if "Показать ключ" in d or "Скрыть ключ" in d:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(0.7)
            print("toggled eye", d)
            break
shot("07_sync_key_visible.png")
# hide again for safety in screenshot set - already have visible
adb("shell", "input", "keyevent", "4")
time.sleep(0.5)

# try complete/postpone if a task exists
tap("Сегодня")
time.sleep(0.8)
# open first task-like card if any
for label in labs():
    if label and label not in (
        "Ежедневник Марса",
        "Настроение дня",
        "Сегодня",
        "Задачи",
        "Календарь",
        "Статистика",
        "Настройки",
        "＋ Новая задача",
    ):
        if tap(label):
            break
time.sleep(0.8)
shot("02_task_detail.png")
if tap("Выполнено"):
    time.sleep(1.0)
    shot("02b_done_reaction.png")
adb("shell", "input", "keyevent", "4")
time.sleep(0.5)
# postpone path if still on detail
if "Перенесено" in " ".join(labs()):
    tap("Перенесено")
    time.sleep(1)
    shot("03_postponed_reaction.png")

print("done")
