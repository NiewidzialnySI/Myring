# Audyt UI — Moje Krążki (2025-11)

## Problemy

1. **`enableEdgeToEdge()` bez insetów** — górny pasek zapasu (`padding(top = 24.dp)`) nachodził na status bar.
2. **Motyw zawsze jasny** — `darkTheme = false` w `MojeKrążkiTheme`; w nocy rażący biały ekran.
3. **Hardcoded kolory kalendarza** — nie reagowały na schemat Material.
4. **Brak spójnego paddingu** — ekran główny vs zapas vs ustawienia różnie odkładały treść od krawędzi.
5. **Typografia** — prawie tylko `bodyLarge`; brak hierarchii display/title/label.

## Wzorzec (Compose / Material 3)

- `enableEdgeToEdge()` + `statusBarsPadding()` / `navigationBarsPadding()` (lub `safeDrawingPadding`) — [Android edge-to-edge](https://developer.android.com/develop/ui/compose/layouts/insets)
- Jasne ikony status bara przy ciemnym tle (`WindowInsetsController.isAppearanceLightStatusBars`)
- Preferencja motywu: System / Jasny / Ciemny (jak w typowych apkach Material)
- Ciemna paleta: głęboki charcoal, nie czysty `#000`; miękkie on-surface

## Wdrożone

- Insety na Home + Setup
- Ciemny motyw + przełącznik w Ustawieniach
- Kolory kalendarza z `MaterialTheme.colorScheme`
- Większy odstęp treści od belki zapasu (`padding(top = 88.dp)` na okręgu)
- Uporządkowana typografia Material 3
