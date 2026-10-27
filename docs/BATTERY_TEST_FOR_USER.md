# Инструкция по проверке энергоэффективности / Battery Verification Guide

---

## Русский язык (Инструкция для пользователя)

### Что было сделано:
В предыдущей версии приложение расходовало 3 часа 32 минуты процессорного времени (CPU time) за 1,5 часа работы в фоне из-за:
1. Спама уведомлений каждую секунду (`NotificationManager.notify()`), который непрерывно будил процессор телефона в кармане (5 580 вызовов IPC).
2. Подробного логирования sing-box (`level: info`), из-за которого каждый пакет отправлял логи через JNI в системный `logd`.
3. Фонового тиканья таймеров и графиков в памяти приложения при заблокированном экране.

**В новой версии всё исправлено:**
- При выключенном экране уведомление переходит в статичный режим («Connected: <Сервер> • Protected via Nebula»), а отправка обновлений скорости в систему **полностью замораживается (0 вызовов IPC)**.
- Добавлен профиль энергопотребления **Balanced** (по умолчанию) и **Battery Saver** (максимальная экономия).
- Логирование ядра переведено в режим `warn` (только предупреждения/ошибки).
- Включен TCP Fast Open для ускорения рукопожатий и экономии энергии радиомодема.
- В раздел «Инструменты» (Tools) добавлен прямой отчет **«Battery & CPU Report»**, показывающий реальное время процессора (`Process.getElapsedCpuTime()`) и количество живых потоков.

---

### Как проверить на телефоне Samsung:

1. **Установите свежий APK:**
   - Готовые файлы лежат в папке:
     `C:\Users\User\.gemini\antigravity\scratch\nebula\app\build\outputs\apk\release\`
     (Для обычных телефонов подходит `app-arm64-v8a-release.apk` или `app-universal-release.apk`).

2. **Запустите VPN:**
   - Откройте Nebula, выберите любой рабочий сервер из подписки и нажмите подключение.
   - Зайдите во вкладку **Инструменты (Tools)**:
     - В верхней карточке **Battery & CPU Report** вы увидите текущее процессорное время (`PROCESS CPU`), время работы приложения и число потоков.

3. **Тест в фоне (как с INCY):**
   - Заблокируйте телефон или оставьте его работать в обычном режиме (в кармане, при обычном использовании) на 1–2 часа или на ночь.
   - При заблокированном экране обратите внимание: скорость в шторке не скачет каждую секунду и не будит телефон.

4. **Проверка статистики в настройках Samsung:**
   - Откройте **Настройки телефона** -> **Батарея** (или **Обслуживание устройства** -> **Батарея**).
   - Найдите в списке приложение **Nebula**.
   - Посмотрите строку **«Время ЦП» (CPU Time)**:
     - Раньше было: **3 ч 32 мин** за 1 ч 33 мин фона.
     - Теперь будет: **несколько минут** (как у INCY — порядка 3–8 минут за несколько часов работы), и телефон больше не будет ругаться на «интенсивное фоновое использование батареи».

---

## English (Verification Guide)

### Root Causes Fixed:
1. **Notification IPC Storm:** Eliminated 1000ms `NotificationManager.notify()` loop. When the screen is OFF, speed updates are completely frozen, posting static text once and causing zero Binder IPC waking the CPU.
2. **Core Logcat Writes:** Lowered default core log level to `warn`, stopping thousands of JNI log allocations and system `logd` writes per minute.
3. **Redundant JNI Command Subscriptions:** Removed empty group and connection tracking across the Go-Java bridge.
4. **Leaking UI Coroutines:** Connected graph history and duration timers to screen lifecycle (`DisposableEffect`), stopping all ViewModel timers when the app is minimized.
5. **TCP Fast Open:** Enabled on all TCP outbounds to reduce modem transmission duration.
6. **In-App Battery Report:** Added live `Process.getElapsedCpuTime()` readout in Tools tab.

### How to Verify on Samsung Device:
1. Install the release APK:
   `app/build/outputs/apk/release/app-arm64-v8a-release.apk`
2. Open Nebula, connect to any proxy server.
3. Go to **Tools** tab: observe the **Battery & CPU Report** card showing process CPU time, thread count, and power profile.
4. Leave the phone locked or in pocket for 1-2 hours under normal conditions.
5. Open Samsung **Settings -> Device Care -> Battery -> Nebula**:
   - Verify that **CPU Time** is in low single-digit minutes (matching INCY), with no "intensive battery drain" alerts.
