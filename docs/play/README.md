# Google Play listing graphics

| File | Format | Use in the console |
|---|---|---|
| `icon-512.png` | 512 × 512, 32-bit PNG with alpha, 3 KB | App icon (square, no rounded corners: Play applies the mask). Same artwork as `app/src/main/res/drawable/ic_launcher.xml`. |
| `feature-graphic-1024x500-fr.png` | 1024 × 500, 24-bit PNG without alpha | Feature graphic, French listing. |
| `feature-graphic-1024x500-en.png` | same | Feature graphic, English listing. |
| `captions.html` | standalone page, no network | Adds the caption to the 4 raw screenshots you take yourself. |

Sources: `src/icon.svg`, `src/feature-graphic.html` (Inter font, SIL OFL 1.1, not included: the render was made with Inter loaded locally).

## Screenshots

The screenshots are **not** in this folder: they require running the app (emulator or phone).

1. Install a build that contains the "EcoDrive OBD" rename (the header of the Live screen shows it).
2. Take 4 screenshots in **simulator mode**, without captions, at exactly **1080 × 2160** (2.0 ratio) or 1080 × 1920, in this order: live (the "Direct" tab), trip (the "Trajet" section of that same screen), map (the "Carte" tab), vehicle (the last tab). On the emulator: a "Pixel" AVD with a custom 1080 × 2160 resolution; on a 20:9 phone the screenshot comes out at 1080 × 2400 (2.22 ratio) and will be rejected.
3. Open `captions.html` in a browser, choose the 4 files, check the captions, and download the 4 JPEGs (no transparency). The tool rejects any size other than 1080 × 2160 or 1080 × 1920: it neither stretches nor crops. The tool's interface is in French; the language selector only switches the captions.
