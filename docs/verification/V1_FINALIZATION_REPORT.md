# Отчёт финализации v1.0.0

Дата: 22.08.2026  
Проект: Ежедневник Марса  
Commit / push / keystore: **не выполнялись**

## Версия приложения

| Параметр | Значение |
|----------|----------|
| `versionName` | `1.0.0` |
| `versionCode` | `1` (без изменений — достаточно для первой установки) |
| Debug `applicationId` | `com.mars.planner.debug` |
| Release `applicationId` (будущий) | `com.mars.planner` |

Файл: `app/build.gradle.kts` — версия уже была `1.0.0`; правки не требовались.

## Результаты проверок

### Unit-тесты

Команда: `gradlew.bat test`  
Результат: **BUILD SUCCESSFUL**  
Тестовый класс: `app/src/test/java/com/mars/planner/DomainLogicTest.kt` — **9** тестов (`@Test`), debug и release unit-test прогоны пройдены.

### Сборка debug APK

Команда: `gradlew.bat assembleDebug`  
Результат: **BUILD SUCCESSFUL**

Путь к APK:

`F:\1\Cursor\Mars\Mars\app\build\outputs\apk\debug\app-debug.apk`

Размер: ~44,7 МБ (44723182 байт).

### Изображения Марса (assets)

Каталог: `app/src/main/assets/mars/`

| Файл | Статус | Размер |
|------|--------|--------|
| `mars_default.webp` | OK | >1 МБ |
| `mars_done.webp` | OK | >1 МБ |
| `mars_working.webp` | OK | >1 МБ |
| `mars_postponed.webp` | OK | >1 МБ |
| `mars_overdue.webp` | OK | >1 МБ |
| `mars_supportive.webp` | OK | >1 МБ |
| `mars_strict.webp` | OK | >1 МБ |

Приложение загружает **WebP** в первую очередь (`Components.kt` → `loadMarsBitmap`).  
Placeholder `.txt` и `mars_placeholder.xml` — только fallback при отсутствии WebP; в текущей сборке WebP присутствуют.

> **Примечание:** семь файлов `*.webp` на диске есть, но **ещё не добавлены в Git** (untracked). Для полноценного релиза их нужно включить в commit отдельно.

### Заглушки в UI

- Основной интерфейс использует прозрачные WebP — случайные placeholder-изображения в штатном UI **не отображаются**.
- `MarsAvatar` / fallback на `R.drawable.mars_placeholder` — только при ошибке загрузки asset.

### desktop-sync-server

- Запуск: `run.bat` / `run.sh` / `python server.py` — инструкция в `README.md`.
- В исходниках: `desktop-sync-server/data/.gitkeep` — **без** пользовательских бэкапов.
- `pairing_key.txt`, `backups/`, `sync_log.txt` — в `.gitignore`.

### Данные пользователя при проверках

Проверки не выполняли `pm clear`, не импортировали пользовательские JSON и не изменяли БД на подключённом устройстве.

## Безопасность и Git

### Обновлён `.gitignore`

Добавлены правила для: keystore, `local.properties`, APK/AAB, `pairing_key.txt`, backups sync-сервера, экспортов JSON/CSV, `_seed/`, секретных имён файлов.

### Секреты (ключи, пароли) в отслеживаемых файлах

**Содержимое ключей сопряжения или паролей в tracked-файлах не обнаружено.**

Скрипты верификации ссылаются на путь `desktop-sync-server/data/pairing_key.txt`, но **не содержат** значение ключа в репозитории.

### Экспортированные данные в отслеживаемых файлах (не секреты, но не для Git)

Следующие файлы **уже отслеживаются Git** и содержат **тестовые** экспорты задач (QA/верификация):

- `docs/verification/bluestacks/mars_backup_1787341674972.json`
- `docs/verification/bluestacks/mars_backup_1787341748162.json`
- `docs/verification/bluestacks/mars_tasks_1787341679153.csv`
- `docs/verification/bluestacks/mars_tasks_1787341751465.csv`
- `docs/verification/mars_backup_verification.json`
- `docs/verification/mars_tasks_verification.csv`
- `docs/verification/visual/_seed/*.json` (7 файлов)

**Безопасное исправление (вручную, без commit в этом сеансе):**

```bat
git rm --cached docs/verification/bluestacks/mars_backup_*.json
git rm --cached docs/verification/bluestacks/mars_tasks_*.csv
git rm --cached docs/verification/mars_backup_verification.json
git rm --cached docs/verification/mars_tasks_verification.csv
git rm --cached docs/verification/visual/_seed/*.json
```

После этого `.gitignore` не даст им вернуться в индекс.

### Не отслеживается / исключено из будущих коммитов

- `local.properties`, `*.apk`, keystore — правила в `.gitignore`
- `desktop-sync-server/data/pairing_key.txt`, `backups/` — правила в `.gitignore`
- Сборочные каталоги `build/`, `.gradle/`

## Созданные и изменённые файлы (финализация)

| Файл | Действие |
|------|----------|
| `CHANGELOG.md` | создан |
| `README.md` | обновлён |
| `docs/RELEASE_V1_CHECKLIST.md` | создан |
| `docs/verification/V1_FINALIZATION_REPORT.md` | создан (этот файл) |
| `.gitignore` | расширен |
| `app/build.gradle.kts` | без изменений (версия уже 1.0.0) |

Код приложения, логика и дизайн **не менялись** в рамках финализации.

## Что осталось выполнить вручную для release APK

