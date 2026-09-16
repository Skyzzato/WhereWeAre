# Verifica v0.41 — 16 settembre 2026

Baseline pulita `6339664`, v0.4/9, branch iniziale `codex/v0.4`.
Nessun AGENTS.md applicabile trovato nel repository o nelle directory superiori.
Sviluppo su `codex/v0.41`, Android 0.41/10. Nessuna migrazione modificata.

## Risultati locali

| Verifica | Esito |
| --- | --- |
| Build consolidata `:app:build` | PASS: debug e release compilati, 89 test, 0 failure/error |
| Ultime correzioni di etichette e lista richieste | PASS: 9 test mirati, lint, rigenerazione APK debug dal codice definitivo |
| Lint | 0 errori, 61 warning; non dichiarato privo di warning |
| SQL PGlite `node supabase/tests/run-v03.mjs --v04` | PASS migrazioni 001–017 e suite di sicurezza/regressione |
| Migrazioni storiche `node tools/verify-v033-migrations.mjs` | PASS hash 001–007; nessun file 008–017 modificato |
| Deno dispatcher (index/payload/delivery) | 3 PASS, 0 failure |
| RLS richieste | Mittente/destinatario leggono purpose/data/stati; estranei non enumerano; nascondere una riga non la nasconde all’altro utente |
| SOS | Deduplicazione persona + gruppo, una sola notifica in outbox; retry, countdown, annullamento, scadenza, chiusura, ACK e limiti coperti dalle suite esistenti/estese |
| Check-in | Suite SQL/Android: ACK/errore, snapshot, precisione, revoca, TTL 24 ore, rimozione/idempotenza; renderer usa lista persistita, non evento selezionato |
| Bengala | Nuove preferenze a ID 46; ID 1/30/31/46/50 conservati dopo riapertura DataStore; preset/renderer coerenti |
| Richieste | Mapping legacy/location, scadenza a 24 ore senza mutare gli altri stati, consenso non reciproco e assenza di avvio GPS all’invio |
| Gruppi | Creazione priva di data non inventa timestamp; scadenza continua a essere applicata |
| UI locale | 24 PNG Robolectric/native: IT/EN, chiaro/scuro, 360×800, font 100%/160%; avatar 24/40/72 dp, info, griglia e selettore SOS con nomi lunghi |
| Ispezione immagini | Controllati badge senza clipping, icone informazioni, griglia completa, contrasto, wrapping e pulsanti; corretta l’etichetta SOS rilevata nelle immagini |
| Versione/firma APK | AAPT e output metadata: 0.41/10, debug, minSdk 26; apksigner verifica firma v2 |
| Consenso/stop/background | Suite di regressione locale PASS; collaudo fisico non eseguito |

I PNG sono fixture di componenti reali, non screenshot di una sessione con
account e cartografia reali. Non provano gesti, TalkBack, navigazione tra tutte
le schermate, permessi Android o consegna di notifiche.

## Comandi e problema Windows

La cartella generata originale dei test non era eliminabile da Gradle.
Il tentativo di pulizia è stato rifiutato dal controllo automatico; non è stata
forzata alcuna rimozione. La build è stata completata in una directory distinta
con lo script `tools/isolated-v041-build.gradle`, che cambia solo gli output.
Nessuna modifica di dipendenze o firma per aggirare il problema.

```
.\gradlew.bat -I tools/isolated-v041-build.gradle :app:build --console=plain
.\gradlew.bat -I tools/isolated-v041-build.gradle :app:testDebugUnitTest --tests '*LocationRequestTest' --tests '*V041*' :app:lint :app:assembleDebug --console=plain
node supabase/tests/run-v03.mjs --v04
node tools/verify-v033-migrations.mjs
.tools/deno/package/deno.exe test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts
```

Log locali: `.tools/v041-final-build.log`, `.tools/v041-final-labels.log`,
`.tools/v041-delivery-build.log`, `.tools/v041-sql.log`, `.tools/v041-edge.log`.
Report 89 test conservati in `.tools/v041-full-test-results`; ultimo report mirato
in `.tools/build-v041/app/test-results/testDebugUnitTest`.
PNG in `app/build/reports/v041-ui`; report lint in `.tools/build-v041/app/reports`.
La build ordinaria `gradlew :app:build` resta il workflow per un checkout pulito.

## APK

- File: `.tools/release-v0.41/WhereWeAre-v0.41-debug.apk`
- Dimensione: 74.266.405 byte.
- SHA-256: `a2b48ffbbf06bb10c1b987f5f5bc53926b423096f272902012dde264c60698dc`
- Certificato debug SHA-256:
  `acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6`.
  Coincide con l’APK v0.4 disponibile: aggiornamento compatibile con quella firma.
- La variante release prodotta dalla build completa è non firmata e non viene
  consegnata come APK installabile.
- Binari e checksum vengono consegnati come asset GitHub, non nel Git sorgente.
  Tag `v0.41` sul commit del codice usato per l’APK; esito push verificato nella task.

## Server e prove mancanti

Nessun deploy remoto. Bootstrap letto HTTP 200: 0.4/9 e capability v0.4 true,
prossimità false. Le migrazioni locali non sono prova dell’effettivo contenuto di
ogni funzione distribuita. La lettura RLS delle richieste è verificata nel DB
locale, non tramite due sessioni autenticate reali sul server remoto.

**Firebase client incompleto: questo APK non inizializza FCM.** Restano eventi
server e refresh/realtime nell’app. Scheduler, dispatcher distribuito, secret
FCM e ricezione sul telefono non verificati. Routing non configurato.

SOS di prossimità indisponibile: Android e capability false; nessun registro
opt-in revocabile, disponibilità recente, ricerca non enumerabile/antiabuso o
protocollo identità/precisione verificato. SOS autorizzati restano separati.

Non c’erano dispositivi ADB o AVD configurati. Da effettuare su due telefoni:
SOS reale/annullo/risposte; ritrovo e check-in con riapertura e cambio stile;
richieste inviate/ricevute e transizioni; revoche durante uso; permessi negati;
GPS/stop/background/processo terminato e rete intermittente; salvataggio remoto
nome/avatar; tutte le schermate con TalkBack, testo grande e cartografia reale.
Nessuna consegna o affidabilità in emergenza è certificata dai test locali.
