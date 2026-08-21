# -*- coding: utf-8 -*-
"""UI smoke test of Mars Planner in BlueStacks via adb."""
from __future__ import annotations

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
REPORT: list[str] = []


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    cmd = [ADB, "-s", SERIAL, *args]
    return subprocess.run(cmd, capture_output=True, check=check)


def adb_out(*args: str) -> bytes:
    return subprocess.check_output([ADB, "-s", SERIAL, *args])


def log(msg: str) -> None:
    line = f"[{datetime.now().strftime('%H:%M:%S')}] {msg}"
    REPORT.append(line)
    print(line)


def shot(name: str) -> Path:
    raw = adb_out("exec-out", "screencap", "-p")
    # BlueStacks sometimes inserts CR; normalize PNG
    if raw.startswith(b"\r\n"):
        raw = raw.replace(b"\r\n", b"\n")
    elif b"\r\nIHDR" in raw[:64]:
        raw = raw.replace(b"\r\n", b"\n")
    path = OUT / name
    path.write_bytes(raw)
    log(f"screenshot {path.name} ({len(raw)} bytes)")
    return path


def dump_ui() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb_out("shell", "cat", "/sdcard/ui.xml").decode("utf-8", errors="replace")
    # strip noise before <?xml
    i = xml.find("<?xml")
    if i > 0:
        xml = xml[i:]
    (OUT / "_last_ui.xml").write_text(xml, encoding="utf-8")
    return ET.fromstring(xml)


def nodes_with_text(root: ET.Element) -> list[tuple[str, tuple[int, int, int, int], ET.Element]]:
    out = []
    for n in root.iter("node"):
        text = n.attrib.get("text") or ""
        desc = n.attrib.get("content-desc") or ""
        label = text or desc
        b = n.attrib.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not m:
            continue
        bounds = tuple(map(int, m.groups()))  # type: ignore
        if label.strip():
            out.append((label, bounds, n))  # type: ignore
    return out


def find_bounds(root: ET.Element, needle: str, contains: bool = False) -> tuple[int, int, int, int] | None:
    for label, bounds, n in nodes_with_text(root):
        ok = (needle in label) if contains else (label == needle)
        if ok:
            return bounds  # type: ignore
    return None


def tap_bounds(bounds: tuple[int, int, int, int]) -> None:
    x = (bounds[0] + bounds[2]) // 2
    y = (bounds[1] + bounds[3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.8)


def tap_text(needle: str, contains: bool = False, retries: int = 5) -> bool:
    for _ in range(retries):
        root = dump_ui()
        b = find_bounds(root, needle, contains=contains)
        if b:
            log(f"tap '{needle}' at {b}")
            tap_bounds(b)
            return True
        time.sleep(0.6)
    log(f"FAIL find '{needle}'")
    # list available
    root = dump_ui()
    labels = [l for l, _, _ in nodes_with_text(root)]
    log("visible: " + " | ".join(labels[:40]))
    return False


def type_text(text: str) -> None:
    # Escape spaces for adb input text
    escaped = text.replace(" ", "%s").replace("'", "").replace('"', "")
    adb("shell", "input", "text", escaped)
    time.sleep(0.4)


def press_back() -> None:
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.7)


def launch() -> None:
    adb("shell", "am", "start", "-n", f"{PKG}/com.mars.planner.MainActivity")
    time.sleep(2.5)


def create_task(title: str, mark_done: bool = False) -> None:
    # Go to Tasks tab if needed, use CTA
    if not tap_text("＋ Новая задача", contains=True):
        if not tap_text("Новая задача", contains=True):
            raise RuntimeError("no new task button")
    time.sleep(1)
    # Title field - often first EditText; tap by placeholder or first empty
    root = dump_ui()
    # try common labels
    if not tap_text("Название", contains=True):
        # tap first EditText-like node
        for n in root.iter("node"):
            cls = n.attrib.get("class", "")
            if "EditText" in cls:
                b = n.attrib.get("bounds", "")
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
                if m:
                    tap_bounds(tuple(map(int, m.groups())))  # type: ignore
                    break
    type_text(title)
    time.sleep(0.3)
    if mark_done:
        tap_text("Выполнена", contains=True) or tap_text("Готово", contains=True) or tap_text("Done", contains=True)
        # status chips: Ищу "Выполнено" / DONE
        tap_text("Выполнено", contains=True)
    if not tap_text("Сохранить", contains=True):
        tap_text("Создать", contains=True)
    time.sleep(1)
    log(f"created task '{title}' done={mark_done}")


