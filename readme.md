# Txori Zero

**A minimal focus-session timer. Lists of tasks, a stopwatch, a timer and a roulette.**

Port of Txori (Kotlin) to the Mako Zero standard: plain Java, no dependencies, Android API 5 (Android 2.0) through the latest versions, one font (Jersey 25), the Catppuccin/Mono themes, one language (English).

- **Lists**: tap play on a list header to run its tasks one after another (with optional rest). Pencil icon edits lists, tasks and order.
- **Stopwatch / Timer**: tap to start or pause, long-press to reset. Pencil icon sets the timer (HHMMSS).
- **Roulette**: shuffles a list and gives every task the same timer.
- **Settings**: keep screen awake, zoom, notifications (sound, vibration, screen flash, camera flash on API 23+), themes, backup export/import (API 19+).

Backups use the same JSON format as Txori (schema 1), so Txori backups can be imported.
