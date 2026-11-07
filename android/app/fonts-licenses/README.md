# Bundled fonts (fork-only, Saħħa looks)

The Sage and Conqueror looks bundle these open-licence typefaces under `src/main/res/font/`:

| Files | Typeface | Source | Licence |
|---|---|---|---|
| `sage_head_*.ttf` | Shippori Mincho (600/800) | npm `@fontsource/shippori-mincho@5.3.0` | SIL Open Font License 1.1 (`OFL-ShipporiMincho.txt`) |
| `sage_text_*.ttf` | Zen Kaku Gothic New (400/500/700) | npm `@fontsource/zen-kaku-gothic-new@5.3.1` | SIL Open Font License 1.1 (`OFL-ZenKakuGothicNew.txt`) |
| `conq_head_*.ttf` | Chakra Petch (600/700) | npm `@fontsource/chakra-petch@5.3.0` | SIL Open Font License 1.1 (`OFL-ChakraPetch.txt`) |
| `conq_text_*.ttf` | Inter (400/500/600) | npm `@fontsource/inter@5.3.0` | SIL Open Font License 1.1 (`OFL-Inter.txt`) |

The files are each family's fontsource latin and latin-ext WOFF2 subsets, decompressed and merged into one TTF with
`fontTools.merge`. Shippori Mincho and Zen Kaku Gothic New have no ħ/Ħ, which the Saħħa wordmark needs, so those
two glyphs were added as modified versions under OFL §3: each is the font's own h/H with a horizontal bar
added. The fonts keep their original names, since none uses a Reserved Font Name. All other glyphs are
unchanged.
None of the fonts contains Cyrillic or CJK, so Russian and Chinese text falls back to the system font.
