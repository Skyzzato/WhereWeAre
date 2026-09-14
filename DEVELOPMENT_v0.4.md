# WhereWeAre — Troviamoci.

## Blocco 01 — audit della baseline

Base: `origin/main` aggiornato con fetch il 14 settembre 2026,
`26fe0bc` (aggiunge esclusivamente i criteri JVM Gradle alla v0.33 `fda3dd4`).
Branch di sviluppo: `codex/v0.4`. Nessuna riscrittura della storia.

Confermati direttamente in sorgenti e manifest: versione 0.33, codice 8,
minSdk 26, compileSdk/targetSdk 37, Compose, Hilt, DataStore, WorkManager,
Fused Location, MapLibre, cartografie OSM, PostgreSQL, REST/realtime e FCM.
I link custom person/group/meeting sono registrati; HTTPS rimane subordinato
alla configurazione esterna. Portrait e lingue IT/EN presenti.

LocationForegroundService è foreground di tipo location, START_STICKY e
stopWithTask=false. SharingController conserva sessione e revisione in DataStore;
StopSharingWorker ritenta lo stop. ACCESS_BACKGROUND_LOCATION non è richiesto.
Gli stili Bengala sono 50, verificati anche da V033Test.

Differenza dalla descrizione attesa: la baseline conserva soltanto l'ultima
posizione, non una cronologia GPS. Non introdurre né promettere uno storico
implicitamente. La predisposizione FCM non prova che Firebase e dispatcher
remoti siano configurati.

Migrazioni 001–007 immutabili: nuove modifiche solo da 008. Il manifest degli
hash Git è in `supabase/tests/baseline-v033.json`; il controllo locale
`node tools/verify-v033-migrations.mjs` ne verifica l'integrità.

Verifiche baseline:

- `gradlew.bat :app:build`: BUILD SUCCESSFUL; molte task già aggiornate,
  quindi non equivale a una nuova compilazione integrale. Log locale:
  `.tools/v04-baseline-build.log`.
- `node supabase/tests/run-v03.mjs --v033`: PASS, incluse regressioni,
  RLS e riapplicazione 007 su database locale PGlite. Nessuna migrazione remota.
- `adb devices -l`: nessun dispositivo; `emulator -list-avds`: nessun AVD.
- Nessuna occorrenza del nome della tecnologia backend nelle stringhe UI.

## Progressione

Versione e bootstrap rimangono 0.33/8 fino alla verifica della release completa.
Blocco 01 completato; blocchi 02–18 ancora da completare. Questo documento è
un registro di sviluppo, non una dichiarazione di disponibilità della v0.4.

Rischi da risolvere nel Blocco 02: la riconciliazione avviene nella Mappa e può
ordinare uno stop prima del recovery; lo stato locale diventa ON prima dell'ACK;
lo stop remoto viene osservato dal publisher soltanto al successivo fix; occorre
verificare la cancellazione fra vecchio servizio e nuova sessione. Non costruire
diagnostica o funzioni di sicurezza su stati ambigui.
