# -*- coding: utf-8 -*-
"""Проверка схемы экспорта и локального sync-сервера (без телефона)."""
from __future__ import annotations

import csv
import io
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
SERVER_DIR = ROOT / "desktop-sync-server"
BACKUPS = SERVER_DIR / "data" / "backups"
KEY_FILE = SERVER_DIR / "data" / "pairing_key.txt"
LOG_FILE = SERVER_DIR / "data" / "sync_log.txt"
PORT = 8765


def build_test_payload() -> dict:
    today = 20321  # 2025-08-21-ish epoch day placeholder; value only for structure
    now = int(time.time() * 1000)
    return {
        "version": 1,
        "exportedAt": now,
        "tasks": [
            {
                "id": 1,
                "title": "Подготовить отчёт",
                "description": "Основная задача с напоминанием",
                "dueDateEpochDay": today,
                "dueTimeMinutes": 600,
                "reminderAtEpochMillis": now + 3_600_000,
                "priority": "high",
                "category": "Работа",
                "status": "in_progress",
                "createdAt": now - 10_000,
                "updatedAt": now - 5_000,
                "postponeCount": 0,
                "postponeReason": None,
                "parentTaskId": None,
                "nestingLevel": 0,
                "relatedToTaskId": None,
                "isDemo": False,
            },
            {
                "id": 2,
                "title": "Купить корм Марсу",
                "description": "Вторая основная",
                "dueDateEpochDay": today + 2,
                "dueTimeMinutes": 1080,
                "reminderAtEpochMillis": None,
                "priority": "normal",
                "category": "Дом",
                "status": "done",
                "createdAt": now - 20_000,
                "updatedAt": now - 1_000,
                "postponeCount": 1,
                "postponeReason": "магазин закрыт",
                "parentTaskId": None,
                "nestingLevel": 0,
                "relatedToTaskId": None,
                "isDemo": False,
            },
            {
                "id": 3,
                "title": "Собрать цифры за неделю",
                "description": "Подзадача к отчёту",
                "dueDateEpochDay": today,
                "dueTimeMinutes": None,
                "reminderAtEpochMillis": None,
                "priority": "low",
                "category": "Работа",
                "status": "new",
                "createdAt": now - 9_000,
                "updatedAt": now - 9_000,
                "postponeCount": 0,
                "postponeReason": None,
                "parentTaskId": 1,
                "nestingLevel": 1,
                "relatedToTaskId": None,
                "isDemo": False,
            },
        ],
        "enhancements": [
            {
                "id": 10,
                "sourceTaskId": 1,
                "title": "Добавить график динамики",
                "description": "Дополнение к отчёту",
                "status": "idea",
                "priority": "normal",
                "createdAt": now - 8_000,
                "plannedDateEpochDay": today + 3,
                "deferredReason": None,
                "convertedTaskId": None,
            }
        ],
        "settings": {
            "motivatorMode": "adaptive",
            "morningReminderEnabled": True,
            "morningReminderHour": 9,
            "morningReminderMinute": 0,
            "eveningReminderEnabled": True,
            "eveningReminderHour": 21,
            "eveningReminderMinute": 0,
            "defaultSnoozeMinutes": 30,
            "userName": "Михаил",
        },
    }


def to_csv_roots(payload: dict) -> str:
    roots = [
        t
        for t in payload["tasks"]
        if t.get("parentTaskId") is None and int(t.get("nestingLevel") or 0) == 0
    ]
    header = [
        "id",
        "title",
        "description",
        "due_date",
        "due_time",
        "priority",
        "category",
        "status",
        "created_at",
        "updated_at",
        "postpone_count",
        "postpone_reason",
        "parent_task_id",
        "nesting_level",
        "related_to_task_id",
    ]
    buf = io.StringIO()
    writer = csv.writer(buf, lineterminator="\n")
    writer.writerow(header)
    for t in roots:
        writer.writerow(
            [
                t["id"],
                t["title"],
                t["description"],
                t.get("dueDateEpochDay") or "",
                t.get("dueTimeMinutes") or "",
                t["priority"],
                t["category"],
                t["status"],
                t["createdAt"],
                t["updatedAt"],
                t["postponeCount"],
                t.get("postponeReason") or "",
                t.get("parentTaskId") or "",
                t["nestingLevel"],
                t.get("relatedToTaskId") or "",
            ]
        )
    return buf.getvalue()


def http_json(method: str, url: str, key: str | None = None, data: bytes | None = None):
    headers = {}
    if key is not None:
        headers["X-Sync-Key"] = key
    if data is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, body, None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return e.code, body, str(e)
    except Exception as e:  # noqa: BLE001
        return None, "", str(e)


