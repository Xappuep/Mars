# -*- coding: utf-8 -*-
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
S = "emulator-5554"
OUT = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\bluestacks")
PKG = "com.mars.planner.debug"
FN = "mars_backup_1787341748162.json"


def adb(*a):
    return subprocess.run([ADB, "-s", S, *a], capture_output=True)


def out(*a):
    return subprocess.check_output([ADB, "-s", S, *a])


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = out("shell", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    i = x.find("<?xml")
    return ET.fromstring(x[i:] if i >= 0 else x)


def labs():
    return [(n.attrib.get("text") or "").strip() for n in dump().iter("node") if (n.attrib.get("text") or "").strip()]


def tap_xy(x, y):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.9)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.5)


def shot(name):
    raw = out("exec-out", "screencap", "-p").replace(b"\r\n", b"\n")
    (OUT / name).write_bytes(raw)
    print("shot", name)


# leave picker
for _ in range(6):
    L = " ".join(labs())
    if "Загрузки" in L and "Настройки" not in L and "Объединить" not in L:
        back()
    else:
        break

adb("shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity")
time.sleep(1.2)

# open import
root = dump()
for n in root.iter("node"):
    if (n.attrib.get("text") or "") == "Настройки":
        b = n.attrib["bounds"]
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        x1, y1, x2, y2 = map(int, m.groups())
        tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
        break
time.sleep(0.5)
for n in dump().iter("node"):
    if "Импорт JSON" in (n.attrib.get("text") or ""):
        b = n.attrib["bounds"]
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        x1, y1, x2, y2 = map(int, m.groups())
        tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
        break
time.sleep(1.2)
shot("50_picker.png")
print("picker", labs())

# analyze nodes near file
root = dump()
print("--- near file ---")
for n in root.iter("node"):
    b = n.attrib.get("bounds", "")
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    if 450 <= y1 <= 650 or 450 <= y2 <= 650:
        print(
            f"y={y1}-{y2} click={n.attrib.get('clickable')} focusable={n.attrib.get('focusable')} "
            f"class={n.attrib.get('class')} text={(n.attrib.get('text') or '')[:50]!r} bounds={b}"
        )

# Try tapping wider area of file row
for x, y in [(200, 545), (450, 545), (600, 545), (200, 580), (450, 580)]:
    print("try tap", x, y)
    tap_xy(x, y)
    L = " ".join(labs())
    print(" =>", L[:120])
    if "Объединить" in L:
        shot("51_dialog1.png")
        break
else:
    shot("51_still_picker.png")
    print("FAILED to open dialog")
    raise SystemExit(1)

# replace flow
for n in dump().iter("node"):
    t = n.attrib.get("text") or ""
    if "Заменить" in t:
        b = n.attrib["bounds"]
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        x1, y1, x2, y2 = map(int, m.groups())
        tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
        break
time.sleep(0.8)
shot("52_dialog2.png")
print("dialog2", labs())
for n in dump().iter("node"):
    if (n.attrib.get("text") or "").strip() == "Заменить":
        b = n.attrib["bounds"]
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        x1, y1, x2, y2 = map(int, m.groups())
        tap_xy((x1 + x2) // 2, (y1 + y2) // 2)
        break
time.sleep(1.2)
shot("53_after_replace.png")
print("after", labs())
r = adb("shell", "run-as", PKG, "ls", "files")
print("files", r.stdout.decode("utf-8", "replace"))
print("err", r.stderr.decode("utf-8", "replace"))
