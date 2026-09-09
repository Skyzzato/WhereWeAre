# Verifica eseguita — 9 settembre 2026

## Android

Build eseguita con la configurazione reale in `local.properties`, escluso da Git:

```text
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
BUILD SUCCESSFUL in 21s
```

- JDK Temurin 21, Gradle 9.7.1, AGP 9.4.0, Android API 37.
- APK configurato: `app/build/outputs/apk/debug/app-debug.apk`.
- Unit test: 6 test, 0 errori, 0 fallimenti, 0 saltati.
- Lint rieseguito senza cache dei task dopo l'ultima modifica al manifest: **No issues found**, build riuscita in 20s.
- Report: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

## Supabase reale

Migration `supabase/migrations/001_initial_schema.sql` applicata integralmente tramite la sessione autenticata del SQL Editor al progetto `vqvouzpsgbuaddcyitzg`. Nessuna chiave amministrativa usata nel client o salvata nel repository.

Verifiche con due sessioni Auth reali e chiamate HTTPS PostgREST / WebSocket Realtime, usando esclusivamente la Publishable Key e i JWT utente:

- Login dei due account sintetici, profili automatici e codici personali diversi; ogni account legge soltanto il proprio profilo.
- Ricerca esatta per codice e invio richiesta; ricezione dell'evento sul secondo client.
- Accettazione con creazione delle due autorizzazioni direzionali.
- Avvio condivisione e pubblicazione di coordinate sintetiche da entrambi gli account.
- Letture reciproche e ricezione degli INSERT/UPDATE tramite Realtime, con sottoscrizioni confermate dal server.
- Revoca: posizione immediatamente assente dalla SELECT del destinatario e evento UPDATE ricevuto.
- Stop: posizione immediatamente assente dalla SELECT del destinatario e evento di stato ricevuto.
- Upload tardivo dopo Stop respinto con `sharing_stopped`.
- Scadenza: portata soltanto la coordinata sintetica a oltre due ore tramite SQL; la SELECT del destinatario la esclude, mentre la riga del proprietario esiste ancora. La protezione non dipende dal cleanup.
- Accesso anonimo alle tabelle respinto con HTTP 401.
- Job `whereweare-expiry` presente, attivo, pianificato ogni 15 minuti.

Gli account sintetici sono stati creati e confermati esclusivamente come fixture di collaudo e rimossi al termine, con cascata sui relativi dati applicativi. La conferma email globale è rimasta attiva. Il tentativo di signup con un indirizzo riservato `example.com` è stato correttamente rifiutato da Auth con `email_address_invalid`: **consegna email e registrazione completa con una casella reale non sono state collaudate**.

La variante ripetibile del test client è `supabase/tests/live-clients.mjs`: richiede due account nuovi di prova già confermati e credenziali nelle variabili ambiente descritte nel README. Non incorpora password o token.

## SQL locale

Migration eseguita anche su PostgreSQL 17.11 locale con ruoli Auth simulati. `supabase/tests/security.sql`: **26 asserzioni PASS**, oltre alle eccezioni attese; dati annullati con ROLLBACK.

Verificati anche non enumerabilità, richieste inverse e retry, negazione a terzi, timestamp futuri, sessioni precedenti, limite esatto delle due ore e protezione contro avvii tardivi dopo Stop.

## Collaudo ancora da eseguire su dispositivi

Non sono stati collegati due telefoni Android. Restano da verificare materialmente rendering MapLibre, permessi e acquisizione GPS, cadenza approssimativa di un minuto e comportamento del foreground service sotto le restrizioni energetiche dei dispositivi. Il test live verifica il backend e la consegna Realtime con due client distinti, non simula il sistema operativo Android. Seguire la procedura a due dispositivi nel README.
