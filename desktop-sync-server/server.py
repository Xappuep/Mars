"""
Локальный сервер резервного копирования «Ежедневник Марса».
Слушает только локальную сеть (0.0.0.0 по умолчанию в LAN), без облака.
"""

from __future__ import annotations

import json
import os
import secrets
import time
from datetime import datetime
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse, PlainTextResponse
import uvicorn

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
BACKUPS_DIR = DATA_DIR / "backups"
LOG_FILE = DATA_DIR / "sync_log.txt"
KEY_FILE = DATA_DIR / "pairing_key.txt"

MAX_VERSIONS = 10
DEFAULT_PORT = 8765

app = FastAPI(title="Mars Planner Sync", version="1.0.0")


def ensure_dirs() -> None:
    BACKUPS_DIR.mkdir(parents=True, exist_ok=True)
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not KEY_FILE.exists():
        KEY_FILE.write_text(secrets.token_urlsafe(16), encoding="utf-8")


def read_key() -> str:
    ensure_dirs()
    return KEY_FILE.read_text(encoding="utf-8").strip()


def log_event(message: str) -> None:
    ensure_dirs()
    stamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    with LOG_FILE.open("a", encoding="utf-8") as f:
        f.write(f"[{stamp}] {message}\n")


def require_key(x_sync_key: str | None) -> None:
    expected = read_key()
    if not x_sync_key or x_sync_key.strip() != expected:
        raise HTTPException(status_code=401, detail="Неверный ключ сопряжения")


def list_backups() -> list[Path]:
    ensure_dirs()
    files = sorted(BACKUPS_DIR.glob("backup_*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    return files


@app.get("/health")
def health(x_sync_key: str | None = Header(default=None)):
    require_key(x_sync_key)
    backups = list_backups()
    last_at = int(backups[0].stat().st_mtime * 1000) if backups else None
    return {
        "ok": True,
        "message": "Подключение успешно",
        "last_backup_at": last_at,
        "backup_count": len(backups),
    }


@app.post("/backup")
async def upload_backup(request: Request, x_sync_key: str | None = Header(default=None)):
    require_key(x_sync_key)
    body = await request.body()
    if not body:
        raise HTTPException(status_code=400, detail="Пустое тело запроса")
    try:
        payload = json.loads(body.decode("utf-8"))
        if "tasks" not in payload:
            raise ValueError("нет поля tasks")
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Некорректный JSON: {exc}") from exc

    ensure_dirs()
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = BACKUPS_DIR / f"backup_{stamp}.json"
    path.write_bytes(body)
    # ротация старых версий
    for old in list_backups()[MAX_VERSIONS:]:
        old.unlink(missing_ok=True)
    log_event(f"UPLOAD ok size={len(body)} file={path.name}")
    return {"ok": True, "file": path.name, "saved_at": int(time.time() * 1000)}


@app.get("/backup/latest")
def latest_backup(x_sync_key: str | None = Header(default=None)):
    require_key(x_sync_key)
    backups = list_backups()
    if not backups:
        raise HTTPException(status_code=404, detail="Нет резервных копий")
    content = backups[0].read_text(encoding="utf-8")
    log_event(f"DOWNLOAD ok file={backups[0].name}")
    return JSONResponse(content=json.loads(content))


@app.get("/backups")
def backups_meta(x_sync_key: str | None = Header(default=None)):
    require_key(x_sync_key)
    items = [
        {
            "file": p.name,
            "mtime": int(p.stat().st_mtime * 1000),
            "size": p.stat().st_size,
        }
        for p in list_backups()
    ]
    return {"items": items}


@app.get("/")
def root():
    return PlainTextResponse(
        "Mars Planner Sync Server. Используйте /health с заголовком X-Sync-Key.\n"
        f"Ключ сопряжения сохранён в: {KEY_FILE}\n"
    )


def main() -> None:
    ensure_dirs()
    key = read_key()
    host = os.environ.get("MARS_SYNC_HOST", "0.0.0.0")
    port = int(os.environ.get("MARS_SYNC_PORT", str(DEFAULT_PORT)))
    print("=== Ежедневник Марса · локальный sync-сервер ===")
    print(f"Слушает: http://{host}:{port}")
    print(f"Ключ сопряжения: {key}")
    print(f"Папка копий: {BACKUPS_DIR}")
    print("Остановите сервер: Ctrl+C")
    # 0.0.0.0 — доступ из LAN; наружу не публикуйте порт роутером
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
