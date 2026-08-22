# Чек-лист release-сборки v1.0+

Используйте перед публикацией подписанного APK. Keystore **не создаётся** автоматически — только вручную.

## 1. Keystore

- [ ] Создать keystore в безопасном месте **вне** репозитория (не коммитить):
  ```bat
  keytool -genkey -v -keystore mars-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mars
  ```
- [ ] Записать alias, пароли store/key в **менеджер паролей** (не в Git, не в README).
- [ ] Убедиться, что `*.jks`, `*.keystore` в `.gitignore`.

## 2. Конфигурация подписи (локально)

- [ ] Создать `keystore.properties` **локально** (файл в `.gitignore`):
  ```properties
  storeFile=../path/to/mars-release.jks
  storePassword=***
  keyAlias=mars
  keyPassword=***
  ```
- [ ] Подключить signing config в `app/build.gradle.kts` только через `keystore.properties`.
- [ ] Убрать `applicationIdSuffix = ".debug"` и `versionNameSuffix = "-debug"` для release buildType.
- [ ] Установить `applicationId = "com.mars.planner"` для release (без `.debug`).

## 3. Версионирование

- [ ] `versionName` — семантическая версия (например `1.0.0`, затем `1.0.1`).
- [ ] `versionCode` — целое число, **строго больше** предыдущей опубликованной сборки.
- [ ] Обновить `CHANGELOG.md`.

## 4. Сборка

- [ ] `gradlew.bat test` — все unit-тесты зелёные.
- [ ] `gradlew.bat assembleRelease` — успешная сборка.
- [ ] Проверить размер APK и наличие всех `mars_*.webp` в assets.
- [ ] При необходимости включить R8/ProGuard (`isMinifyEnabled = true`) и прогнать smoke-тест.

## 5. Установка поверх debug

- [ ] Понимать: debug (`com.mars.planner.debug`) и release (`com.mars.planner`) — **разные** applicationId; обновление «поверх» возможно только между сборками с **одним и тем же** applicationId.
- [ ] Для миграции с debug на release: экспорт JSON на debug → установка release → импорт JSON.
- [ ] Для обновления release → release: `versionCode` выше установленного → `adb install -r app-release.apk`.

## 6. Проверка сохранности данных

- [ ] Создать несколько задач, подзадач, дополнений.
- [ ] Обновить поверх установленной версии (тот же applicationId).
- [ ] Убедиться, что Room-база и настройки DataStore сохранились.
- [ ] Проверить напоминания после обновления.

## 7. Sync-сервер

- [ ] `desktop-sync-server/run.bat` — сервер стартует, ключ в `data/pairing_key.txt` не в Git.
- [ ] Upload/download backup после release-сборки.
- [ ] Неверный ключ — данные на телефоне не повреждаются.

## 8. Безопасность перед публикацией

- [ ] `git status` — нет `pairing_key.txt`, `local.properties`, keystore, экспортов JSON/CSV пользователя.
- [ ] Скриншоты и отчёты верификации не содержат ключей сопряжения.
- [ ] ProGuard rules проверены, если minify включён.

## 9. Магазин / распространение (по желанию)

- [ ] Иконка, скриншоты, описание на русском.
- [ ] Политика конфиденциальности (данные не покидают устройство / локальная сеть).
- [ ] Тест на реальном устройстве Android 8–14.

## 10. После релиза

- [ ] Сохранить копию подписанного APK и mapping.txt (если minify).
- [ ] Зафиксировать тег Git `v1.0.0` (когда будете готовы к commit).
- [ ] Хранить keystore с резервной копией отдельно от исходников.
