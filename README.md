# You

Your **music, movies and TV shows**, on your phone and your PC. Fully offline and private, and made to look like you.

- **No internet permission.** Android itself blocks the app from going online. The Windows program has no networking code.
- **No ads, tracking, analytics or accounts.** Nothing is collected or shared.
- **Your files stay where they are.** The app only reads the music and videos already on your device.
- **Your own colors.** Pick any color for each of the three tones, or use a picture as the background.

## Screens

These are design previews of the real screens. Photos from a phone will replace them.

<table>
  <tr>
    <td><img src="docs/screenshots/01-movies.png" width="180" alt="Movies"></td>
    <td><img src="docs/screenshots/02-tv-shows.png" width="180" alt="TV Shows"></td>
    <td><img src="docs/screenshots/03-artist.png" width="180" alt="Artist"></td>
    <td><img src="docs/screenshots/04-music-grid.png" width="180" alt="Music in Grid"></td>
    <td><img src="docs/screenshots/09-welcome.png" width="180" alt="Welcome"></td>
  </tr>
  <tr>
    <td align="center">Movies</td><td align="center">TV Shows</td><td align="center">Artist</td><td align="center">Music (Grid)</td><td align="center">Welcome</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/05-colors.png" width="180" alt="Colors"></td>
    <td><img src="docs/screenshots/06-profile-icon.png" width="180" alt="Profile and icon color"></td>
    <td><img src="docs/screenshots/07-ocean.png" width="180" alt="Ocean look"></td>
    <td><img src="docs/screenshots/08-picture-background.png" width="180" alt="Picture background"></td>
  </tr>
  <tr>
    <td align="center">Colors</td><td align="center">Profile and icon color</td><td align="center">Ocean look</td><td align="center">Picture background</td>
  </tr>
</table>

## Features

| Tab | What it does |
| --- | --- |
| **Movies** | Found automatically by file name. Poster from a video frame or your own picture, resume where you stopped, subtitles (.srt, .vtt), full screen, favorites, Continue Watching |
| **TV Shows** | Shows, seasons and episodes sorted automatically from file names, with the same posters, resume and subtitles |
| **Music** | Artists, albums, songs, playlists, favorites, synced lyrics (.lrc), cover art in the mini player |
| **Settings** | Profile picture and name, three-color theme with a color wheel, background picture, app icon color, Browse or Grid layout for each tab, Stop button, Clear my data |

**Browse** shows a big picture and rows. **Grid** shows a plain grid of covers. Each tab can use either.

## Progress

- [x] **Stage 1:** Music, Movies and TV Shows (copied from the private app this one comes from)
- [x] **Stage 2:** Your own colors: a color for each of the three tones, a background picture, a look for the whole app or for one tab
- [x] **Stage 3:** Profile picture and name, welcome screen, shortcut with your picture
- [x] **Stage 4:** App icon color (16 colors) on Android; icon color for the window, taskbar and shortcut on Windows
- [x] **Stage 5:** Android and Windows builds publish themselves on GitHub
- [ ] Tested on a real phone (not done yet; the Windows build is tested automatically)
- [ ] Permanent signing key (see the end of this page)

## Download and install

### Android (Android 11 or newer)

1. On your phone, open the [latest release](https://github.com/Blackout-78/You/releases).
2. Find the newest release whose name starts with **You for Android**.
3. Tap **You.apk** to download it.
4. Open the downloaded file.
5. If your phone asks, allow your browser to **install unknown apps**.
6. Tap **Install**.
7. Open **You**.
8. Tap **Allow** when it asks to read your music and videos.

### Windows

1. On your PC, open the [latest release](https://github.com/Blackout-78/You/releases).
2. Find the newest release whose name starts with **You for Windows**.
3. Click **You-Windows.zip** to download it.
4. Right-click the downloaded zip and choose **Extract All**.
5. Open the **You** folder and double-click **You.exe**.
6. If Windows says "Windows protected your PC", click **More info**, then click **Run anyway**.
7. Optional: double-click **Install shortcuts.bat** to add You to your Desktop and Start menu.

To remove it, double-click **Uninstall You.bat** in the same folder.

## Make it yours

1. Open **Settings**.
2. Under **Profile**, choose your picture and type your name.
3. Under **Colors**, tap **Light**, **Middle** or **Dark**, then drag on the color circle and the sliders.
4. To use a picture as the background, choose **Choose picture** and set **Dim picture** so the words stay easy to read.
5. To give Music, Movies or TV Shows its own colors, choose that name, then turn on **Use my own colors here**.
6. Under **App icon color**, tap a color dot to change the icon on your home screen.

## Build it yourself

Every push to this repository builds both apps automatically (see `.github/workflows`). The Android app is in `app/`, the Windows app is in `windows/`.

## For the maintainer: permanent signing key

The release key is stored encrypted in `release.jks.enc`. Until the secret below is added, builds use a throwaway test key, so a new version must be installed after uninstalling the old one.

1. Open the repository **Settings** > **Secrets and variables** > **Actions**.
2. Choose **New repository secret**.
3. Name: `KEY_PASSPHRASE`
4. Value: the passphrase from when the project was set up.
5. Choose **Add secret**.

## License

[MIT](LICENSE). Free to use, change and share.
