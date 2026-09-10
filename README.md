# WhereWeAre

Applicazione Android nativa, in italiano, per condividere volontariamente l’ultima posizione fra persone collegate tramite codice personale. Kotlin, Compose Material 3, Hilt, Fused Location Provider, MapLibre Compose e Supabase (Auth, PostgREST, Realtime). Nessuna cronologia GPS.

## Prerequisiti

- Android Studio con supporto AGP 9.4, JDK 21 consigliato (bytecode Java 17).
- Android SDK Platform 37.0 / API 37, Build Tools 36.0.0. Minimo Android 8 / API 26.
- Telefono con Google Play Services, oppure emulatore **Google APIs / Google Play** con posizione simulata.
- Progetto Supabase. Il client usa esclusivamente la chiave **publishable** o **anon**, mai `service_role` o una secret key.
- Connessione Internet per il primo download Gradle e per autenticazione, condivisione e cartografia.

Le versioni sono fissate in `gradle/libs.versions.toml`, senza alpha, beta o snapshot. Gradle Wrapper incluso. MapLibre Compose 0.16.0 è una release pubblicata; il progetto MapLibre segnala che la sua API non ha ancora raggiunto la stabilità 1.0.

## Configurazione Supabase

1. Crea un progetto. Nel SQL Editor esegui **tutto** `supabase/migrations/001_initial_schema.sql`. La migration è pensata per un progetto vuoto ed eseguita una sola volta, come una normale migration versionata. La creazione dei profili gestisce anche utenti Auth già esistenti.
2. In Authentication abilita Email e password. Imposta una lunghezza minima password di almeno 8 caratteri. L’app gestisce sia sessioni immediate sia conferma email: con conferma attiva, apri il link ricevuto e poi accedi nell’app. Configura Site URL per una pagina di conferma raggiungibile; non è richiesto un deep link Android.
3. In Database → Publications verifica che `supabase_realtime` contenga `latest_locations`, `share_requests`, `location_shares`, `sharing_status`. La migration le aggiunge. Realtime **Postgres Changes** applica le policy SELECT RLS al destinatario.
4. Verifica RLS attivo sulle cinque tabelle pubbliche. Le modifiche avvengono tramite RPC; l’unico UPDATE diretto consentito è il proprio `display_name`.
5. Se disponibile, `pg_cron` pianifica `whereweare-expiry` ogni 15 minuti. Controlla il job in Supabase Cron e i suoi esiti. Se l’estensione non è disponibile o manca il privilegio di abilitarla, la migration emette un NOTICE senza fallire.

### Cleanup alternativo

Esegui ogni 15 minuti, da un job server affidabile con connessione amministrativa PostgreSQL (mai dall’APK):

```sql
delete from public.latest_locations
where recorded_at < now() - interval '2 hours';
```

La scadenza delle letture è applicata dalla RLS anche se il job è assente o guasto. Il cleanup elimina anche la riga scaduta del proprietario; finché esiste, il proprietario può ancora leggerla.

## Configurazione Android

Copia `local.properties.example` in `local.properties` e compila i valori:

```properties
sdk.dir=C\:/Users/NOME/AppData/Local/Android/Sdk
SUPABASE_URL=https://IL_PROGETTO.supabase.co
SUPABASE_ANON_KEY=CHIAVE_PUBLISHABLE_O_ANON
MAP_STYLE_URL=https://tiles.openfreemap.org/styles/liberty
```

`local.properties` è escluso da Git. I valori confluiscono in BuildConfig: le chiavi client sono pubbliche per definizione. Non aggiungere segreti. Senza URL e chiave l’app compila e presenta un errore di configurazione quando si tenta l’accesso.

Apri questa cartella in Android Studio, esegui Gradle Sync e Run. Dalla console:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Su macOS/Linux: `./gradlew` con gli stessi task (eventualmente `chmod +x gradlew`). L’APK è `app/build/outputs/apk/debug/app-debug.apk`. Le build debug usano la firma di sviluppo; per distribuzione serve la propria configurazione di firma release.

Nell’ambiente in cui è stato generato il progetto, gli strumenti scaricati si trovano in `.tools/`, ignorata da Git. `local.properties` locale punta a quel SDK. Per usare la tua installazione Android Studio modifica `sdk.dir`.

## Utilizzo