def main() -> int:
    report: list[str] = []
    payload = build_test_payload()
    json_path = OUT / "mars_backup_verification.json"
    csv_path = OUT / "mars_tasks_verification.csv"
    json_text = json.dumps(payload, ensure_ascii=False, indent=2)
    json_path.write_text(json_text, encoding="utf-8")
    csv_text = to_csv_roots(payload)
    csv_path.write_text(csv_text, encoding="utf-8")

    # --- JSON checks ---
    parsed = json.loads(json_path.read_text(encoding="utf-8"))
    roots = [t for t in parsed["tasks"] if t.get("parentTaskId") is None and t.get("nestingLevel", 0) == 0]
    subs = [t for t in parsed["tasks"] if t.get("parentTaskId") is not None]
    enh = parsed.get("enhancements") or []
    assert len(roots) >= 2, "need >=2 root tasks"
    assert len(subs) >= 1, "need subtask"
    assert len(enh) >= 1, "need enhancement"
    assert any(t.get("reminderAtEpochMillis") for t in roots), "need reminder"
    assert parsed.get("settings"), "need settings"
    assert all(e.get("sourceTaskId") for e in enh), "enhancement must link to task"
    assert "Подготовить отчёт" in json_text
    report.append("PASS JSON: file created, readable, roots/subs/enh/settings/reminder present")
    report.append(f"JSON path: {json_path}")

    # --- CSV checks ---
    rows = list(csv.reader(io.StringIO(csv_path.read_text(encoding="utf-8"))))
    assert rows[0][1] == "title"
    assert len(rows) == 3  # header + 2 roots
    titles = {r[1] for r in rows[1:]}
    assert "Подготовить отчёт" in titles and "Купить корм Марсу" in titles
    assert "Собрать цифры за неделю" not in titles
    # UTF-8 round-trip
    assert "отчёт" in csv_path.read_text(encoding="utf-8")
    report.append("PASS CSV: only root tasks, UTF-8 Russian intact")
    report.append(f"CSV path: {csv_path}")

    # --- merge idempotency (logical model of fixed upsert-by-id) ---
    store: dict[int, dict] = {}
    for _ in range(2):
        for t in parsed["tasks"]:
            store[t["id"]] = t
    assert len(store) == 3, "re-merge by id must not duplicate"
    report.append("PASS merge-by-id model: second import of same file keeps 3 tasks (no dupes)")

    # --- sync server ---
    # install deps quietly
    subprocess.run(
        [sys.executable, "-m", "pip", "install", "-r", str(SERVER_DIR / "requirements.txt")],
        check=True,
        cwd=str(SERVER_DIR),
    )
    proc = subprocess.Popen(
        [sys.executable, "server.py"],
        cwd=str(SERVER_DIR),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    try:
        key = None
        for _ in range(40):
            time.sleep(0.25)
            if KEY_FILE.exists():
                key = KEY_FILE.read_text(encoding="utf-8").strip()
                break
        if not key:
            raise RuntimeError("pairing key not created")

        # wait until listening
        for _ in range(40):
            code, body, err = http_json("GET", f"http://127.0.0.1:{PORT}/health", key=key)
            if code == 200:
                break
            time.sleep(0.25)
        else:
            raise RuntimeError(f"server not healthy: {err} {body}")

        report.append("PASS sync: server started on 127.0.0.1:8765 (LAN bind 0.0.0.0 in server.py)")

        code, body, _ = http_json("GET", f"http://127.0.0.1:{PORT}/health", key=key)
        assert code == 200 and json.loads(body).get("ok") is True
        report.append("PASS sync: check connection (health) with pairing key")

        # wrong key
        code, body, _ = http_json("GET", f"http://127.0.0.1:{PORT}/health", key="WRONG_KEY")
        assert code == 401
        assert "ключ" in body.lower() or "Неверный" in body
        report.append(f"PASS sync error wrong key: HTTP {code}, body mentions key")

        # wrong IP / unreachable (simulate closed port)
        code, body, err = http_json("GET", "http://127.0.0.1:18765/health", key=key)
        assert code is None and err
        report.append(f"PASS sync error wrong port/IP: connection failed ({err.split(':')[0]})")

        # upload
        data = json_path.read_bytes()
        code, body, err = http_json("POST", f"http://127.0.0.1:{PORT}/backup", key=key, data=data)
        assert code == 200, f"upload failed {code} {body} {err}"
        up = json.loads(body)
        backup_name = up["file"]
        backup_path = BACKUPS / backup_name
        assert backup_path.exists()
        report.append("PASS sync: upload backup from 'phone' payload")
        report.append(f"PC backup path: {backup_path}")

        # log without task titles
        log_text = LOG_FILE.read_text(encoding="utf-8") if LOG_FILE.exists() else ""
        assert "UPLOAD ok" in log_text
        assert "Подготовить отчёт" not in log_text
        assert "Купить корм" not in log_text
        report.append("PASS sync log: event logged without task text")

        # download latest
        code, body, err = http_json("GET", f"http://127.0.0.1:{PORT}/backup/latest", key=key)
        assert code == 200
        got = json.loads(body)
        assert got["tasks"][0]["title"] == payload["tasks"][0]["title"]
        assert len(got["enhancements"]) == 1
        report.append("PASS sync: download latest backup")

        # server off
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
        proc = None
        code, body, err = http_json("GET", f"http://127.0.0.1:{PORT}/health", key=key)
        assert code is None and err
        report.append(f"PASS sync error server off: {err.split(':')[0]}")
        report.append("NOTE: phone local DB unchanged on these errors (client only mutates on Success)")

    finally:
        if proc is not None and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()

    stamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    report_path = OUT / "verification_report.txt"
    report_path.write_text("\n".join([f"Generated: {stamp}", *report]), encoding="utf-8")
    print("\n".join(report))
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
