# -*- coding: utf-8 -*-
"""Full BlueStacks UI verification for Mars Planner export/import/sync."""
from __future__ import annotations

import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

ADB = r"C:\Users\Mikhail\AppData\Local\Android\Sdk\platform-tools\adb.exe"
SERIAL = "emulator-5554"
PKG = "com.mars.planner.debug"
OUT = Path(r"F:\1\Cursor\Mars\Mars\docs\verification\bluestacks")
OUT.mkdir(parents=True, exist_ok=True)
KEY_FILE = Path(r"F:\1\Cursor\Mars\Mars\desktop-sync-server\data\pairing_key.txt")
BACKUPS = Path(r"F:\1\Cursor\Mars\Mars\desktop-sync-server\data\backups")
REPORT: list[str] = []


def adb(*args: str, check: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, check=check)


def adb_out(*args: str) -> bytes:
    return subprocess.check_output([ADB, "-s", SERIAL, *args])


def log(msg: str) -> None:
    line = f"[{datetime.now().strftime('%H:%M:%S')}] {msg}"
    REPORT.append(line)
    print(line, flush=True)


def shot(name: str) -> Path:
    raw = adb_out("exec-out", "screencap", "-p")
    if b"\r\n" in raw[:200]:
        raw = raw.replace(b"\r\n", b"\n")
    path = OUT / name
    path.write_bytes(raw)
    log(f"shot {name} ({len(raw)} B)")
    return path


def dump_ui() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb_out("shell", "cat", "/sdcard/ui.xml").decode("utf-8", errors="replace")
    i = xml.find("<?xml")
    if i >= 0:
        xml = xml[i:]
    (OUT / "_last_ui.xml").write_text(xml, encoding="utf-8")
    return ET.fromstring(xml)


def parse_bounds(b: str) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b or "")
    return tuple(map(int, m.groups())) if m else None  # type: ignore


def labels(root: ET.Element) -> list[str]:
    out = []
    for n in root.iter("node"):
        t = (n.attrib.get("text") or n.attrib.get("content-desc") or "").strip()
        if t:
            out.append(t)
    return out


def find_bounds(root: ET.Element, needle: str, contains: bool = True) -> tuple[int, int, int, int] | None:
    for n in root.iter("node"):
        t = (n.attrib.get("text") or "").strip()
        d = (n.attrib.get("content-desc") or "").strip()
        for label in (t, d):
            if not label:
                continue
            ok = (needle in label) if contains else (label == needle)
            if ok:
                b = parse_bounds(n.attrib.get("bounds", ""))
                if b:
                    return b
    return None


def tap_xy(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.7)


def tap_bounds(b: tuple[int, int, int, int]) -> None:
    tap_xy((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)


def tap_text(needle: str, contains: bool = True, retries: int = 6) -> bool:
    for _ in range(retries):
        root = dump_ui()
        b = find_bounds(root, needle, contains=contains)
        if b:
            log(f"tap '{needle}' {b}")
            tap_bounds(b)
            return True
        time.sleep(0.5)
    log(f"MISS '{needle}' :: {labels(dump_ui())[:35]}")
    return False


def tap_nth_text(needle: str, index: int = 0) -> bool:
    root = dump_ui()
    found = []
    for n in root.iter("node"):
        t = (n.attrib.get("text") or "").strip()
        if needle in t:
            b = parse_bounds(n.attrib.get("bounds", ""))
            if b:
                found.append(b)
    if index < len(found):
        log(f"tap nth={index} '{needle}' {found[index]}")
        tap_bounds(found[index])
        return True
    log(f"MISS nth '{needle}' count={len(found)}")
    return False


def type_text(text: str) -> None:
    escaped = (
        text.replace("\\", "\\\\")
        .replace(" ", "%s")
        .replace("'", "\\'")
        .replace('"', "")
        .replace("&", "")
    )
    adb("shell", "input", "text", escaped)
    time.sleep(0.35)


def back() -> None:
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)


def swipe_up() -> None:
    adb("shell", "input", "swipe", "450", "1200", "450", "400", "400")
    time.sleep(0.6)


def go_tab(name: str) -> None:
    # bottom nav labels are unique enough
    tap_text(name, contains=False) or tap_text(name, contains=True)
    time.sleep(0.8)


def ensure_on_list() -> None:
    root = dump_ui()
    labs = labels(root)
    if any("＋ Новая задача" in x for x in labs):
        return
    if any(x == "Задача" for x in labs) or any("Подзадачи" in x for x in labs):
        back()
        time.sleep(0.5)
    go_tab("Задачи")


