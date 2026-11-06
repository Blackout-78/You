# You

Your music, movies and TV shows, on your own phone or PC. Nothing leaves your device.

- **Offline.** The apps have no internet permission and no networking code. They collect nothing and share nothing.
- **Yours.** Pick your own picture and name. Pick your own colors: any color for the light, middle and dark tones, or a background picture of your choice.
- **Two looks.** Browse (a big picture and rows) or Grid (a plain grid of covers), set separately for Music, Movies and TV Shows.

## Install on Android

1. On your phone, open this page: https://github.com/Blackout-78/You/releases
2. Find the newest release whose name starts with **You for Android**.
3. Tap **You.apk** to download it.
4. Open the downloaded file.
5. If your phone asks, tap **Settings** and allow installs from your browser, then go back.
6. Tap **Install**.
7. Open **You**.
8. Tap **Allow** when it asks to read your music and videos.

To update later, download the newest **You.apk** and install it over the old one.

## Install on Windows

1. On your PC, open this page: https://github.com/Blackout-78/You/releases
2. Find the newest release whose name starts with **You for Windows**.
3. Click **You-Windows.zip** to download it.
4. Right-click the downloaded zip and choose **Extract All**.
5. Open the **You** folder.
6. Double-click **You.exe**.
7. If Windows says "Windows protected your PC", click **More info**, then click **Run anyway**. This message appears for every new free program that has no paid publisher certificate.
8. Optional: double-click **Install shortcuts.bat** to put You on your Desktop and in the Start menu.

To remove it, open the folder and double-click **Uninstall You.bat**.

## Make it yours

1. Open **Settings**.
2. Under **Profile**, choose your picture and type your name.
3. Under **Colors**, tap **Light**, **Middle** or **Dark**, then drag on the color circle and the sliders.
4. To use a picture as the background, choose **Choose picture** and set **Dim picture** so the words stay easy to read.
5. To give Music, Movies or TV Shows its own colors, choose that name, then turn on **Use my own colors here**.
6. Choose **Make a shortcut with my picture** to put a shortcut with your picture on your Desktop (Windows) or home screen (Android).

## Build it yourself

Every push to this repository builds both apps automatically (see `.github/workflows`).
The Android app is in `app/`, the Windows app is in `windows/`.

## For the maintainer: signed Android releases

The release key is stored encrypted in `release.jks.enc`. To use it for builds:

1. Open the repository **Settings** > **Secrets and variables** > **Actions**.
2. Choose **New repository secret**.
3. Name: `KEY_PASSPHRASE`
4. Value: the passphrase you were given when the project was set up. Keep it safe: with it, every update installs over the previous one.

Without this secret the build still works, but it uses a throwaway test key, so each new version must be installed after uninstalling the old one.

## License

MIT. See `LICENSE`.
