# WhereWeAre v0.3 — configurazione

## Migrazione Supabase

Fare un backup del database prima dell’aggiornamento. Applicare tramite migration runner o SQL Editor `004_v0_3.sql` dopo 001, 002 e 003. Lo script è transazionale e preserva profili, codici precedenti, relazioni e appartenenze esistenti. La colonna `sharing_enabled` dei membri esistenti parte da true, mantenendo il comportamento precedente.

La versione minima client diventa **4**: le vecchie app devono aggiornarsi perché il codice gruppo ora produce una richiesta, invece di un ingresso immediato. Il nuovo client richiede `api_version=3`. Per un progetto vuoto applicare tutte le migrazioni in ordine. Non applicare la 004 due volte.

Verificare Auth email/password, conferma email e Site URL. Conservare la pubblicazione Realtime già esistente per `latest_locations`, `share_requests`, `location_shares`, `sharing_status`, `account_events`. Le nuove funzioni generano invalidazioni prive di payload attraverso `account_events`; non occorre esporre nuove tabelle Realtime.

Mantenere bucket privato avatar, policy storage e funzione `delete-account` delle versioni precedenti. Il client non deve usare una chiave amministrativa.

## Firebase Android (facoltativo)

Non è necessario per mappa, gruppi e banner in-app. La build consegnata lascia FCM inattivo perché il progetto Firebase non esiste ancora.

1. Creare un progetto Firebase e registrare un’app Android con package **com.whereweare.app**.
2. Scaricare `google-services.json` dalla console. L’integrazione usa `FirebaseOptions` esplicite: il plugin Google Services e la copia del JSON nel repository non sono necessari.
3. Riportare in `local.properties` questi soli valori pubblici del JSON:

| Proprietà locale | Campo JSON |
| --- | --- |
| FIREBASE_APP_ID | client del package corretto → client_info.mobilesdk_app_id |
| FIREBASE_API_KEY | stesso client → api_key[0].current_key |
| FIREBASE_PROJECT_ID | project_info.project_id |
| FIREBASE_SENDER_ID | project_info.project_number |

4. Ricompilare e installare l’APK. Dopo login, su Android 13+ l’app richiede il permesso notifiche; la registrazione del token usa una RPC autenticata. È richiesto un dispositivo/emulatore con Google Play Services.
5. Se la configurazione è incompleta Firebase non viene inizializzato. Non aggiungere service-account o service-role al JSON/BuildConfig del client.

Riferimento: [configurazione Firebase Android](https://firebase.google.com/docs/android/setup), [FCM Android](https://firebase.google.com/docs/cloud-messaging/android/get-started).

## Dispatcher push server

1. Abilitare Firebase Cloud Messaging API HTTP v1 nel progetto Google Cloud.
2. Creare un service account con il solo ruolo necessario a inviare messaggi FCM; conservare il relativo JSON come secret Supabase `FIREBASE_SERVICE_ACCOUNT` (una stringa JSON completa).
3. Generare una stringa casuale lunga e salvarla come secret `PUSH_DISPATCH_SECRET`. Non conservarla nel repository o nell’APK.
4. Distribuire `supabase/functions/send-meeting-push` con Supabase CLI. `supabase/config.toml` disattiva il JWT gateway solo per questa funzione; la funzione verifica autonomamente `Authorization: Bearer <PUSH_DISPATCH_SECRET>` e accetta soltanto POST. `delete-account` mantiene la verifica JWT.
5. Configurare Supabase Cron/Vault o uno scheduler server affidabile per un POST ogni minuto a `https://<project-ref>.supabase.co/functions/v1/send-meeting-push`, con il suddetto header. Memorizzare il secret in Vault/configurazione segreta dello scheduler, non nel testo SQL condiviso.
6. Verificare risposta HTTP 200 con conteggio `completed`, log della funzione e coda. Non registrare token, payload GPS o chiavi nei log.

Il server usa `SUPABASE_SERVICE_ROLE_KEY` disponibile nelle Edge Functions. `claim_push_batch` e `complete_push` sono eseguibili solo dal service role. La coda ha lease di 5 minuti, massimo 8 tentativi e batch massimo 50; per carichi elevati ridurre il batch o aumentare la concorrenza del dispatcher rispettando il timeout della piattaforma. I job falliti rimangono disponibili all’ispezione amministrativa.

Il messaggio FCM contiene solo ID meeting, destinatario e tipo evento. Il worker Android rilegge il meeting con la sessione corrente e RLS prima di creare una notifica. Deduplicazione persistente distinta da quella del bengala. In primo piano resta il banner. Il deep link interno è `whereweare://meeting/<uuid>`.

Android può limitare o rinviare i messaggi (rete, Doze, restrizioni del produttore); un force-stop da Impostazioni impedisce la consegna fino alla riapertura. Verificare la consegna reale dopo configurazione: non è stata testata in questo ambiente.

## Link futuri

Le RPC `create_invite_link` e `resolve_invite_link` sono pronte: token casuali di 32 byte, SHA-256 nel database, scadenza 7 giorni. La risoluzione mostra un’anteprima, la conferma crea una richiesta; non concede GPS.

Il formato previsto è `https://whereweare.app/join/<token>`. Il dominio non è stato configurato o pubblicato. Il manifest ha `autoVerify=false`; la UI non genera link pubblici mentre il flag `invite_links` è false. Prima di abilitarli, pubblicare pagina fallback e `/.well-known/assetlinks.json` con package e fingerprint SHA-256 della firma reale, abilitare autoVerify e collaudare la risoluzione. I codici brevi continuano a funzionare indipendentemente dal dominio.

## Statistiche e configurazione

`private.usage_summary` espone conteggi aggregati solo all’amministratore; `private.daily_usage` conta aggiornamenti GPS senza coordinate o ID utente. Per errori backend usare log Edge/Postgres con codici sanitizzati, mai payload GPS. Nessuna dashboard pubblica è stata aggiunta.

`private.client_events` è disattivata tramite `features.client_analytics=false`. Il client ha eventi tipizzati per preferenze; il server ammette soltanto enumerazioni di tema, scala, lingua e codici errore. Versione app e timestamp sono limitati; 60 eventi/ora per utente. Per attivare la raccolta, definire prima accessi amministrativi, informativa e retention adeguati al prodotto.

`private.app_bootstrap.defaults`: intervallo GPS, alta precisione, tema, scala avatar, tolleranza posizioni vecchie. Le preferenze già salvate hanno precedenza. `features`: meeting_points, invite_links, client_analytics. `private.code_policy` e rate limit sono parametri server di sicurezza, non feature flag modificabili dal client. I privilegi RLS non dipendono dai flag UI.

Pianificare con credenziali amministrative la pulizia dei link scaduti, delle push concluse e degli eventi analytics secondo la retention scelta. Non cancellare i meeting attivi. Non ripristinare il vecchio cleanup GPS a due ore.

## Test SQL locali

Il runner usa PGlite con pgcrypto, senza connettersi a Supabase reale. Installare/estrarre `@electric-sql/pglite` in `.tools/pglite/package` in modo che esistano `dist/index.js` e `dist/contrib/pgcrypto.js`, quindi eseguire `node supabase/tests/run-v03.mjs`. Le directory `.tools` e gli eventuali pacchetti scaricati sono esclusi da Git.
