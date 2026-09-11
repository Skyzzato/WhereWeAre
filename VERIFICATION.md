# Verifiche WhereWeAre

## Collaudo APK v0.2 — 11 settembre 2026

- Build debug con URL e chiave publishable del progetto reale in `local.properties` (escluso da Git). Nessuna chiave amministrativa nell'APK.
- Test Android: 12 test, 0 errori/fallimenti. Lint: 0 errori, 45 warning (44 risorse inutilizzate e 1 ObsoleteSdkInt).
- Suite SQL locale completa, inclusa regressione del trigger Storage: `ALL SQL TESTS PASSED`.
- Collaudo HTTPS/WebSocket reale `live-v02.mjs`: `ALL LIVE V0.2 TESTS PASSED`. Include regressioni condivisione/revoca/Stop/Realtime, gruppi e join ripetuti, scadenza scelta dal proprietario, isolamento da terzi, upload e download avatar, revoca degli accessi, invalidazione privata Realtime, cancellazione autenticata con avatar, JWT eliminato bloccato e cancellazione del creatore con cascata sui gruppi.
- Account creati esclusivamente per il test, con password casuali in memoria; cleanup completato anche durante le iterazioni fallite.
- Il collaudo ha individuato e corretto il trigger avatar: Storage persiste tramite una connessione interna senza `auth.uid()`. La migration 003 usa l'ownership verificata da Storage, conservando lock sul profilo e tombstone. [Riferimento ownership](https://supabase.com/docs/guides/storage/security/ownership).
- Verificata anche la revoca con richieste nuove: il client disabilita la cache HTTP e usa `cacheNonce` nelle letture dopo un cache miss RAM, evitando risposte CDN precedenti alla revoca. I dati già ricevuti non possono essere cancellati retroattivamente dai dispositivi altrui. [Riferimento CDN](https://supabase.com/docs/guides/storage/cdn/smart-cdn).
- APK universale, package `com.whereweare.app`, versione 0.2/code 2, min SDK 26, target 37; firma debug APK v2 verificata.

Limite esplicito: nessun dispositivo ADB collegato e nessun AVD configurato. Non sono state eseguite installazione/avvio su Android né prove fisiche di GPS, fotocamera/galleria, rendering mappa, batteria o foreground service. La release GitHub è pertanto una **prerelease di collaudo**, non una certificazione di produzione. I limiti del precedente deploy sotto sono superati soltanto per i casi coperti dal nuovo test live.

## Deploy remoto v0.2 — 11 settembre 2026 (Europe/Rome)

Progetto `vqvouzpsgbuaddcyitzg`, aggiornato tramite dashboard Supabase autenticato.

- Preflight: migration v0.2 assente, 3 profili presenti.
- Applicate in transazione le istruzioni di `002_v0_2.sql`: esito SQL `Success. No rows returned`.
- Dopo la migration: 3 profili e 3 righe `account_events`; sessioni delle posizioni migrate correttamente.
- RLS attiva sulle tre nuove tabelle; INSERT diretto su `groups` negato ad `anon` e `authenticated` (scritture tramite RPC).
- Bucket `avatars` privato, quattro policy avatar e trigger `avatars_guard` presenti.
- `account_events` presente nella publication `supabase_realtime`.
- Job `whereweare-expiry` assente, come previsto dalla conservazione dell'ultima posizione v0.2.
- `app_bootstrap()` eseguita anche con `SET LOCAL ROLE anon`, in transazione annullata: versione `0.2`, codice 2, minimo 2, manutenzione disattivata.
- Edge Function `delete-account` pubblicata dal sorgente del repository, verifica JWT attiva. POST senza Authorization e con token volutamente invalido respinti con HTTP 401.
- Nessuna credenziale amministrativa copiata nel repository o nel client; nessun account esistente eliminato durante questi controlli.

Limiti: i controlli HTTP della funzione verificano il rifiuto al gateway, non l'esecuzione completa della cancellazione. Restano da collaudare su account esclusivamente di prova cancellazione autenticata, upload avatar e gruppi end-to-end; restano inoltre le prove su due telefoni Android. I risultati v0.1 sotto sono storici, non una nuova esecuzione sulla v0.2.

Riferimento autenticazione: [Supabase — Securing Edge Functions](https://supabase.com/docs/guides/functions/auth).

## Verifica v0.1 — 9 settembre 2026

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

Primo collaudo con fixture confermate nel dashboard; successivamente, su richiesta del proprietario, **Confirm Email disabilitato**. Verificato `mailer_autoconfirm=true`, create due nuove utenze tramite `/auth/v1/signup` (HTTP 200), poi eseguito integralmente `supabase/tests/live-clients.mjs` con quelle credenziali: tutti i controlli PASS. I profili e i codici sono stati generati dal trigger durante la registrazione reale. Gli account temporanei sono stati rimossi dopo il test. Non è stato inviato alcun messaggio email nella modalità finale di collaudo.
La variante ripetibile del test client è `supabase/tests/live-clients.mjs`: richiede due account nuovi di prova già confermati e credenziali nelle variabili ambiente descritte nel README. Non incorpora password o token.

## SQL locale

Migration eseguita anche su PostgreSQL 17.11 locale con ruoli Auth simulati. `supabase/tests/security.sql`: **26 asserzioni PASS**, oltre alle eccezioni attese; dati annullati con ROLLBACK.

Verificati anche non enumerabilità, richieste inverse e retry, negazione a terzi, timestamp futuri, sessioni precedenti, limite esatto delle due ore e protezione contro avvii tardivi dopo Stop.

## Collaudo ancora da eseguire su dispositivi

Non sono stati collegati due telefoni Android. Restano da verificare materialmente rendering MapLibre, permessi e acquisizione GPS, cadenza approssimativa di un minuto e comportamento del foreground service sotto le restrizioni energetiche dei dispositivi. Il test live verifica il backend e la consegna Realtime con due client distinti, non simula il sistema operativo Android. Seguire la procedura a due dispositivi nel README.

### Configurazione Auth finale

Signup email/password abilitato, Confirm Email disabilitato su scelta esplicita del proprietario, Custom SMTP disabilitato. Il blocco SMTP non impedisce più la registrazione in questa modalità di collaudo. La verifica delle caselle e la consegna email restano fuori dal collaudo; per riattivarle occorre configurare SMTP nel dashboard.
