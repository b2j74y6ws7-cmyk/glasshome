# Glass Home

Лаунчер для Android TV в стиле «стекло»: приветствие, часы, дата, панель приложений, сцены.
Без погоды, рекламы и аналитики.

## Как получить APK (без установки программ на компьютер)
1. Создайте бесплатный аккаунт на github.com.
2. Нажмите «+» -> New repository -> имя `glasshome` -> Create repository.
3. На странице репозитория нажмите «uploading an existing file».
4. Перетащите ВСЁ содержимое этой папки (включая скрытую папку .github) в окно браузера -> Commit changes.
5. Вкладка Actions -> «Build APK» -> дождитесь зелёной галочки (3-6 минут).
6. Откройте завершённый запуск -> внизу «Artifacts» -> скачайте GlassHome-apk (zip) -> внутри app-debug.apk.
7. Переименуйте в glasshome.apk и положите в папку apks рядом с install.bat.

Если папка .github не загрузилась: Add file -> Create new file, имя `.github/workflows/build.yml`,
вставьте содержимое файла build.yml из этой папки.

## Своя картинка вместо сцены
Положите JPG на приставку:
adb push wallpaper.jpg /sdcard/Android/data/com.glasshome.tv/files/wallpaper.jpg
и выберите «Своя картинка» в настройках.
