# Отчёт v1.1.0 — баг «Сегодня» + визуальный стиль

**Дата:** 23.08.2026  
**Версия:** `1.1.0` / `versionCode = 2`

## 1. Причина бага

Главный экран «Сегодня» брал задачи через `TaskDao.observeForDay()` с условием SQL:

```sql
WHERE dueDateEpochDay = :epochDay
```

То есть показывались **только задачи с датой ровно сегодня**. Просроченная задача «тест» (дата в прошлом) была видна на экране «Задачи» (`observeRootTasks`) и в статистике (`TaskRules.isOverdue`), но **не попадала** в выборку «Сегодня» → счётчики = 0, текст «На сегодня задач нет».

## 2. Исправление выборки и счётчиков

Добавлен `TodayTasksSelector` (`TaskLogic.kt`):

| Критерий | Правило |
|----------|---------|
| Уровень | только корневые задачи |
| Статус | не `DONE`, не `CANCELLED` |
| Дата | `dueDateEpochDay <= сегодня` (обязательна) |
| Сортировка | просроченные первыми, затем по дате |

`AppViewModel.todayTasks` и `daySummary` строятся из `allRootTasks` через `TodayTasksSelector.select()` — **один источник данных** для списка и счётчиков.

Пустое состояние «На сегодня задач нет» показывается только если `tasks.isEmpty()` (есть просроченные → текст не показывается).

## 3. Тесты

Файл: `app/src/test/java/com/mars/planner/DomainLogicTest.kt`

| Тест | Сценарий |
|------|----------|
| `todayScreenShowsTaskDueToday` | задача на сегодня видна |
| `todayScreenShowsOverdueIncompleteTask` | просроченная видна, счётчик «Просрочено» = 1 |
| `completedOverdueTaskNotCountedAsOverdue` | выполненная просроченная исключена |
| `cancelledTaskExcludedFromToday` | отменённая не попадает |
| `futureTaskExcludedFromToday` | будущая не попадает |

**Результат:** `.\gradlew.bat test` — **BUILD SUCCESSFUL**

## 4. Визуальные изменения

- **Фон:** многослойный графит, радиальное свечение у зоны Марса, сетка/точки, медленный drift (`MarsAmbientBackground`)
- **Счётчики:** компактная панель `TodayStatsPanel` (стеклянная основа, ячейки)
- **Карточки:** полупрозрачное стекло, внутренний свет, тень, акценты по статусу; просроченные — метка «Просрочено», дата, красно-оранжевая линия, мягкое первое проявление
- **Фильтры:** градиент и свечение активного фильтра
- **Навигация:** усиленный светящийся индикатор вкладки
- **Кнопка «Новая задача»:** объёмный градиент с золотистой кромкой
- **Марс:** радиальное свечение за силуэтом, редкие частицы/линии; цвет свечения зависит от просрочки / реакции

При **уменьшении анимаций:** движения, частицы, переливы и параллакс отключены; подсветки статусов и контраст сохранены.

## 5. Сборка

```
.\gradlew.bat test          → BUILD SUCCESSFUL
.\gradlew.bat assembleDebug → BUILD SUCCESSFUL
```

**APK:** `app/build/outputs/apk/debug/app-debug.apk`

## 6. Скриншоты

`adb` недоступен в PATH на машине сборки — автозахват не выполнен. Рекомендуется проверить на устройстве с локальной задачей «тест».

## 7. Изменённые файлы

- `app/src/main/java/com/mars/planner/domain/logic/TaskLogic.kt`
- `app/src/main/java/com/mars/planner/data/TaskRepository.kt`
- `app/src/main/java/com/mars/planner/screens/MarsApp.kt`
- `app/src/main/java/com/mars/planner/ui/components/Components.kt`
- `app/src/main/java/com/mars/planner/ui/theme/MarsMotion.kt`
- `app/src/main/java/com/mars/planner/ui/theme/Theme.kt`
- `app/src/test/java/com/mars/planner/DomainLogicTest.kt`
- `CHANGELOG.md`
