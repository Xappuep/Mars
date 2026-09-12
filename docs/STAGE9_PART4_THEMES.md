# Этап 9 — часть 4: интеграция семи утверждённых мобильных фонов

**Статус части 4: ПРИНЯТА** (ручная приёмка пользователем; публикация — текущий коммит ветки)

| Параметр | Значение |
|----------|----------|
| Дата ручной приёмки | 12.09.2026 |
| Базовый HEAD (часть 3) | `653c2691ebdc99a1a52a93ac5cd13c534fce4b01` |
| Ветка | `phase/9-mobile-ui-refresh` |
| Разрешение всех PNG | `841×1870 px` |
| Контрольные суммы ресурсов | проверены (совпали с утверждённым пакетом) |
| Автотесты | `testDebugUnitTest`: 165 passed / 1 skipped / 0 failed |
| Сборка | `assembleDebug`: SUCCESS |
| Ручная приёмка | выполнена пользователем |

**Принята только часть 4 этапа 9. Весь этап 9 не завершён.**
Merge в `main`, PR, tag и Release не выполнялись.

## Содержание

Интегрированы семь утверждённых мобильных фонов (побайтовые копии, без перекодирования):

| Тема | ID | Android-ресурс |
|------|----|----------------|
| Орбита | `orbit` | `theme_orbit_mobile.png` |
| Туманность | `nebula` | `theme_nebula_mobile.png` |
| Белая станция | `white-station` | `theme_white_station_mobile.png` |
| Пепел и янтарь | `ash-amber` | `theme_ash_amber_mobile.png` |
| Полярная ночь | `polar-night` | `theme_polar_night_mobile.png` |
| Светлый бетон | `light-concrete` | `theme_light_concrete_mobile.png` (v3-circle) |
| Убежище | `shelter-terminal` | `theme_shelter_terminal_mobile.png` |

`ThemeSceneBackgrounds.config()` сопоставляет все семь `AppTheme` с соответствующими ресурсами. Режимы `Hero` / `CompactHeader` / `ContentSurface` и кадрирование `ContentScale.Crop` + `TopCenter` сохранены.
