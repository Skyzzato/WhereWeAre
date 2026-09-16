# Verifica v0.44 — build di collaudo

Ambiente: Windows, JDK/Gradle del progetto, Android SDK 37, Robolectric API 34, PGlite locale, Deno locale; progetto remoto vqvouzpsgbuaddcyitzg via SQL Editor. Nessun dispositivo in `adb devices -l`, nessun AVD in `emulator -list-avds`. Nessuna credenziale WWA_TEST_EMAIL/PASSWORD configurata per test Auth/PostgREST. Nessuna chiamata reale al 112, nessuna notifica SOS a utenti reali.

Build: package com.whereweare.app, debug, versionName 0.44, versionCode 13. Il commit esatto dell'artefatto pubblicato è riportato in BUILD_PROVENANCE.json allegato alla release. I test si riferiscono al codice di quel commit; le differenze rispetto alla baseline 639031f sono descritte in AUDIT_v0.44.md.

## Prove automatiche e risultati

- Baseline v0.43: testDebugUnitTest PASS, 101 test preesistenti.
- Suite Android finale: `:app:build`, test JVM/Robolectric, APK debug e release, lint. Risultato finale: 113 test, 0 fallimenti/errori/skipped; lint 0 errori e 66 warning. Riepilogo JUnit per suite nel BUILD_REPORT.json allegato. Nessun test rimosso, nessun controllo lint disabilitato. La variante consegnata è debug; l'APK release non firmato non viene presentato come installabile.
- Nuovi test: pulsante SOS effettivamente cliccato senza capability caricate; SOS esistente apre il proprio dettaglio; distinzione parsing/rete; errore di un servizio non cancellato dal successo di un altro; risposta pubblica non verifica sessione; banner coerente con tentativo reale; metadata disponibili prima delle posizioni; 30 cicli di stato attivo mantengono marker; stop/revoche rimuovono dati; retry servizio e arresto su errore permanente.
- Riproduzione del loop servizio: con errore permanente e tempo virtuale di 60 secondi, la versione del loop prima della correzione effettuava **21 tentativi**. Il test richiede un solo tentativo e arresto del servizio. Un secondo test verifica backoff a 2 e 4 secondi e `retrying=false` durante l'attesa. Evidenza prima: `.tools/v044-final-build.log` e rapporto JUnit di quella esecuzione; evidenza dopo: suite finale. È tempo virtuale, non una misura di rete sul telefono.
- SQL finale: `node supabase/tests/run-v044.mjs` PASS. Migrazioni 001–020, sicurezza storica eseguita sul relativo schema, poi device status, sharing recovery, precisione, richieste posizione, check-in, gruppi/scadenze, convergenza, luoghi, SOS, vicini/limiti, v043 e smoke v044. Le prime prove del nuovo runner avevano applicato test con versione/retention storiche allo schema finale: assert falliti; il runner ora esegue quei test allo stadio corretto senza cambiarli o eliminarli.
- `node tools/verify-v033-migrations.mjs` PASS: baseline SQL 001–007 invariata. Nessuna delle migrazioni 001–020 modificata.
- Deno: 3 test PASS per autorizzazione dispatcher, payload e distinzione coda/accettazione. Non attestano FCM end-to-end.
- Isolamento test: il nuovo scenario di arresto richiede uno scheduler. Avviare WorkManager reale nel test locale interferiva con il caricatore nativo Robolectric (FileSystemAlreadyExistsException/UnsatisfiedLinkError nelle prove grafiche). Il test usa ora un delegate di scheduler, verifica l'accodamento dello stop e il suo intento persistente, poi rimuove il delegate. Nessun test grafico è stato disabilitato. Corretto anche il matcher Mockito che passava null a parametri Kotlin non-null.
- SQL remoto: remote_v044_smoke.sql PASS. Tre fixture, ruolo authenticated, persona/gruppo, inbox, risposta, chiusura, idempotenza, estraneo e anonimo, luoghi e disponibilità; rollback e zero utenti fixture residui. Ricerca vicini confinata ai test PGlite locali; sul remoto nessun fix SOS e nessuna selezione vicini.
- Contratti remoti e configurazione: bootstrap HTTP 200; metadata SQL restituisce SOS/Luoghi true sui quattro account presenti; nessuna precisione null nelle tre posizioni presenti al controllo aggregato. RPC 020 presenti, RLS principali attive, cinque tabelle Realtime pubblicate. Nessun contenuto personale copiato nel report.

## Scenari richiesti

PASS locali/SQL non vengono promossi a PASS end-to-end. Ogni riga riporta precondizione, azione/atteso e risultato osservato.

