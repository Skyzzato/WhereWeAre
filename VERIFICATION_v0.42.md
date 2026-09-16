# Verifica v0.42 — 16 settembre 2026

Baseline `8d68ebd`, v0.41/10, working tree inizialmente pulita e allineata a
`origin/codex/v0.41`. Nessun AGENTS.md applicabile trovato. Branch di lavoro
`codex/v0.42`, Android 0.42/11; package e firma debug esistenti conservati.

## Verifiche

- Gradle Wrapper, task `:app:build`, senza aggiornare dipendenze/toolchain.
  Output isolati per evitare i problemi di file generati Windows già documentati
  nella v0.41. Log definitivo: `.tools/v042-release-build.log`.
- SQL locale PGlite: migrazioni 001–019, test SOS precedenti e nuovi,
  Luoghi, Check-in, gruppi temporanei, precisione e richieste posizione PASS.
  Migrazioni 001–017 non modificate; hash 001–007 confrontati con baseline PASS.
- Nuovi test SQL: opt-in OFF, consenso distinto dalla disponibilità, timestamp
  obsoleti/ritrasmessi, NaN, raggio, massimo 20, limite destinatario e cooldown,
  identità/coordinate protette prima dell'accettazione, revoca/ritiro/chiusura/
  scadenza, privilegi negativi, annullamento notifiche accodate e retry.
- Dodici richieste concorrenti della stessa registrazione producono un evento
  nel test API PGlite. **PGlite serializza una singola connessione:** il test non
  dimostra il comportamento dei lock con sessioni PostgreSQL indipendenti.
  Il lock transazionale e i vincoli univoci sono implementati, ma lo stress test
  multi-sessione rimane da effettuare nell'ambiente di test dedicato.
- Deno: 3 test dispatcher PASS (autenticazione/configurazione assente,
  payload di soli identificativi, accettazione distinta da coda/token assenti).
- Remoto: 018/019 applicate, bootstrap HTTP 200 0.42/11 e nearby_sos=true;
  parametri 900/900/2000/20 verificati, privilegi privati verificati.
  `remote_v042_smoke.sql` PASS con ruoli autenticati nel DB effettivo, rollback
  finale e nessuna notifica trasmessa. Non è un test di login/trasporto su due telefoni.
- Dispatcher distribuito tramite editor; POST non autenticato HTTP 401.
  Nessun secret FCM/dispatcher presente e nessun Firebase configurato nell'APK:
  non è stata provata una consegna push e non è dichiarata operativa.
- Rendering Robolectric dei componenti IT/EN, chiaro/scuro, 360×800 e font
  100%/160%, incluso editor senza icona. Le immagini sono fixture renderizzate,
  non screenshot di una sessione reale o prove di interazione con TalkBack.

## Comandi riproducibili

```powershell
.\gradlew.bat -I tools/isolated-v042-build.gradle :app:build --console=plain
node supabase/tests/run-v042.mjs
node tools/verify-v033-migrations.mjs
.tools/deno/package/deno.exe test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts
```

## Prove non eseguite

Nessun dispositivo ADB collegato. Da completare su telefoni di test: percorso
Impostazioni/Luoghi → icona presente → rimozione → salvataggio → riapertura →
nuova icona → riavvio, navigazione/accessibilità, revoca offline e cambio account,
precisione durante GPS intermittente, invio/ricezione SOS, background, processo
terminato, permessi negati, token reali e accettazione FCM. Il ciclo icone è
verificato nei dati SQL e nel rendering, non come interazione fisica completa.

Il progetto Firebase e la configurazione Android locale sono stati creati.
Rimangono l'identità server, i secret e lo scheduler descritti in SETUP_v0.42.md.
La ricezione push non è ancora verificata.

## Artefatto

- Build completa definitiva PASS: 93 test Android, zero failure/error; lint 0 errori e 64 warning.
- APK: `.tools/release-v0.42/WhereWeAre-v0.42-debug.apk`.
- Dimensione: **74331565 byte**.
- SHA-256: `51dc3a8b2b498bae0447d9a9461cfbcdcd36311d6cf35053a55dd838b0159c1c`.
- AAPT: package `com.whereweare.app`, versionName `0.42`, versionCode `11`, minSdk 26.
- Firma verificata da apksigner: Android Debug, certificato SHA-256
  `acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6`,
  uguale alla precedente build di test.
- Verifica HTTP remota finale: bootstrap 0.42/11, minimo client **4** invariato;
  RPC nearby_sos_status e send_sos_v042 con ruolo anonimo HTTP **401**.
- APK e checksum fuori dal Git sorgente; asset previsti della release di sviluppo v0.42.

Nessun codice/configurazione Android era stato modificato prima della pubblicazione di questa build.
I tentativi intermedi hanno rilevato un avvio prematuro del client di sessione:
corretto inizializzando la sincronizzazione in MainActivity, poi ripetuta l'intera
build con successo. Non sono stati disabilitati controlli lint o test.

## APK locale con Firebase (successivo alla release)

- Configurati i quattro parametri pubblici Firebase in `local.properties`, escluso da Git.
- `:app:assembleDebug` con `tools/isolated-v042-build.gradle`: BUILD SUCCESSFUL.
- APK: `.tools/firebase-setup/WhereWeAre-v0.42-firebase-debug.apk`.
- Dimensione: **74331565 byte**.
- SHA-256: `ecd44bf3730cf5583e1e9c4df3a2cd9c4865f8c287ab0e145b8f8ef959e0f73f`.
- Questa ricompilazione non costituisce una prova end-to-end delle notifiche.
- Gli asset e il tag della release GitHub v0.42 restano quelli già pubblicati.