1. Registra due account A e B e conferma le email se richiesto.
2. Copia il codice personale B da Impostazioni. A apre Persone → Aggiungi persona, cerca il codice esatto e invia la richiesta.
3. B accetta dalle richieste ricevute. L’accettazione crea due autorizzazioni distinte: A → B e B → A.
4. Avvia la condivisione da entrambi i dispositivi con l’app visibile. Consenti la posizione precisa o approssimativa e, da Android 13, le notifiche.
5. La mappa mostra i marker, nome, freschezza e, al tocco, precisione e data. “Centra su di me” acquisisce la posizione locale anche senza condividerla. “Mostra tutti” inquadra i marker; gli aggiornamenti remoti non spostano la camera.
6. Il selettore in Persone modifica **solo** chi può vedere la tua posizione. La direzione opposta è informativa.
7. Stop dalla notifica e dalla UI usano lo stesso controller. Logout ferma prima la condivisione e attende la conferma server.

## Privacy e comportamento di rete

- Le tabelle `profiles` non sono enumerabili. La ricerca richiede un codice completo, normalizzato e casuale (~39,6 bit), limita i risultati al minimo e applica un limite di 60 tentativi all’ora per account, condiviso con l’invio richieste. Nessun risultato per codici errati, propri o limite superato. Per una distribuzione pubblica configura anche limiti Auth, CAPTCHA e misure anti-abuso Supabase: creare molti account può aggirare un limite per account.
- L’accettazione è atomica, protetta da lock sulla coppia e da un indice unico sulle richieste pending in entrambe le direzioni. Doppio tap e retry non ripristinano un’autorizzazione revocata dopo l’accettazione.
- Il server salva una sola riga in `latest_locations`. Il client mantiene al massimo un fix da inviare in memoria; non crea file o code di coordinate. I nuovi fix sostituiscono i precedenti.
- `recorded_at = least(device_recorded_at, server_now)`; date future oltre 30 secondi e fix più vecchi di due ore sono rifiutati. `server_received_at` e `updated_at` sono assegnati dal server. Un client non può scrivere direttamente le date, estenderle nel futuro o pubblicare per altri utenti. Come per qualunque app senza attestazione hardware, il server non può provare che coordinate e tempo dichiarati corrispondano a un vero fix GPS.
- La RLS richiede autorizzazione **owner → viewer** attiva, stato generale ON e `recorded_at >= now() - interval '2 hours'`. Il client rimuove i marker scaduti ogni secondo usando un riferimento temporale server e un contatore monotono, senza query periodiche in condizioni normali.
- Stop/revoche diventano effettivi nel momento del commit server. Gli eventi sulle autorizzazioni e sullo stato invalidano subito i marker e provocano una nuova lettura RLS. Una query partita prima dell’invalidazione non può ripubblicare lo snapshot precedente.
- Un numero di revisione server impedisce a un avvio ritardato dalla rete di riaccendere la condivisione dopo uno stop. Un identificatore di sessione impedisce a un upload della vecchia sessione di proseguire dopo un riavvio.
- **Stop senza rete:** il GPS si ferma subito; nessun sistema può comunicare istantaneamente lo stop al server offline. L’app mostra esplicitamente “stop remoto in attesa”, salva soltanto l’intenzione di stop (ID utente, nessuna coordinata) e usa WorkManager per ritentare con rete disponibile. Fino alla conferma, gli autorizzati possono ancora vedere l’ultima posizione, al massimo fino alla scadenza. Nuovi avvii e logout sono bloccati finché lo stop non è confermato. Una revoca offline mostra errore e va ritentata; il selettore conserva il valore server.
- Il servizio è `START_NOT_STICKY`, non viene riavviato al boot e non richiede background location. Una terminazione di Android non equivale a uno stop esplicito: l’ultima posizione resta valida fino a due ore. La UI distingue un servizio terminato da una condivisione ancora visibile sul server.
- Realtime si collega durante l’utilizzo delle schermate, recupera uno snapshot dopo le riconnessioni e chiude i canali al logout o dopo l’uscita dalla UI. In assenza di rete conserva i dati in memoria fino alla scadenza e segnala che potrebbero essere superati. I tentativi di riconnessione non sono polling ordinario delle posizioni.
- I canali sottoscrivono soltanto INSERT e UPDATE: gli eventi DELETE di Supabase non applicano la stessa RLS di riga. Il cleanup viene riflesso dalla scadenza locale; stop e revoche sono UPDATE osservabili in sicurezza.
- I token sono gestiti dalla persistenza di Supabase Auth; backup Android disabilitato. Nessun log di password, chiavi, token o coordinate. Dati già ricevuti da una persona non possono essere cancellati dal suo dispositivo tramite RLS.

