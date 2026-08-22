# -*- coding: utf-8 -*-
"""Finish Mars mood screenshots: import retries + status reactions + Ideas."""
from __future__ import annotations

import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import date, datetime
from pathlib import Path

ADB = r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
S = "127.0.0.1:5555"
PKG = "com.mars.planner.debug"
OUT = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\visual\moods")
SEED = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\visual\_seed")
OUT.mkdir(parents=True, exist_ok=True)

TODAY = (date.today() - date(1970, 1, 1)).days
NOW = int(time.time() * 1000)


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
    log(f"shot {name} ({dest.stat().st_size if dest.exists() else 0})")


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
        time.sleep(0.75)
        log(f"tap {t!r}")
        return True
    log(f"MISS {needle!r}")
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


def ensure_home() -> None:
    for _ in range(6):
        j = " ".join(labs())
        if "Настроение дня" in j:
            return
        if "Загрузки" in j or "Открыть" in j or "Импорт:" in j or "Объединить" in j:
            back(1)
            continue
        if "Задача" in j or "Новая задача" in j or "Редактирование" in j:
            back(1)
            continue
        if "Настройки" in j and "Импорт JSON" in j:
            back(1)
            continue
        tap("Сегодня", exact=True)
        time.sleep(0.4)
    launch()


def wait_text(s: str, sec: float = 5.0) -> bool:
    end = time.time() + sec
    while time.time() < end:
        if s in " ".join(labs()):
            return True
        time.sleep(0.3)
    return False


def write_push(name: str, payload: dict) -> None:
    p = SEED / name
    p.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    adb("shell", "mkdir", "-p", "/sdcard/Download")
    adb("push", str(p), f"/sdcard/Download/{name}")


def task(tid, title, status, due, postpone=0):
    return {
        "id": tid,
        "title": title,
        "description": "",
        "dueDateEpochDay": due,
        "dueTimeMinutes": None,
        "reminderAtEpochMillis": None,
        "priority": "normal",
        "category": "mood",
        "status": status,
        "createdAt": NOW,
        "updatedAt": NOW,
        "postponeCount": postpone,
        "postponeReason": None,
        "parentTaskId": None,
        "nestingLevel": 0,
        "relatedToTaskId": None,
        "isDemo": False,
    }


def backup(tasks, motivator="adaptive"):
    return {
        "version": 1,
        "exportedAt": NOW,
        "tasks": tasks,
        "enhancements": [],
        "settings": {
            "motivatorMode": motivator,
            "morningReminderEnabled": False,
            "morningReminderHour": 9,
            "morningReminderMinute": 0,
            "eveningReminderEnabled": False,
            "eveningReminderHour": 21,
            "eveningReminderMinute": 0,
            "defaultSnoozeMinutes": 30,
            "userName": "Mikhail",
        },
    }


def import_merge(fname: str, expect_substr: str | None = None) -> bool:
    for attempt in range(1, 6):
        log(f"import {fname} attempt {attempt}")
        adb("shell", "pm", "clear", PKG)
        time.sleep(0.5)
        launch()
        if not tap("Настройки", exact=True):
            continue
        if not tap("Импорт JSON"):
            continue
        time.sleep(1.0)
        if not tap(fname, exact=True):
            continue
        time.sleep(0.35)
        # Prefer double-tap open (Открыть often fails to return to app)
        tap(fname, exact=True)
        if not wait_text("Объединить", 4.5):
            back(2)
            continue
        if not tap("Объединить", exact=True):
            continue
        time.sleep(1.3)
        ensure_home()
        time.sleep(1.0)
        labels = " | ".join(labs()[:20])
        log("after import: " + labels)
        if expect_substr and expect_substr not in labels:
            log("expected mood text missing, retry")
            continue
        return True
    return False


def type_text(text: str) -> None:
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.3)


def create_and_status(title: str, status: str) -> None:
    ensure_home()
    tap("＋ Новая задача") or tap("Новая задача")
    time.sleep(0.7)
    tap("Название")
    type_text(title)
    if status in ("В работе", "Выполнено"):
        tap(status, exact=True)
    tap("Сохранить") or tap("Создать")
    time.sleep(0.9)
    # landed on detail
    if status in ("Перенесено", "Не выполнено"):
        tap(status, exact=True)
        time.sleep(0.6)
        if status == "Перенесено":
            tap("Перенести") or tap("ОК")
            time.sleep(0.6)
    # stay on today with reaction: back to today
    back(1)
    ensure_home()
    time.sleep(1.0)


def main() -> None:
    # --- 01 default ---
    adb("shell", "pm", "clear", PKG)
    launch()
    shot("01_default.png")

    # --- 02 done mood card ---
    write_push(
        "02_done.json",
        backup([task(1, "Done", "done", TODAY)]),
    )
    if import_merge("02_done.json", "Марс доволен"):
        shot("02_done.png")

    # --- 03 working mood card ---
    write_push(
        "03_working.json",
        backup([task(1, "Working", "in_progress", TODAY)]),
    )
    if import_merge("03_working.json", "Марс сосредоточен"):
        shot("03_working.png")

    # --- 04 postponed mood card ---
    write_push(
        "04_postponed.json",
        backup([task(1, "Postponed", "postponed", TODAY, 1)]),
    )
    if import_merge("04_postponed.json", "Марс озадачен"):
        shot("04_postponed.png")
    else:
        # fallback: reaction with postponed image
        adb("shell", "pm", "clear", PKG)
        launch()
        create_and_status("PostReact", "Перенесено")
        shot("04_postponed.png")

    # --- 05 overdue via NOT_DONE reaction ---
    adb("shell", "pm", "clear", PKG)
    launch()
    create_and_status("OverReact", "Не выполнено")
    shot("05_overdue.png")

    # --- 06 supportive: Ideas empty ---
    adb("shell", "pm", "clear", PKG)
    launch()
    tap("Настройки", exact=True)
    time.sleep(0.4)
    if tap("Идеи и улучшения"):
        time.sleep(0.9)
        shot("06_supportive.png")
        back(1)

    # --- 07 strict: motivator strict + postponeCount>=2 via import then postpone ---
    write_push(
        "07_strict_seed.json",
        backup([task(1, "StrictSeed", "new", TODAY, postpone=2)], motivator="strict"),
    )
    if import_merge("07_strict_seed.json"):
        ensure_home()
        if tap("StrictSeed"):
            time.sleep(0.5)
            tap("Перенесено", exact=True)
            time.sleep(0.5)
            tap("Перенести")
            time.sleep(1.0)
            back(1)
            ensure_home()
            time.sleep(1.0)
            shot("07_strict.png")
        else:
            shot("07_strict.png")
    else:
        # soft fallback: Ideas not available — create and mark not done under strict
        adb("shell", "pm", "clear", PKG)
        launch()
        tap("Настройки")
        tap("Строгий")
        back(1)
        create_and_status("StrictReact", "Не выполнено")
        # still overdue image; try postpone path
        shot("07_strict.png")

    log("done -> " + str(OUT))
    for p in sorted(OUT.glob("0*.png")):
        log(f"  {p.name} {p.stat().st_size}")


if __name__ == "__main__":
    main()
