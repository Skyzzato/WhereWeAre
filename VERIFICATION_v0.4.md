# Verifica dello sviluppo locale v0.4

WhereWeAre — Troviamoci. · 16 settembre 2026.
**Esito: controlli locali passati, rilascio v0.4 NON approvato/completato.**
Versione conservata 0.33 (8). Il mancato arrivo è bloccato dallo scheduler non
verificato; routing e funzioni sperimentali non sono operativi.

## Controlli automatici eseguiti

| Verifica | Esito / evidenza |
|---|---|
| Build debug e release, test e lint | PASS, `.tools/v04-block17-final.log` e verifica finale `.tools/v04-final-build.log` |
| Lint residuo | 57 warning, nessun errore bloccante; report XML/HTML in app/build/reports |
| Test Android/JVM/Robolectric | 83, nessun fallimento |
| SQL locale PGlite | PASS 001–016, `.tools/v04-final-sql.log` |
| Riapplicazione migrazioni nuove | PASS, ogni migrazione ripetuta nel runner |
| Baseline 001–007 | Hash normalizzati LF invariati |
| Deno dispatcher | 3 test passati: accesso, payload, accettazione distinta dalla coda |
| Stringhe | 466 IT e 466 EN, nessun duplicato o chiave mancante |
| Versionamento | 0.33 / 8, nessun aggiornamento server/bootstrap di release |
| Struttura | 4 tab, portrait, 50 stili Bengala, START_STICKY conservati |
| Nuovi permessi | Solo CAMERA; nessun background location o CALL_PHONE |
| Routing nella build | Provider/endpoint vuoti, nessuna richiesta a demo |
| Dispositivi | `adb devices -l`: nessun dispositivo collegato |

La verifica finale aggiunge la 016: PostgreSQL accetta timestamp infiniti, ma
il client richiede date reali. Il vincolo rifiuta il valore e il test verifica
che una creazione fallita non lasci gruppo o permessi parziali.

## Cosa coprono i test

SQL: sessioni e recovery, sharing OFF, consensi direzionali/sovrapposti,
precisione raw/RPC, staleness, destinatari, scadenza gruppi senza cleanup,
idempotenza/rate limit, eventi privati, Check-in, convergenza prudente, luoghi,
regole/debounce, SOS/risposte/chiusura e nessuna accettazione push falsificabile.
PGlite verifica SQL/ruoli/RLS; non sostituisce un test remoto PostgREST/Realtime.

Android: logica e ViewModel, form/parsing, QR round-trip e stride immagini,
brightness restore, timestamp/precisione, ACK e mancato avvio FGS nei Check-in,
routing validato/fallback, candidati arrivo, countdown/annullamento/errore SOS,
inviti persistenti attraverso onboarding e ricreazione.

Durante alcune build Windows ha marcato non scrivibili file generati del lint.
Ripristinati solo gli attributi di `app/build` e ripetuti i task; i log finali
passano. Non sono stati cancellati sorgenti o aggirati test falliti.

## Prove ancora necessarie (NON dichiarate passate)

- Login, registrazione, logout/eliminazione account e avatar su backend reale.
- Permessi GPS precisi/approssimati/negati, camera e notifiche; QR tra telefoni.
- Schermo spento, Doze/OEM, rete assente/ripresa, processo terminato, riavvio e
  conferma server senza nuovo fix; vedere VERIFICATION_v0.4_BACKGROUND.md.
- UI/font grandi/TalkBack, sezioni, mappa, animazioni, tutti gli stili,
  scanner, inviti onboarding/login e lingua IT/EN su dispositivo.
- Realtime e REST remoti: revoca, precisione, gruppi scaduti e cambio account.
- FCM reale con scheduler/secret configurati, background, retry/lease, token
  invalidi e visualizzazioni/risposte SOS su destinatario separato.
- Regole sul territorio: jitter ai bordi, intervalli lenti, stato OFF e
  transizioni reali. Il telefono non esegue monitoraggio aggiuntivo.
- Endpoint routing affidabile, quote/carico/privacy e qualità dei sentieri/ETA.
- Scheduler mancato arrivo, arming/deadline/promemoria/proroga/annullamento
  server-side e prova con telefono mittente spento: funzione non implementata
  operativamente, prerequisiti in BLOCKED_OVERDUE_ALERTS.md.
- SOS vicini: servizio opt-in/discovery/antiabuso non disponibile; nessun test
  di produzione o scambio con sconosciuti eseguito.

## Comandi

```
.\gradlew.bat :app:build
node tools/verify-v033-migrations.mjs
node supabase/tests/run-v03.mjs --v04
deno test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts
```

APK locali: `app/build/outputs/apk/debug/app-debug.apk` (firma debug) e
`app/build/outputs/apk/release/app-release-unsigned.apk` (non firmato).
Non sono una release v0.4. Nessun upload, tag, push o deploy è stato eseguito.
