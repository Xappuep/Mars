# BlueStacks UI verification — Ежедневник Марса
Date: 2026-08-21

## APK installed
- Path: F:\1\Cursor\Mars\Mars\app\build\outputs\apk\debug\app-debug.apk
- Package: com.mars.planner.debug
- APK LastWriteTime: 2026-08-21 22:15:36
- Size: 18676044 bytes

## Passed via UI in BlueStacks
1. Install APK via adb into BlueStacks (emulator-5554).
2. Create test data in UI:
   - Osnovnaya_A_otchet (not done)
   - Osnovnaya_B_korm (status Выполнено)
   - Subtask Podzadacha_cifry inside A
   - Enhancement Ideya_grafik inside A
3. Settings → Экспорт JSON / Экспорт CSV
   - UI showed path for CSV under Android/data/.../files/
4. CSV contains only 2 root tasks (3 lines incl. header), no subtask title.
5. JSON: 2 roots + 1 sub + 1 enhancement.
6. Import merge via system picker → dialog «Объединить» → «Данные объединены»
   - No duplicate roots on Tasks list (A=1, B=1)
   - Task A still shows subtask + enhancement
7. Import replace:
   - Dialog 1: merge vs replace
   - Dialog 2: «Заменить все локальные данные?» + confirm «Заменить»
   - UI message: «Данные заменены. Резервная копия: pre_replace_backup_1787342463283.json»
8. Sync screen:
   - IP 10.0.2.2, port 8765, pairing key from pairing_key.txt
   - «Проверить подключение» → «Подключение успешно…»
   - «Отправить копию на ПК» → «Резервная копия отправлена на ПК»
   - Last sync shown: 21.08.2026 22:57
9. Wrong key → «Неверный ключ сопряжения»; tasks still on list (A=1, B=1)

## Notes / partial
- On Sync screen the upload button label is «Отправить копию на ПК» (not literally «Синхронизировать с ПК»; that label opens Sync from Today/Settings).
- run-as cannot list internal filesDir on this BlueStacks build; backup existence confirmed by Settings UI message text.
- Screenshots saved under docs/verification/bluestacks/

## Export paths (device + pulled copies)
Device:
- /storage/emulated/0/Android/data/com.mars.planner.debug/files/mars_backup_1787341748162.json
- /storage/emulated/0/Android/data/com.mars.planner.debug/files/mars_tasks_1787341751465.csv
Pulled to PC:
- F:\1\Cursor\Mars\Mars\docs\verification\bluestacks\mars_backup_1787341748162.json
- F:\1\Cursor\Mars\Mars\docs\verification\bluestacks\mars_tasks_1787341751465.csv

## New PC sync backup
- F:\1\Cursor\Mars\Mars\desktop-sync-server\data\backups\backup_20260821_225728.json
- Created: 2026-08-21 22:57:28

## Key screenshots
- 12_two_roots.png — two main tasks
- 11c_after_sub.png — subtask+enhancement
- 14/15_export_*.png — export messages
- 17_import_dialog_merge.png — merge dialog
- 51/52/53 — replace dialogs + success with pre_replace name
- 36/37_sync_*.png — health + upload success
- 38_wrong_key.png — wrong key error
- 39_tasks_final.png — tasks intact
