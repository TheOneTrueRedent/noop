# Bundled fonts (fork-only, Slate layout)

The Slate layout bundles two open-licence typefaces under `src/main/res/font/`:

| Files | Typeface | Source | Licence |
|---|---|---|---|
| `slate_num_*.ttf` | Barlow (Medium, SemiBold, Bold) | github.com/google/fonts `ofl/barlow` | SIL Open Font License 1.1 (`OFL-Barlow.txt`) |
| `slate_text_*.ttf` | Figtree (static 400/500/600/700 instances cut from the variable font) | github.com/google/fonts `ofl/figtree` | SIL Open Font License 1.1 (`OFL-Figtree.txt`) |

The static Figtree weights were produced with `fontTools.varLib.instancer --static`; glyphs are unchanged.
Neither font contains Cyrillic or CJK, so Russian and Chinese text falls back to the system font.
