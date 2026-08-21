# -*- coding: utf-8 -*-
"""Continue BlueStacks UI test: import + sync."""
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
JSON = OUT / "mars_backup_1787341748162.json"
REPORT = OUT / "FINAL_UI_REPORT.txt"
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
    log(f"shot {name} ({len(raw)} B)")


def dump() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb_out("shell", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    i = xml.find("<?xml")
    if i >= 0:
        xml = xml[i:]
    return ET.fromstring(xml)


def bounds(n) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
    return tuple(map(int, m.groups())) if m else None  # type: ignore


def labels() -> list[str]:
    return [(n.attrib.get("text") or "").strip() for n in dump().iter("node") if (n.attrib.get("text") or "").strip()]


def tap_text(needle: str, contains: bool = True, exact: bool = False) -> bool:
    root = dump()
    for n in root.iter("node"):
        t = (n.attrib.get("text") or "").strip()
        ok = (t == needle) if exact else ((needle in t) if contains else (t == needle))
        if ok:
            b = bounds(n)
            if b:
                x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
                log(f"tap '{t}' {b}")
                adb("shell", "input", "tap", str(x), str(y))
                time.sleep(0.85)
                return True
    log(f"MISS '{needle}' :: {labels()[:40]}")
    return False


def type_text(text: str) -> None:
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.3)


def clear_field(n: int = 30) -> None:
    for _ in range(n):
        adb("shell", "input", "keyevent", "67")


def pick_json_in_system_ui() -> bool:
    time.sleep(1.2)
    shot("16_import_picker.png")
    labs = labels()
    log("picker: " + " | ".join(labs[:60]))
    # try common BlueStacks / DocumentsUI paths
    for candidate in [
        JSON.name,
        "mars_backup_1787341748162",
        "mars_backup",
        "Download",
        "Downloads",
        "Загрузки",
        "Recent",
        "Недавние",
        "Show roots",
        "Показать корни",
    ]:
        if tap_text(candidate, contains=True):
            time.sleep(0.7)
            shot("16b_picker_nav.png")
            if candidate in ("Download", "Downloads", "Загрузки", "Show roots", "Показать корни", "Recent", "Недавние"):
                # navigate then pick file
                if tap_text("mars_backup", contains=True):
                    time.sleep(0.5)
            # confirm
            for conf in ("Открыть", "Open", "Выбрать", "Select", "Done", "ОК", "OK"):
                if tap_text(conf, contains=True):
                    return True
            # sometimes tapping file opens it
            if "Объединить" in " ".join(labels()) or "Импорт" in " ".join(labels()):
                return True
    return "Объединить" in " ".join(labels())


def count_roots_on_tasks() -> int:
    tap_text("Задачи")
    time.sleep(0.8)
    labs = labels()
    a = sum(1 for x in labs if "Osnovnaya_A_otchet" in x)
    b = sum(1 for x in labs if "Osnovnaya_B_korm" in x)
    log(f"list hits A={a} B={b}")
    return a + b


