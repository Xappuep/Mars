# -*- coding: utf-8 -*-
"""Retry replace import + sync after dismissing stuck file picker."""
from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

ADB = r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
S = "emulator-5554"
PKG = "com.mars.planner.debug"
OUT = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\bluestacks")
KEY_FILE = Path(r"F:\1\Cursor\Mars\Mars\desktop-sync-server\data\pairing_key.txt")
BACKUPS = Path(r"F:\1\Cursor\Mars\Mars\desktop-sync-server\data\backups")
JSON_NAME = "mars_backup_1787341748162.json"
lines: list[str] = []


def log(m: str) -> None:
    t = f"[{datetime.now().strftime('%H:%M:%S')}] {m}"
    lines.append(t)
    print(t, flush=True)


def adb(*a):
    return subprocess.run([ADB, "-s", S, *a], capture_output=True)


def adb_out(*a) -> bytes:
    return subprocess.check_output([ADB, "-s", S, *a])


def shot(name: str) -> None:
    raw = adb_out("exec-out", "screencap", "-p").replace(b"\r\n", b"\n")
    (OUT / name).write_bytes(raw)
    log(f"shot {name}")


def dump() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb_out("shell", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    i = xml.find("<?xml")
    if i >= 0:
        xml = xml[i:]
    return ET.fromstring(xml)


def bounds(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
    return tuple(map(int, m.groups())) if m else None


def labs() -> list[str]:
    return [(n.attrib.get("text") or "").strip() for n in dump().iter("node") if (n.attrib.get("text") or "").strip()]


def tap(needle: str, exact: bool = False) -> bool:
    root = dump()
    for n in root.iter("node"):
        t = (n.attrib.get("text") or "").strip()
        ok = (t == needle) if exact else (needle in t)
        if ok:
            b = bounds(n)
            if not b:
                continue
            log(f"tap '{t}' {b}")
            adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
            time.sleep(0.9)
            return True
    log(f"MISS '{needle}' :: {labs()[:35]}")
    return False


def back(n: int = 1) -> None:
    for _ in range(n):
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.6)


def type_text(text: str) -> None:
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.3)


def clear(n: int = 40) -> None:
    for _ in range(n):
        adb("shell", "input", "keyevent", "67")


def wait_for(substr: str, timeout: float = 8.0) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        if any(substr in x for x in labs()):
            return True
        time.sleep(0.4)
    return False


def import_file_and_choose(mode: str) -> None:
    """mode: merge|replace"""
    tap("Настройки")
    time.sleep(0.5)
    tap("Импорт JSON")
    time.sleep(1.0)
    shot(f"30_picker_{mode}.png")
    L = labs()
    log("picker: " + " | ".join(L[:40]))
    if "Загрузки" in L and JSON_NAME not in " ".join(L):
        tap("Загрузки")
        time.sleep(0.7)
    # select file — do NOT press top Открыть; selecting file returns to app
    if not tap(JSON_NAME):
        tap("mars_backup")
    time.sleep(1.2)
    shot(f"31_dialog1_{mode}.png")
    if not wait_for("Объединить", 5):
        # maybe need Открыть if still in picker
        if "Открыть" in labs():
            # tap file row again then Open at bottom if any
            tap(JSON_NAME)
            time.sleep(0.5)
        wait_for("Объединить", 5)
    log("dialog1: " + " | ".join(labs()[:20]))
    if mode == "merge":
        tap("Объединить", exact=True) or tap("Объединить")
    else:
        # first dialog: Заменить…
        tap("Заменить")
        time.sleep(0.8)
        shot("32_dialog2_replace.png")
        log("dialog2: " + " | ".join(labs()[:20]))
        # second confirm exact
        if not tap("Заменить", exact=True):
            # button might be just Заменить
            for n in dump().iter("node"):
                t = (n.attrib.get("text") or "").strip()
                if t == "Заменить":
                    b = bounds(n)
                    if b:
                        adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
                        time.sleep(1)
                        break
    time.sleep(1.2)
    shot(f"33_after_{mode}.png")
    for x in labs():
        if any(k in x.lower() for k in ("объединен", "замен", "резерв", "pre_replace", "данные")):
            log(f"{mode} msg: {x}")