1. Создать keystore вне репозитория.
2. Настроить signing config через локальный `keystore.properties`.
3. Убрать debug-suffix для release buildType.
4. `gradlew assembleRelease`, подписанный APK.
5. Исключить из Git тестовые JSON/CSV (команды выше).
6. Добавить в Git семь файлов `mars_*.webp` (сейчас untracked).
7. Commit, тег `v1.0.0`, публикация — по вашему решению.

Подробно: `docs/RELEASE_V1_CHECKLIST.md`.

## Не проверялось

| Область | Причина |
|---------|---------|
| Подписанный release APK | keystore не создавался по заданию |
| Google Play / публикация | вне scope |
| Установка release поверх debug | разные applicationId |
| Instrumented / UI-тесты на устройстве | не запрашивались; есть ручные отчёты в `docs/verification/` |
| Полный регресс всех 7 mood на физическом телефоне | выполнялся ранее в сессии разработки, не перезапускался в финализации |

## Итог (после первой финализации)

**v1.0.0 готова** как проверенная offline debug-версия с документацией и чек-листом release.  
Перед публикацией подписанного APK — keystore, очистка tracked-экспортов, включение WebP в репозиторий.

---

## Подготовка к безопасному commit v1.0.0 (22.08.2026, без commit)

### Убрано из Git-индекса (`git rm --cached`, локальные файлы сохранены)

**JSON/CSV экспорты и seed:**
- `docs/verification/bluestacks/mars_backup_*.json` (2)
- `docs/verification/bluestacks/mars_tasks_*.csv` (2)
- `docs/verification/mars_backup_verification.json`
- `docs/verification/mars_tasks_verification.csv`
- `docs/verification/visual/_seed/*.json` (8)

**Скриншоты и UI-галереи:**
- весь `docs/screenshots/`
- весь `docs/verification/screenshots/`
- весь `docs/verification/visual/` (png/jpg + capture-скрипты)
- `docs/ui-*.html`

**BlueStacks / raw verification:**
- весь `docs/verification/bluestacks/` (png, xml, txt, py, md-отчёты)
- `docs/verification/report.html`, `verification_report.txt`, `fix_visual_imports.py`, `run_export_sync_check.py`

`desktop-sync-server/data/.gitkeep` **оставлен** в индексе. Runtime-данные (`pairing_key.txt`, backups) в индексе не были.

### Добавленные / уточнённые правила `.gitignore`

- APK/AAB/dex, keystore, `keystore.properties`, `local.properties`, `*.env`
- `desktop-sync-server/data/pairing_key.txt`, `backups/`, `sync_log.txt`, `data/*.json|*.csv` (с исключением `.gitkeep`)
- `**/mars_backup*.json`, `**/mars_tasks*.csv`, общие `*backup*.json`, `*export*.json|csv`
- `docs/verification/**/*.json|csv`
- `docs/screenshots/`, `docs/ui-*.html`, `docs/verification/visual/`, `docs/verification/bluestacks/`, `docs/verification/screenshots/`
- `docs/verification/**/*.{png,jpg,jpeg,webp,mp4,webm,xml,html,txt,py}` с исключением `!docs/verification/V1_FINALIZATION_REPORT.md`

Проверено `git check-ignore -v` на примерах JSON, CSV, APK, ключа, скриншота — все игнорируются.

### Разрешённые Markdown (без секретов)

Проверка по шаблонам `password|pairing_key|secret|token|api_key|PRIVATE`:
- `README.md`, `CHANGELOG.md` — чисто
- `docs/RELEASE_V1_CHECKLIST.md`, `docs/verification/V1_FINALIZATION_REPORT.md` — только **инструкции** (`storePassword=***`, упоминания имён файлов `pairing_key.txt` в чек-листе). **Значений секретов нет.**

### Обязательно в будущий commit

| Группа | Файлы |
|--------|--------|
| Код | изменения в `MarsApp.kt`, `Screens.kt`, `Components.kt` и остальной уже tracked app/ |
| Assets | 7× `app/src/main/assets/mars/mars_*.webp` (+ уже tracked png/placeholder) |
| Docs | `CHANGELOG.md`, `README.md`, `docs/RELEASE_V1_CHECKLIST.md`, `docs/verification/V1_FINALIZATION_REPORT.md` |
| Build/config | `.gitignore`, gradle-файлы (уже tracked) |

### Не добавлять

APK, keystore, `local.properties`, JSON/CSV экспорты, backups, скриншоты/видео, `docs/verification/visual/**`, `docs/verification/bluestacks/**`, ключи, raw-логи.

### Решение по `tools/`

| Файл | Решение | Почему |
|------|---------|--------|
| `tools/mars_remove_bg.py` | **Рекомендуется добавить** | Нужен для повторяемой перегенерации прозрачных WebP из PNG (`rembg`). Не требуется для `assembleDebug`, если WebP уже в assets. |

Не добавлять автоматически без явного `git add tools/mars_remove_bg.py`.

### Повторные проверки

| Проверка | Результат |
|----------|-----------|
| `gradlew.bat test assembleDebug` | **EXIT=0** SUCCESS |
| `git diff --check` | только CRLF-warnings; trailing whitespace в README исправлены |
| Tracked JSON/CSV | **0** |
| Tracked screenshots under docs | **0** |

### Готовность к commit

**Да** — рабочее дерево готово к безопасному commit v1.0.0 после явного `git add` обязательных файлов (webp, docs, tools по желанию) и без добавления игнорируемых артефактов.  
**Commit / push / tag в этом сеансе не выполнялись.**