def main() -> int:
    log("=== IMPORT + SYNC UI ===")
    adb("shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity")
    time.sleep(1.5)

    # push json to Download
    adb("shell", "mkdir", "-p", "/sdcard/Download")
    adb("push", str(JSON), f"/sdcard/Download/{JSON.name}")
    log(f"pushed {JSON.name}")

    tap_text("Настройки")
    time.sleep(0.8)
    tap_text("Импорт JSON")
    ok = pick_json_in_system_ui()
    if not ok:
        log("WARN picker failed first try — retry")
        # dismiss and retry via content URI intent?
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.5)
        tap_text("Импорт JSON")
        ok = pick_json_in_system_ui()

    time.sleep(1)
    shot("17_import_dialog_merge.png")
    if not tap_text("Объединить", exact=True):
        tap_text("Объединить", contains=True)
    time.sleep(1.2)
    shot("18_after_merge_msg.png")
    for L in labels():
        if "объединен" in L.lower() or "Данные" in L:
            log("merge msg: " + L)

    hits = count_roots_on_tasks()
    shot("19_tasks_after_merge.png")
    if hits <= 2:
        log("PASS merge: no duplicate root titles on list")
    else:
        log(f"FAIL merge duplicates hits={hits}")

    tap_text("Osnovnaya_A_otchet")
    time.sleep(1)
    shot("20_task_a_after_merge.png")
    joined = " ".join(labels())
    if "Podzadacha_cifry" in joined and "Ideya_grafik" in joined:
        log("PASS links preserved after merge")
    else:
        log("WARN links after merge: " + joined[:400])
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.7)

    # Replace import
    tap_text("Настройки")
    time.sleep(0.6)
    tap_text("Импорт JSON")
    pick_json_in_system_ui()
    time.sleep(1)
    shot("21_import_dialog1.png")
    tap_text("Заменить", contains=True)
    time.sleep(0.9)
    shot("22_import_dialog2_confirm.png")
    # second dialog confirm button exact Заменить
    if not tap_text("Заменить", exact=True):
        tap_text("Заменить", contains=True)
    time.sleep(1.3)
    shot("23_after_replace.png")
    for L in labels():
        if "замен" in L.lower() or "Резерв" in L or "pre_replace" in L:
            log("replace msg: " + L)

    internal = adb("shell", "run-as", PKG, "ls", "files").stdout.decode("utf-8", "replace")
    log("filesDir:\n" + internal)
    if "pre_replace_backup_" in internal:
        log("PASS pre_replace backup exists")
    else:
        log("FAIL/WARN no pre_replace in filesDir")

    # Sync screen
    key = KEY_FILE.read_text(encoding="utf-8").strip()
    tap_text("Синхронизация с ПК")
    time.sleep(1)
    shot("24_sync_screen.png")
    root = dump()
    edits = [bounds(n) for n in root.iter("node") if "EditText" in (n.attrib.get("class") or "")]
    edits = [e for e in edits if e]
    log(f"edit fields {len(edits)}")
    host = "10.0.2.2"
    if len(edits) >= 3:
        for i, val, clears in ((0, host, 24), (1, "8765", 8), (2, key, 40)):
            b = edits[i]
            adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
            time.sleep(0.3)
            clear_field(clears)
            type_text(val)
    tap_text("Сохранить настройки")
    time.sleep(0.5)
    shot("25_sync_filled.png")
    tap_text("Проверить подключение")
    time.sleep(2.5)
    shot("26_sync_check.png")
    for L in labels():
        if any(k in L.lower() for k in ("подключ", "успеш", "копи", "ошиб", "ключ", "не удалось")):
            log("health: " + L)

    before = {p.name for p in BACKUPS.glob("backup_*.json")}
    tap_text("Отправить копию на ПК")
    time.sleep(3)
    shot("27_sync_upload.png")
    for L in labels():
        if any(k in L.lower() for k in ("отправлен", "резерв", "ошиб", "последн", "синхрон")):
            log("upload: " + L)

    after = sorted(BACKUPS.glob("backup_*.json"), key=lambda p: p.stat().st_mtime)
    new = [p for p in after if p.name not in before]
    if new:
        log(f"PASS new PC backup: {new[-1]}")
    elif after:
        latest = after[-1]
        log(f"latest backup {latest} mtime={datetime.fromtimestamp(latest.stat().st_mtime)}")
        if latest.stat().st_mtime > time.time() - 120:
            log(f"PASS recent PC backup: {latest}")
        else:
            log("FAIL no recent PC backup")
    else:
        log("FAIL no PC backups")

    # wrong key
    root = dump()
    edits = [bounds(n) for n in root.iter("node") if "EditText" in (n.attrib.get("class") or "")]
    edits = [e for e in edits if e]
    if len(edits) >= 3:
        b = edits[2]
        adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        clear_field(40)
        type_text("WRONG_KEY_TEST_123")
    tap_text("Сохранить настройки")
    time.sleep(0.4)
    tap_text("Отправить копию на ПК")
    time.sleep(2.5)
    shot("28_wrong_key.png")
    joined = " | ".join(labels())
    if "Неверный ключ сопряжения" in joined:
        log("PASS wrong key message exact")
    else:
        log("FAIL/WARN wrong key text: " + joined[:500])

    hits = count_roots_on_tasks()
    shot("29_tasks_unchanged.png")
    if hits >= 2:
        log("PASS tasks unchanged after wrong key")
    else:
        log(f"WARN task hits after wrong key={hits}")

    REPORT.write_text("\n".join(lines), encoding="utf-8")
    log(f"wrote {REPORT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
