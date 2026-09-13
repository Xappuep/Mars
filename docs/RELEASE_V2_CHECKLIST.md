# Чек-лист release-сборки Mars 2.0.0

Актуальный чек-лист перед распространением **подписанной** версии `2.0.0` (`versionCode` 3).
Предыдущий документ `docs/RELEASE_V1_CHECKLIST.md` относится к линейке 1.x и **не используется** для 2.0.0.

Keystore **не создаётся** автоматически и **не хранится** в репозитории.
Пароли и персональные абсолютные пути к ключу в этот документ **не** записывать.

## Процедура подписи (без паролей в файлах)

1. `gradlew.bat assembleRelease` → получить unsigned APK из `app/build/outputs/apk/release/`.
2. `zipalign -f -p 4 <unsigned.apk> <aligned-unsigned.apk>` (инструмент из Android Build Tools).
3. В обычном терминале (интерактивный ввод пароля):  
   `apksigner sign --ks <keystore-вне-Git> --ks-key-alias mars --out Mars-2.0.0-release-signed.apk <aligned-unsigned.apk>`
4. `apksigner verify --verbose --print-certs Mars-2.0.0-release-signed.apk` → ожидается `Verifies` (v2/v3).
5. Проверить package / versionName / versionCode / minSdk / targetSdk (`aapt dump badging` или аналог).
6. Зафиксировать SHA-256 подписанного APK **вне** репозитория исходников.
7. Не изменять APK после подписи.

## 1. Версии и идентификаторы

- [x] `versionName = 2.0.0`, `versionCode = 3`.
- [x] Схема Room = 3.
- [x] Протокол синхронизации с «Рубежом» = v1.
- [x] `minSdk = 26`, `targetSdk = 35`.
- [x] Release `applicationId = com.mars.planner` (без `.debug`).
- [x] Debug остаётся отдельным приложением: `com.mars.planner.debug`.
- [x] `CHANGELOG.md` и `README.md` согласованы с 2.0.0 / закрытием этапа 9.

## 2. Keystore и подпись

- [x] Keystore вне репозитория; alias `mars`; пароли только в менеджере паролей.
- [x] `*.jks`, `*.keystore`, `keystore.properties` в `.gitignore`.
- [x] Подпись вручную через `apksigner` (Gradle `signingConfigs` не обязателен).
- [x] `apksigner verify` = Verifies (v2 + v3).
- [x] SHA-256 файла keystore (основной = резерв, по подтверждению владельца):  
  `9996CB2EF3EDEDFB066183B7D2EBD502665009DF1E26A02627305D4337A77D9B`.
- [x] SHA-256 сертификата:  
  `9F39001CDA5113FBC80F7BEF913E2E4FE4AFEBE20BC2B226FEA761F844E67E5A`  
  (`CN=Mars Android Release, OU=Personal, O=Mars Planner, C=RU`, RSA 4096, до 29.01.2054).
- [x] Подписанный артефакт (вне Git): `Mars-2.0.0-release-signed.apk`,  
  SHA-256 `AE8B0A3B49C199CBC1A516D7DD590ACB62E01F8B2780A0BCB91C262E7B97B88B`,  
  52 493 095 байт, package `com.mars.planner`, `2.0.0` / code `3`.

## 3. Автоматические проверки (зафиксировано в 9.7)

- [x] `testDebugUnitTest` — 178 passed / 1 skipped / 0 failed.
- [x] `lintDebug` — 43 Warning / 0 Error (без блокеров выпуска).
- [x] `assembleDebug` / `assembleRelease` — SUCCESS (Gradle release = unsigned до ручной подписи).
- [ ] `connectedDebugAndroidTest` — только эмулятор / отдельный тестовый профиль.

## 4–10. Функциональные пункты

Модель проектов/задач, миграция 1.x, sync v1, темы, уведомления/голос, раздельность debug/release —
см. принятые части 9.1–9.6 и ручную приёмку 9.7 в `docs/STAGE9_PART7_RELEASE_READINESS.md`.

Ручной smoke подписанной сборки (9.7): запуск, пять вкладок, задача, тема, перезапуск, debug рядом,
сопряжение с «Рубежом», «ПК на связи, протокол v1» — **выполнен и принят**.

## 11. Публикация (отдельное решение)

- [ ] Merge в `main`, tag, GitHub Release — только после явного решения пользователя.
- [x] Keystore с резервной копией хранится отдельно от исходников.
- [ ] Описание для распространения: данные не уходят в интернет; обмен только с «Рубежом» в домашней сети.
