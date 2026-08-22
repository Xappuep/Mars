# -*- coding: utf-8 -*-
"""Capture supportive / strict / full reaction banners."""
from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

ADB = r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
S = "127.0.0.1:5555"
PKG = "com.mars.planner.debug"
OUT = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\visual\moods")


def log(m: str) -> None:
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {m}", flush=True)


def adb(*a):
    return subprocess.run([ADB, "-s", S, *a], capture_output=True)


def outb(*a) -> bytes:
    return subprocess.check_output([ADB, "-s", S, *a])


def shot(name: str) -> None:
    remote = "/sdcard/mars_shot.png"
    adb("shell", "rm", "-f", remote)
    adb("shell", "screencap", "-p", remote)
    time.sleep(0.2)
    dest = OUT / name
    adb("pull", remote, str(dest))
    log(f"shot {name} ({dest.stat().st_size})")


def dump() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    x = outb("shell", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    i = x.find("<?xml")
    return ET.fromstring(x[i:] if i >= 0 else x)


def labs() -> list[str]:
    return [(n.attrib.get("text") or "").strip() for n in dump().iter("node") if (n.attrib.get("text") or "").strip()]


def tap(needle: str, exact: bool = False) -> bool:
    for n in dump().iter("node"):
        t = (n.attrib.get("text") or "").strip()
        ok = (t == needle) if exact else (needle in t)
        if not ok:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.8)
        log(f"tap {t!r}")
        return True
    log(f"MISS {needle!r} :: {labs()[:15]}")
    return False


def back(n: int = 1) -> None:
    for _ in range(n):
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.4)


def launch() -> None:
    adb("shell", "am", "force-stop", PKG)
    time.sleep(0.4)
    adb("shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity")
    time.sleep(2.2)


def type_text(text: str) -> None:
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.3)


def ensure_home() -> None:
    for _ in range(5):
        j = " ".join(labs())
        if "Настроение дня" in j:
            return
        back(1)
    launch()


def main() -> None:
    # --- Supportive: Tasks → Ideas empty ---
    adb("shell", "pm", "clear", PKG)
    launch()
    tap("Задачи", exact=True)
    time.sleep(0.6)
    if tap("Идеи и улучшения"):
        time.sleep(1.0)
        shot("06_supportive.png")
        log("ideas labels: " + " | ".join(labs()[:20]))
        back(1)

    # --- Overdue reaction (full banner) ---
    adb("shell", "pm", "clear", PKG)
    launch()
    tap("＋ Новая задача")
    time.sleep(0.7)
    tap("Название")
    type_text("OverdueReact")
    tap("Сохранить")
    time.sleep(0.9)
    tap("Не выполнено", exact=True)
    time.sleep(0.8)
    back(1)
    ensure_home()
    time.sleep(1.0)
    shot("05_overdue.png")

    # --- Postponed reaction (full banner) ---
    adb("shell", "pm", "clear", PKG)
    launch()
    tap("＋ Новая задача")
    time.sleep(0.7)
    tap("Название")
    type_text("PostReact")
    tap("Сохранить")
    time.sleep(0.9)
    tap("Перенесено", exact=True)
    time.sleep(0.5)
    tap("Перенести")
    time.sleep(0.8)
    back(1)
    ensure_home()
    time.sleep(1.0)
    shot("04_postponed.png")

    # --- Strict reaction: Strict mode + postpone twice ---
    adb("shell", "pm", "clear", PKG)
    launch()
    tap("Настройки", exact=True)
    time.sleep(0.5)
    tap("Строгий")
    time.sleep(0.3)
    back(1)
    ensure_home()
    # first create + postpone
    tap("＋ Новая задача")
    time.sleep(0.7)
    tap("Название")
    type_text("StrictTwice")
    tap("Сохранить")
    time.sleep(0.9)
    tap("Перенесено", exact=True)
    time.sleep(0.4)
    tap("Перенести")
    time.sleep(0.8)
    back(1)
    ensure_home()
    # task moved off today — open from Tasks list
    tap("Задачи", exact=True)
    time.sleep(0.7)
    if tap("StrictTwice"):
        time.sleep(0.6)
        tap("Перенесено", exact=True)
        time.sleep(0.4)
        tap("Перенести")
        time.sleep(1.0)
        back(1)
        # go today to see reaction if still held
        tap("Сегодня", exact=True)
        time.sleep(1.0)
        # reaction may be on previous screen — re-open task and set again if needed
        j = " ".join(labs())
        if "переносили" not in j.lower() and "разбить" not in j:
            # open from tasks again — reaction is set on ViewModel, should show on Today
            pass
        shot("07_strict.png")
        log("strict labels: " + " | ".join(labs()[:25]))
    else:
        log("StrictTwice not found")
        shot("07_strict.png")

    # Also capture working via UI if import stale
    adb("shell", "pm", "clear", PKG)
    launch()
    tap("＋ Новая задача")
    time.sleep(0.7)
    tap("Название")
    type_text("WorkUI")
    tap("В работе", exact=True)
    tap("Сохранить")
    time.sleep(0.8)
    back(1)
    ensure_home()
    time.sleep(1.0)
    shot("03_working.png")
    log("working: " + " | ".join(labs()[:18]))

    adb("shell", "pm", "clear", PKG)
    launch()
    tap("＋ Новая задача")
    time.sleep(0.7)
    tap("Название")
    type_text("DoneUI")
    tap("Выполнено", exact=True)
    tap("Сохранить")
    time.sleep(0.8)
    back(1)
    ensure_home()
    time.sleep(1.0)
    shot("02_done.png")
    log("done: " + " | ".join(labs()[:18]))

    adb("shell", "pm", "clear", PKG)
    launch()
    shot("01_default.png")

    log("done")


if __name__ == "__main__":
    main()