def open_first_task_named(name: str) -> bool:
    return tap_text(name, contains=True)


def add_subtask(title: str) -> None:
    if not tap_text("Подзадач", contains=True):
        tap_text("Добавить подзадачу", contains=True)
    # maybe opens field
    root = dump_ui()
    for n in root.iter("node"):
        if "EditText" in (n.attrib.get("class") or ""):
            b = n.attrib.get("bounds", "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
            if m:
                tap_bounds(tuple(map(int, m.groups())))  # type: ignore
                break
    type_text(title)
    tap_text("Добавить", contains=True) or tap_text("Сохранить", contains=True)
    time.sleep(0.8)
    log(f"subtask '{title}'")


def add_enhancement(title: str) -> None:
    tap_text("Дополнен", contains=True) or tap_text("Иде", contains=True) or tap_text("улучш", contains=True)
    root = dump_ui()
    for n in root.iter("node"):
        if "EditText" in (n.attrib.get("class") or ""):
            b = n.attrib.get("bounds", "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
            if m:
                tap_bounds(tuple(map(int, m.groups())))  # type: ignore
                break
    type_text(title)
    tap_text("Добавить", contains=True) or tap_text("Сохранить", contains=True)
    time.sleep(0.8)
    log(f"enhancement '{title}'")


def main() -> int:
    log("=== BlueStacks UI verification start ===")
    launch()
    shot("02_home.png")
    root = dump_ui()
    labels = [l for l, _, _ in nodes_with_text(root)]
    log("home labels: " + " | ".join(labels[:50]))

    # Navigate Tasks
    tap_text("Задачи", contains=True)
    time.sleep(1)
    shot("03_tasks.png")

    create_task("Osnovnaya_A_otchet")
    create_task("Osnovnaya_B_korm", mark_done=True)

    tap_text("Задачи", contains=True)
    time.sleep(1)
    shot("04_two_tasks.png")

    open_first_task_named("Osnovnaya_A")
    time.sleep(1)
    shot("05_task_a_open.png")
    add_subtask("Podzadacha_cifry")
    add_enhancement("Ideya_grafik")
    shot("06_task_a_with_links.png")
    press_back()

    # Settings exports
    tap_text("Настройки", contains=True)
    time.sleep(1)
    shot("07_settings.png")
    tap_text("Экспорт JSON", contains=True)
    time.sleep(1.2)
    shot("08_export_json.png")
    tap_text("Экспорт CSV", contains=True)
    time.sleep(1.2)
    shot("09_export_csv.png")

    # Pull exported files from app external files
    files = adb_out("shell", "run-as", PKG, "ls", "-la", "files").decode("utf-8", errors="replace")
    log("run-as files:\n" + files)
    # also Android/data
    ext = adb_out(
        "shell",
        "ls",
        "-la",
        f"/sdcard/Android/data/{PKG}/files/",
    ).decode("utf-8", errors="replace")
    log("external files:\n" + ext)

    # pull newest json/csv
    listing = adb_out("shell", "ls", "-1", f"/sdcard/Android/data/{PKG}/files/").decode()
    jsons = [x.strip() for x in listing.splitlines() if x.strip().endswith(".json") and "mars_backup" in x]
    csvs = [x.strip() for x in listing.splitlines() if x.strip().endswith(".csv")]
    if jsons:
        j = sorted(jsons)[-1]
        adb("pull", f"/sdcard/Android/data/{PKG}/files/{j}", str(OUT / j))
        log(f"pulled JSON {j}")
    if csvs:
        c = sorted(csvs)[-1]
        adb("pull", f"/sdcard/Android/data/{PKG}/files/{c}", str(OUT / c))
        log(f"pulled CSV {c}")

    (OUT / "ui_report.txt").write_text("\n".join(REPORT), encoding="utf-8")
    log("partial script done — continue in next stage")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