## Mappa e attribuzione

Motore **MapLibre Compose/Native**; dati **OpenStreetMap**, con stile OpenFreeMap sostituibile tramite `MAP_STYLE_URL`. Il componente mantiene i controlli di attribuzione del provider e il logo MapLibre; nelle impostazioni è presente il link al copyright OSM. Non usa Google Maps.

I server pubblici standard OpenStreetMap non sono un’infrastruttura gratuita illimitata per applicazioni distribuite su larga scala. Verifica termini, attribuzione e capacità del provider scelto. Sostituire lo style URL non richiede riscrivere `MapScreen`. Il primo caricamento e le aree non presenti nella cache richiedono rete; l’app non scarica regioni offline.

## Struttura

- `domain/`: modelli indipendenti, freschezza, validazione e autorizzazione client testabili.
- `data/`: DTO e mapping, repository Auth/Supabase, preferenze DataStore, acquisizione GPS.
- `service/`: foreground service, controller condiviso UI/notifica e worker per stop offline.
- `ui/`: quattro ViewModel distinti, StateFlow, Compose e messaggi centralizzati in `res/values/strings.xml`.
- `di/`: dipendenze singleton Hilt.
- `supabase/migrations/`: schema, trigger signup, RPC, RLS, Realtime e cleanup.
- `supabase/tests/`: verifiche SQL su database reale, con transazione finale annullata.

## Test automatici

`testDebugUnitTest` copre soglie freshness (30s, 5m, 45m, 1h59, 2h01 e confini esatti), combinazioni enabled/sharing/expired, normalizzazione e validazione, mapping senza inventare velocità o direzione.

Per eseguire i test SQL su **PostgreSQL vuoto di test**:

```powershell
psql -v ON_ERROR_STOP=1 -f supabase/tests/local_bootstrap.sql
psql -v ON_ERROR_STOP=1 -f supabase/migrations/001_initial_schema.sql
psql -v ON_ERROR_STOP=1 -f supabase/tests/security.sql
```

`local_bootstrap.sql` emula solo le primitive Auth necessarie e non va eseguito su Supabase. Su un progetto Supabase **di test**, esegui migration e `security.sql` con una connessione amministrativa; la suite usa account artificiali all’interno di una transazione che termina con ROLLBACK. Non usare un database di produzione.

I test SQL controllano profili, non enumerabilità, ricerca esatta, richieste inverse/retry, accettazione, accesso da terzi, revoca, stop, timestamp futuri, upload di vecchie sessioni e scadenza RLS senza cleanup. Non sostituiscono la verifica della consegna WebSocket Realtime nel proprio progetto Supabase.

## Verifica manuale su due dispositivi

1. Completa il collegamento A/B e avvia entrambi i servizi: osserva i marker e la notifica persistente.
2. Simula spostamenti in emulatore o usa dispositivi reali; verifica aggiornamenti circa ogni minuto, mantenendo ferma la camera scelta manualmente.
3. Disabilita rete su B: A mantiene la posizione, con età crescente e testo LIVE → RECENTE → VECCHIA. Entro un secondo dal superamento delle due ore il marker scompare anche senza eventi.
4. Riattiva rete B: viene inviata solo l’ultima posizione disponibile, non una traccia storica.
5. Revoca B → A mentre entrambi sono online: il marker B sparisce da A, A → B resta invariato.
6. Riabilita B → A; premi Stop sulla notifica B: la posizione sparisce da A subito dopo l’evento server. Ripeti lo stop dalla UI.
7. Prova Stop senza rete e riapertura app: controlla l’avviso e il completamento automatico al ritorno della rete.
8. Nega permessi, disattiva GPS, passa alla posizione approssimativa, cambia precisione, ruota lo schermo e prova logout/login con un altro account.
9. Termina realmente il processo B: nessun riavvio occulto del servizio; A vede l’ultima posizione fino a scadenza.
10. Con account C non collegato verifica che una SELECT manuale PostgREST non restituisca le posizioni A/B, anche conoscendo gli UUID.

## Riferimenti tecnici