def create_root_task(title: str, done: bool = False) -> None:
    ensure_on_list()
    if not tap_text("＋ Новая задача"):
        raise RuntimeError("CTA missing")
    time.sleep(0.8)
    # focus title
    tap_text("Название")
    # clear if any
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(title)
    if done:
        # status chip on create form — only New/In progress/Done
        tap_text("Выполнено")
    if not tap_text("Сохранить"):
        raise RuntimeError("save missing")
    time.sleep(1.0)
    log(f"created root '{title}' done={done}")
    # if landed on detail, go back to list
    root = dump_ui()
    if "Подзадачи" in labels(root):
        back()


def on_detail_add_subtask(title: str) -> None:
    # first "Добавить" under Подзадачи
    if not tap_nth_text("Добавить", 0):
        raise RuntimeError("subtask add")
    time.sleep(0.6)
    tap_text("Название")
    type_text(title)
    tap_text("Сохранить")
    time.sleep(0.8)
    log(f"subtask '{title}'")


def on_detail_add_enhancement(title: str) -> None:
    # may need scroll
    swipe_up()
    if not tap_nth_text("Добавить", 1):
        # try any Добавить after scroll
        if not tap_text("Добавить"):
            raise RuntimeError("enh add")
    time.sleep(0.6)
    tap_text("Название")
    type_text(title)
    tap_text("Сохранить")
    time.sleep(0.8)
    log(f"enhancement '{title}'")


def pull_exports() -> tuple[Path | None, Path | None]:
    base = f"/sdcard/Android/data/{PKG}/files"
    listing = adb_out("shell", "ls", "-1", base).decode("utf-8", errors="replace")
    log("export dir:\n" + listing)
    jsons = sorted([x.strip() for x in listing.splitlines() if "mars_backup_" in x and x.endswith(".json")])
    csvs = sorted([x.strip() for x in listing.splitlines() if "mars_tasks_" in x and x.endswith(".csv")])
    jp = cp = None
    if jsons:
        j = jsons[-1]
        dest = OUT / j
        adb("pull", f"{base}/{j}", str(dest))
        jp = dest
        log(f"JSON pulled: {dest}")
    if csvs:
        c = csvs[-1]
        dest = OUT / c
        adb("pull", f"{base}/{c}", str(dest))
        cp = dest
        log(f"CSV pulled: {dest}")
    return jp, cp


