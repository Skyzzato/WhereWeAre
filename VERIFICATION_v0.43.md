# Verifiche v0.43

## Verifiche eseguite

- SQL locale: node supabase/tests/run-v043.mjs. Migrazioni 001–020, test v043 e regressioni SOS, adesione, rate limit, luoghi/regole, check-in, gruppi temporanei, precisione e richieste posizione. PASS. Dodici chiamate idempotenti concorrenti producono un evento su singola connessione PGlite; non è una prova di concorrenza fra connessioni PostgreSQL indipendenti.
- Baseline: node tools/verify-v033-migrations.mjs, PASS; 001–007 immutate.
- Remoto: stessa suite v043.sql via SQL Editor con ruolo authenticated, fixture temporanee e rollback. PASS; bootstrap finale 12/0.43, zero utenti fixture rimasti. Coordinate solo sintetiche; SOS senza fix e senza destinatari ordinari, nessun invio a utenti reali. HTTP pubblico bootstrap 200 e RPC protette negate 401.
- Android: comando e risultati finali sotto. Test mirati includono ACK, doppio tap, risposta persa recuperata, riapertura con DataStore, ID invariato al retry, assenza di retry automatici tardivi, autorizzazione rifiutata persistente, timeout prima della registrazione e normalizzazione nomi.
- Rendering Robolectric: banner SOS IT/EN, chiaro/scuro, font 100%/160%, 360×800. Immagine italiana scura al 160% ispezionata: titolo, testo, icona e azioni leggibili. Non sono screenshot da telefono.
- Dispatcher Deno: 3 test PASS, inclusa distinzione fra coda e accettazione push.

## Non eseguite

Installazione/aggiornamento su telefono, TalkBack, interazione manuale completa con editor, multiutente fisico, cold start e rinnovo JWT reali, Wi-Fi/dati mobili, perdita/ripresa rete su Android, Doze, terminazione del processo da sistema e FCM end-to-end. Persistenza e risposte perse provate con repository e DataStore di test, non simulando un guasto di rete reale sul telefono. Nessuna prova di concorrenza DB con connessioni indipendenti. I test non certificano l’affidabilità di un servizio di emergenza.

## Riproduzione

~~~powershell
.\gradlew.bat -I tools/isolated-v043-build.gradle :app:build --console=plain
node supabase/tests/run-v043.mjs
node tools/verify-v033-migrations.mjs
.tools/deno/package/deno.exe test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts
~~~

## Risultati e artefatto

Build completa :app:build PASS (log locale .tools/v043-verified-build.log): 101 test, 0 errori/fallimenti/skipped; lint 0 errori, 65 warning. Dopo la sola rifinitura delle etichette della modifica regola e della posizione del pulsante Aggiungi luogo: ricompilazione debug e lint dedicati, log .tools/v043-apk-build.log.

Firma verificata con apksigner: certificato SHA-256 acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6, uguale all’APK v0.42 locale. Package com.whereweare.app, variante debug, versionName 0.43, versionCode 12. L’aggiornamento è compatibile con la firma precedente; non è stata provata l’installazione fisica. Artefatto consegnato: .tools/release-v0.43/WhereWeAre-v0.43-debug.apk; checksum nel file SHA256SUMS.txt della stessa directory e della release.

Gli errori intermedi di build sono stati risolti: fixture Mockito per eccezioni Kotlin, logging Android nei test JVM, inizializzazione Firebase duplicata e indentazione del selettore estratto. Nessuna regola lint disabilitata.