| ID | Precondizione e passi / atteso | Ambiente e risultato osservato | Esito complessivo |
| --- | --- | --- | --- |
| A avvio/login/ripresa | Account test, avvio e riapertura, sessione ripristinata | Bootstrap/onboarding e ripresa servizio testati localmente; nessun login API reale | BLOCCATO end-to-end |
| B upgrade 0.42/0.43 | APK precedente installato, aggiornamento senza perdita dati | Versioni/package/firme confrontati, hash precedenti uguali a GitHub; installazione non disponibile | NON ESEGUITO installazione |
| C condivisione/revoca | A autorizza B, pubblica, ferma/revoca; B perde accesso | SQL precisione/sicurezza e repository PASS | PASS SQL; BLOCCATO telefoni |
| D mappa continuativa | Più cicli, pan/zoom, nessuna perdita ingiustificata marker | 30 cicli di invalidazione PASS; camera/style audit; nessuna GPU nativa/video | NON ESEGUITO grafico |
| E rete/trasporti | Offline, ritorno e Wi-Fi/mobile, ripresa controllata | IOException, timeout e backoff simulati PASS | BLOCCATO rete Android reale |
| F token | Scadenza/rinnovo, nessun loop o funzioni nascoste | Classificazione 401, tentativo limitato nel codice; nessun JWT reale scaduto | NON ESEGUITO Auth end-to-end |
| G SOS persona/gruppo/vicino | A invia, B riceve; C escluso | Persona/gruppo remoto SQL PASS; vicino solo DB locale PASS; FCM mancante | BLOCCATO ricezione dispositivi |
| H doppio tap/timeout/riavvio SOS | ID stabile, esito incerto recuperabile, nessun reinvio automatico | V043Test, SosTest, SQL retry e tombstone PASS | PASS repository/SQL |
| I consenso/disponibilità | Adesione conservata con fix/disponibilità scaduti | v043.sql e smoke remoto PASS | PASS SQL |
| J luoghi/regole | CRUD, duplicati normalizzati, reinserimento icona, eliminazione regole | PlacesTest, v043.sql, places_rules.sql e smoke remoto PASS | PASS logica/SQL; UI fisica non eseguita |
| K ritrovo/Bengala | Destinatari, convergenza, suono e riunione | Feedback/progress/convergence PASS; animazioni/suono multiutente non eseguiti | BLOCCATO end-to-end |
| L check-in/inviti/richieste | Invio con consenso, TTL, invito conservato al login | Test dominio/onboarding/SQL PASS | PASS locali; link HTTPS non configurato |
| M background | Schermo spento, Doze, terminazione processo, distinguere force-stop | Ripresa repository testata; nessun telefono/AVD e FCM mancante | BLOCCATO |
| N logout/cambio account | Nessun dato precedente né sottoscrizione residua | Epoch diagnostica, DataStore SOS e revoche testati localmente | PASS locali; sessioni reali non eseguite |

## Evidenze e riproduzione

```powershell
.\gradlew.bat -I tools/isolated-v044-build.gradle :app:build --console=plain
node supabase/tests/run-v044.mjs
node tools/verify-v033-migrations.mjs
.tools/deno/package/deno.exe test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts
```

Output JUnit/lint: `.tools/build-v044/app/test-results/testDebugUnitTest/` e `.tools/build-v044/app/reports/`. Log finali e riepilogo senza segreti allegati alla release. SQL locale usa il pacchetto PGlite già presente in `.tools/pglite`; non è una prova di concorrenza PostgreSQL con connessioni indipendenti. Il test v043 aggiuntivo ha verificato 12 chiamate idempotenti su una sola connessione PGlite, esplicitamente serializzata.

Firma SHA-256 verificata sull'APK nuovo e sulla v0.43 pubblicata: `acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6`, uguale alla baseline v0.42 documentata. Compatibilità crittografica di aggiornamento confermata; installazione in-place non eseguita. Checksum dell'APK e commit sono negli allegati SHA256SUMS.txt e BUILD_PROVENANCE.json, generati dall'output finale.

## Verdetto

**Prerelease di test, non stabilizzazione certificata.** Restano essenziali prova nativa dello sfarfallio, login/rinnovo reale, aggiornamento installato, due telefoni più account estraneo, background/OEM, push FCM e concorrenza DB indipendente. La mappa continua a invalidare dati quando un evento account potrebbe rappresentare una revoca: non si sacrifica la privacy per promettere continuità visiva. Il guasto specifico segnalato dall'utente non è stato attribuito a una causa di rete/server non dimostrata.