def analyze_csv(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    rows = [r for r in text.strip().splitlines() if r.strip()]
    log(f"CSV rows={len(rows)} (incl header)")
    body = "\n".join(rows[1:])
    assert "Osnovnaya_A_otchet" in body or "Osnovnaya_A" in body
    assert "Osnovnaya_B_korm" in body or "Osnovnaya_B" in body
    assert "Podzadacha" not in body
    assert "Ideya" not in body
    log("PASS CSV: only two roots, no subtask/enhancement titles")


def analyze_json(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    tasks = data.get("tasks") or []
    enh = data.get("enhancements") or []
    roots = [t for t in tasks if not t.get("parentTaskId") and int(t.get("nestingLevel") or 0) == 0]
    subs = [t for t in tasks if t.get("parentTaskId")]
    log(f"JSON roots={len(roots)} subs={len(subs)} enh={len(enh)}")
    assert len(roots) >= 2
    assert len(subs) >= 1
    assert len(enh) >= 1
    log("PASS JSON structure")
    return data


def count_root_task_cards() -> int:
    root = dump_ui()
    # titles we created
    labs = labels(root)
    n = sum(1 for x in labs if "Osnovnaya_A_otchet" in x) + sum(1 for x in labs if "Osnovnaya_B_korm" in x)
    # if scrolled, may miss — also count via dump of all matching
    return n


def main() -> int:
    log("=== FULL UI TEST ===")
    # ensure app foreground
    adb("shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity")
    time.sleep(2)

    # If already on detail of task A from previous run, use it
    root = dump_ui()
    labs = labels(root)
    log("start labels: " + " | ".join(labs[:40]))

    if "Osnovnaya_A_otchet" in " ".join(labs) and "Подзадачи" in labs:
        log("already on task A detail")
        shot("10_task_a_detail.png")
    else:
        go_tab("Задачи")
        shot("10_tasks_before.png")
        # create A if missing
        if "Osnovnaya_A_otchet" not in " ".join(labs):
            create_root_task("Osnovnaya_A_otchet", done=False)
        go_tab("Задачи")
        tap_text("Osnovnaya_A_otchet")
        time.sleep(1)
        shot("10_task_a_detail.png")

    # ensure subtask/enhancement
    labs = labels(dump_ui())
    if "Podzadacha_cifry" not in " ".join(labs):
        on_detail_add_subtask("Podzadacha_cifry")
    labs = labels(dump_ui())
    if "Ideya_grafik" not in " ".join(labs):
        on_detail_add_enhancement("Ideya_grafik")
    shot("11_task_a_linked.png")
    back()

    # create B done if missing
    go_tab("Задачи")
    labs = labels(dump_ui())
    if "Osnovnaya_B_korm" not in " ".join(labs):
        create_root_task("Osnovnaya_B_korm", done=True)
    go_tab("Задачи")
    shot("12_two_roots.png")
    log(f"root title hits on list: {count_root_task_cards()}")

    # --- Export ---
    go_tab("Настройки")
    time.sleep(0.8)
    shot("13_settings.png")
    tap_text("Экспорт JSON")
    time.sleep(1.2)
    shot("14_export_json_msg.png")
    tap_text("Экспорт CSV")
    time.sleep(1.2)
    shot("15_export_csv_msg.png")
    # capture message text
    labs = labels(dump_ui())
    for L in labs:
        if "JSON сохранён" in L or "CSV сохранён" in L or "mars_" in L:
            log("UI path msg: " + L)

    jp, cp = pull_exports()
    assert jp and cp, "exports missing on device"
    data = analyze_json(jp)
    analyze_csv(cp)

    # --- Import merge ---
    go_tab("Настройки")
    # Import needs document picker — OpenDocument. Hard on BlueStacks.
    # Push file to Downloads and use intent, or use adb to grant and click.
    # Strategy: copy export into /sdcard/Download and launch import, then pick.
    adb("shell", "mkdir", "-p", "/sdcard/Download")
    adb("push", str(jp), f"/sdcard/Download/{jp.name}")
    log(f"pushed import source to Download/{jp.name}")

    tap_text("Импорт JSON")
    time.sleep(1.5)
    shot("16_import_picker.png")
    # Try to select file in system picker
    root = dump_ui()
    labs = labels(root)
    log("picker labels: " + " | ".join(labs[:50]))
    picked = False
    if tap_text(jp.name, contains=True) or tap_text("mars_backup", contains=True):
        picked = True
        time.sleep(0.5)
        # sometimes need Open
        tap_text("Открыть", contains=True) or tap_text("Open", contains=True) or tap_text("Выбрать", contains=True)
    if not picked:
        # BlueStacks may show different UI — try Downloads
        tap_text("Download", contains=True) or tap_text("Загрузки", contains=True) or tap_text("Downloads", contains=True)
        time.sleep(0.8)
        shot("16b_downloads.png")
        picked = tap_text("mars_backup", contains=True)
        tap_text("Открыть", contains=True) or tap_text("Open", contains=True)

    time.sleep(1.0)
    shot("17_import_dialog_merge.png")
    if not tap_text("Объединить", contains=False):
        # dialog title might include count
        tap_text("Объединить", contains=True)
    time.sleep(1.2)
    shot("18_after_merge.png")

    go_tab("Задачи")
    time.sleep(0.8)
    shot("19_tasks_after_merge.png")
    hits = count_root_task_cards()
    log(f"after merge root hits={hits}")
    if hits > 2:
        log("FAIL: duplicates after merge")
    else:
        log("PASS: no duplicate roots after merge (hits<=2)")

    # open A check links
    tap_text("Osnovnaya_A_otchet")
    time.sleep(1)
    shot("20_task_a_after_merge.png")
    labs = " ".join(labels(dump_ui()))
    if "Podzadacha_cifry" in labs and "Ideya_grafik" in labs:
        log("PASS: subtask+enhancement still inside task A")
    else:
        log(f"WARN links after merge: {labs[:300]}")
    back()

    # --- Import replace ---
    go_tab("Настройки")
    tap_text("Импорт JSON")
    time.sleep(1.2)
    tap_text("mars_backup", contains=True)
    tap_text("Открыть", contains=True) or tap_text("Open", contains=True)
    time.sleep(1.0)
    shot("21_import_dialog1.png")
    tap_text("Заменить", contains=True)
    time.sleep(0.8)
    shot("22_import_dialog2_confirm.png")
    # confirm second dialog exact button "Заменить"
    tap_text("Заменить", contains=False) or tap_nth_text("Заменить", 0)
    time.sleep(1.2)
    shot("23_after_replace.png")
    labs = labels(dump_ui())
    for L in labs:
        if "Резервная" in L or "pre_replace" in L or "заменены" in L or "Данные" in L:
            log("replace UI msg: " + L)

    # check pre_replace backup in app filesDir (internal)
    # run-as may work for debug builds
    internal = adb("shell", "run-as", PKG, "ls", "files").stdout.decode("utf-8", errors="replace")
    log("internal filesDir:\n" + internal)
    if "pre_replace_backup_" in internal:
        log("PASS: pre_replace backup created")
    else:
        # also check via find
        find_out = adb("shell", "run-as", PKG, "sh", "-c", "ls files").stdout.decode("utf-8", errors="replace")
        log("internal ls again:\n" + find_out)
        if "pre_replace" in find_out:
            log("PASS: pre_replace backup created")
        else:
            log("FAIL/WARN: pre_replace backup not listed via run-as")

    # --- Sync ---
    key = KEY_FILE.read_text(encoding="utf-8").strip()
    go_tab("Настройки")
    tap_text("Синхронизация с ПК")
    time.sleep(1)
    shot("24_sync_screen.png")
    # fill fields: IP, port, key — order of EditTexts
    root = dump_ui()
    edits = []
    for n in root.iter("node"):
        if "EditText" in (n.attrib.get("class") or ""):
            b = parse_bounds(n.attrib.get("bounds", ""))
            if b:
                edits.append(b)
    log(f"sync edittexts={len(edits)}")
    # BlueStacks host gateway
    host = "10.0.2.2"
    if len(edits) >= 3:
        # clear+type IP
        tap_bounds(edits[0])
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        for _ in range(20):
            adb("shell", "input", "keyevent", "67")  # DEL
        type_text(host)
        tap_bounds(edits[1])
        for _ in range(8):
            adb("shell", "input", "keyevent", "67")
        type_text("8765")
        tap_bounds(edits[2])
        for _ in range(40):
            adb("shell", "input", "keyevent", "67")
        type_text(key)
    tap_text("Сохранить настройки")
    time.sleep(0.6)
    shot("25_sync_filled.png")
    tap_text("Проверить подключение")
    time.sleep(2.0)
    shot("26_sync_check.png")
    labs = labels(dump_ui())
    for L in labs:
        low = L.lower()
        if ("Подключ" in L) or ("успеш" in low) or ("Не удалось" in L) or ("ошиб" in low) or ("ключ" in low) or ("копи" in low):
            log("check status: " + L)

    before = set(p.name for p in BACKUPS.glob("backup_*.json"))
    # User asked for "Синхронизировать с ПК" — on Sync screen primary upload is "Отправить копию на ПК"
    # Also Today has "Синхронизировать с ПК" nav. Here use upload button.
    if not tap_text("Отправить копию на ПК"):
        tap_text("Синхронизировать", contains=True)
    time.sleep(2.5)
    shot("27_sync_upload.png")
    labs = labels(dump_ui())
    for L in labs:
        if "Резервная" in L or "отправлена" in L or "Ошибка" in L or "синхронизац" in L.lower() or "Последняя" in L:
            log("sync status: " + L)

    after = sorted(BACKUPS.glob("backup_*.json"), key=lambda p: p.stat().st_mtime)
    new_files = [p for p in after if p.name not in before]
    if new_files:
        log(f"PASS PC backup: {new_files[-1]}")
    elif after:
        log(f"WARN no new backup name; latest={after[-1]} mtime={datetime.fromtimestamp(after[-1].stat().st_mtime)}")
    else:
        log("FAIL no backups on PC")

    # wrong key
    root = dump_ui()
    edits = []
    for n in root.iter("node"):
        if "EditText" in (n.attrib.get("class") or ""):
            b = parse_bounds(n.attrib.get("bounds", ""))
            if b:
                edits.append(b)
    if len(edits) >= 3:
        tap_bounds(edits[2])
        for _ in range(40):
            adb("shell", "input", "keyevent", "67")
        type_text("WRONG_KEY_TEST_123")
    tap_text("Сохранить настройки")
    time.sleep(0.4)
    tap_text("Отправить копию на ПК")
    time.sleep(2.0)
    shot("28_wrong_key.png")
    labs = labels(dump_ui())
    joined = " | ".join(labs)
    if "Неверный ключ сопряжения" in joined:
        log("PASS wrong key message")
    else:
        log("FAIL/WARN wrong-key message not exact. status texts: " + joined[:500])

    go_tab("Задачи")
    shot("29_tasks_after_wrong_key.png")
    hits = count_root_task_cards()
    log(f"tasks after wrong key hits={hits}")
    if hits >= 1:
        log("PASS tasks remain after wrong key")

    report_path = OUT / "FINAL_UI_REPORT.txt"
    report_path.write_text("\n".join(REPORT), encoding="utf-8")
    log(f"report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