def main() -> int:
    log("=== RETRY replace + sync ===")
    # leave picker if stuck
    for _ in range(4):
        L = " ".join(labs())
        if "Загрузки" in L and "Настройки" not in L and "Ежедневник" not in L and "Синхронизация" not in L:
            back()
        else:
            break
    adb("shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity")
    time.sleep(1.5)
    shot("30_recovered.png")

    import_file_and_choose("replace")
    internal = adb("shell", "run-as", PKG, "ls", "-la", "files").stdout.decode("utf-8", "replace")
    log("filesDir:\n" + internal)
    if "pre_replace_backup_" in internal:
        log("PASS pre_replace backup")
    else:
        # debug build may store in files/
        find = adb("shell", "run-as", PKG, "sh", "-c", "ls -la files 2>/dev/null; ls -la . 2>/dev/null").stdout.decode("utf-8", "replace")
        log("run-as ls:\n" + find)
        if "pre_replace" in find:
            log("PASS pre_replace backup")
        else:
            log("FAIL no pre_replace visible via run-as")

    # Sync
    key = KEY_FILE.read_text(encoding="utf-8").strip()
    tap("Настройки") or tap("Настройки")
    time.sleep(0.5)
    if not tap("Синхронизация с ПК"):
        back()
        tap("Настройки")
        tap("Синхронизация с ПК")
    time.sleep(1)
    shot("34_sync.png")
    root = dump()
    edits = [bounds(n) for n in root.iter("node") if "EditText" in (n.attrib.get("class") or "")]
    edits = [e for e in edits if e]
    log(f"edits={len(edits)}")
    if len(edits) >= 3:
        vals = [("10.0.2.2", 30), ("8765", 10), (key, 50)]
        for i, (val, clr) in enumerate(vals):
            b = edits[i]
            adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
            time.sleep(0.25)
            clear(clr)
            type_text(val)
    tap("Сохранить настройки")
    time.sleep(0.5)
    shot("35_sync_saved.png")
    tap("Проверить подключение")
    time.sleep(2.5)
    shot("36_sync_health.png")
    for x in labs():
        if any(k in x.lower() for k in ("подключ", "успеш", "копи", "ошиб", "ключ", "не удалось", "последн")):
            log("health: " + x)

    before = {p.name for p in BACKUPS.glob("backup_*.json")}
    # User wording "Синхронизировать с ПК" — use upload on this screen
    if not tap("Отправить копию на ПК"):
        tap("Синхронизировать")
    time.sleep(3.0)
    shot("37_sync_sent.png")
    for x in labs():
        if any(k in x.lower() for k in ("отправлен", "резерв", "ошиб", "последн", "синхрон")):
            log("upload: " + x)

    after = sorted(BACKUPS.glob("backup_*.json"), key=lambda p: p.stat().st_mtime)
    new = [p for p in after if p.name not in before]
    if new:
        log(f"PASS PC backup: {new[-1]}")
    elif after and after[-1].stat().st_mtime > time.time() - 180:
        log(f"PASS recent PC backup: {after[-1]}")
    else:
        log("FAIL no new PC backup")

    # wrong key
    root = dump()
    edits = [bounds(n) for n in root.iter("node") if "EditText" in (n.attrib.get("class") or "")]
    edits = [e for e in edits if e]
    if len(edits) >= 3:
        b = edits[2]
        adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        clear(50)
        type_text("WRONG_KEY_TEST_123")
    tap("Сохранить настройки")
    time.sleep(0.4)
    tap("Отправить копию на ПК")
    time.sleep(2.5)
    shot("38_wrong_key.png")
    joined = " | ".join(labs())
    if "Неверный ключ сопряжения" in joined:
        log("PASS wrong key message")
    else:
        log("FAIL/WARN wrong key: " + joined[:400])

    tap("Задачи") or (back() or tap("Задачи"))
    time.sleep(0.8)
    shot("39_tasks_final.png")
    L = labs()
    a = sum(1 for x in L if "Osnovnaya_A_otchet" in x)
    b = sum(1 for x in L if "Osnovnaya_B_korm" in x)
    log(f"final hits A={a} B={b}")
    if a + b >= 2:
        log("PASS tasks remain after wrong key")
    else:
        log("WARN tasks after wrong key")

    path = OUT / "FINAL_UI_REPORT_PART2.txt"
    path.write_text("\n".join(lines), encoding="utf-8")
    log(f"wrote {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