- [Compatibilità AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Compose BOM e compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [MapLibre Compose](https://maplibre.org/maplibre-compose/getting-started/)
- [Supabase Kotlin releases](https://github.com/supabase-community/supabase-kt/releases)
- [Supabase Postgres Changes e RLS](https://supabase.com/docs/guides/realtime/postgres-changes)

## Stato del collegamento reale

## v0.2: applicazione della migration

`supabase/migrations/002_v0_2.sql` è incrementale e deve essere eseguita **una sola volta**, dopo `001_initial_schema.sql`. È stata applicata l'11 settembre 2026 al progetto `vqvouzpsgbuaddcyitzg` tramite il dashboard autenticato; anche la funzione `delete-account` è stata pubblicata. **Non rieseguire le migration su questo progetto.** I controlli e i limiti del collaudo remoto v0.2 sono in `VERIFICATION.md`.

Per un altro progetto già inizializzato con la v0.1, applica il file nel SQL Editor, oppure con una connessione amministrativa nel workflow Supabase usuale, e poi pubblica la funzione di cancellazione account:

```powershell
supabase functions deploy delete-account
```

La funzione usa `SUPABASE_SERVICE_ROLE_KEY` esclusivamente nell'ambiente Edge gestito da Supabase; la chiave non entra nell'APK. Conserva `verify_jwt=true` (default) per la funzione.

### Comportamento v0.2

- Il client distingue assenza di permesso, sola posizione approssimativa e posizione precisa. La modalità Alta precisione richiede realmente `ACCESS_FINE_LOCATION`; il valore mostrato è `Location.accuracy`.
- OFF non crea callback FLP. ON richiede subito un fix fresco; gli intervalli 5 s, 30 s, 1 min, 5 min, 30 min e 1 h sono persistenti. Per 5 minuti o più usa richieste singole con timeout e rilascia il GPS tra un ciclo e l'altro.
- `latest_locations` conserva una sola posizione personale. La RLS limita letture altrui con il timeout scelto dal proprietario, stato ON e sessione di pubblicazione corrente; il proprietario la legge sempre. Collegamenti diretti e gruppi sono canali indipendenti.
- Gli avatar sono WebP 512×512 in bucket Storage privato `avatars`; le policy autorizzano proprietario e contatti ammessi. Il client evita URL pubblici e mantiene una cache RAM a capacità limitata.
- Il bootstrap anonimo è soltanto una RPC read-only che restituisce `versionCode`, manutenzione e messaggio. L'ultima risposta valida viene cacheata; al primo avvio senza rete l'app resta bloccata.

Il test SQL locale usa PGlite e non accede al progetto remoto:

```powershell
node supabase/tests/run-local.mjs
```

Verifica comunque su due telefoni permessi Android, posizione precisa, GPS spento, la transizione Mappa → Persone → Mappa → Gruppi → Impostazioni → Mappa, fotocamera/galleria e il servizio in background prima della distribuzione.

Il 9 settembre 2026 lo schema è stato applicato al progetto Supabase `vqvouzpsgbuaddcyitzg`; Realtime e job Cron sono stati verificati. Il workspace e l'APK locale contengono la configurazione client fornita in `local.properties`, non versionato. **Non rieseguire la migration iniziale su questo progetto già configurato.** Le istruzioni iniziali restano valide per un nuovo progetto vuoto.

Per ripetere il collaudo HTTP/WebSocket con Node.js 24, prepara due account nuovi, confermati, destinati esclusivamente ai test. Imposta `WWA_TEST_EMAIL_A`, `WWA_TEST_PASSWORD_A`, `WWA_TEST_EMAIL_B`, `WWA_TEST_PASSWORD_B` nell'ambiente della shell, quindi dalla radice esegui:

```text
node supabase/tests/live-clients.mjs
```

Il test crea un collegamento reciproco, pubblica coordinate sintetiche, verifica gli eventi Realtime, revoca e Stop, poi ferma la condivisione. Usa le credenziali client di `local.properties`; non richiede service role. Elimina successivamente i soli account di prova dal dashboard. Il dettaglio dei risultati e dei limiti del collaudo è in `VERIFICATION.md`.

### Registrazione email: modalità di collaudo attiva

Su richiesta del proprietario, **Confirm Email è stato disabilitato**: è possibile registrarsi dall'app e accedere subito con email e password, senza messaggio di conferma. Due registrazioni via Auth e il successivo test completo HTTP/Realtime sono riusciti. Custom SMTP resta disabilitato.

Per passare alla verifica delle caselle email, configura un SMTP nel dashboard e riattiva Confirm Email. Il mittente predefinito Supabase invia soltanto ai membri dell'organizzazione. Le credenziali SMTP appartengono esclusivamente al dashboard, mai all'app.

Fonte: [Supabase — Custom SMTP](https://supabase.com/docs/guides/auth/auth-smtp).
