# artwork_incoming

Raw source artwork, before processing. Drop new art here; it gets resized,
renamed and converted into the app's real resources under
`shared/src/commonMain/composeResources/drawable/`.

These are the originals — roughly 1024×1024 PNGs, ~1 MB each. The versions the
app actually ships are far smaller (the whole processed set is about 11 MB),
so this folder exists to make the processed art reproducible, not because the
app reads from it. Nothing here is referenced at runtime.

## Naming convention

The processing step derives resource names from these filenames, so the
hyphenated names matter:

| Source file | Becomes | Notes |
| --- | --- | --- |
| `mascot-01.png` … `mascot-20.png` | `mascot_01` … `mascot_20` | The 20 cat portraits, which double as the coaches' base looks |
| `coach-<NN>-<theme>.png` | `coach_look_<NN>_<theme>` | Seasonal outfits. `<NN>` matches the mascot number |
| `app_icon.png` | app icon | Also the source for the Android adaptive icon and the iOS `AppIcon.appiconset` |
| `credits.png` | `credits_photo` | Shown in Settings |
| everything else | `ic_m_<name>` | Machine artwork, e.g. `bench_press.png` → `ic_m_bench_press` |

Valid outfit themes: `newyear`, `valentine`, `spring`, `summer`,
`backtoschool`, `halloween`, `thanksgiving`, `winterholiday` — matching
`CoachTheme` in `data/Models.kt`.

The app is written to be correct with zero, some, or all of the 160 possible
outfit images present, so partial sets are fine; each one starts appearing the
moment it's processed in.

## License

This artwork is licensed **CC BY 4.0** along with the rest of the project's
art and documentation — see [`../LICENSE-CC-BY-4.0.md`](../LICENSE-CC-BY-4.0.md).
The source code is MIT; see [`../LICENSE`](../LICENSE).
