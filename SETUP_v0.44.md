# Configurazione verificata v0.44

Il progetto usato dalla configurazione locale dell'APK è `vqvouzpsgbuaddcyitzg` (WhereWeAre). Verifica del 16/09/2026 tramite HTTP pubblico e dashboard SQL autenticata. Bootstrap 0.43/12, SOS e vicini true, minimo client invariato. La versione Android 0.44/13 non richiede un aggiornamento forzato del bootstrap.

Nessuna migrazione nuova necessaria per le correzioni client identificate. Nessuna modifica retroattiva alle 001–020; nessun replay sul remoto. Verificate definizioni di app_metadata, visible_locations, sos_registered, contact_profiles_v03 e meeting_inbox, presenza di save_place_v043 ed end_nearby_availability; RLS attiva sulle tabelle pubbliche principali. Pubblicazione Realtime: share_requests, location_shares, sharing_status, latest_locations, account_events. Il controllo non pretende di essere un confronto byte-per-byte di ogni oggetto del database.

`supabase/tests/remote_v044_smoke.sql` è stato eseguito sul progetto: tre fixture temporanee, destinatario persona+gruppo, deduplicazione, lettura inbox, risposta, chiusura, estraneo negato, icona luogo, consenso persistente e privilegi. Tutto nella stessa transazione con rollback; controllo successivo: zero fixture residue. Nessun token FCM associato, nessuna ricerca vicini, nessuna chiamata al dispatcher. Sono prove con ruolo SQL authenticated, **non login reali attraverso Auth/PostgREST**.

## Blocco push confermato oggi

Dashboard Edge Functions: send-meeting-push presente; sezione Custom secrets: nessun segreto. Mancano FIREBASE_SERVICE_ACCOUNT e PUSH_DISPATCH_SECRET. Tabelle cron.job e vault.secrets vuote. I parametri Android FCM sono presenti nella configurazione locale, ma non completano la catena di consegna.

Non sono state create nuove identità amministrative o chiavi server. Restano da completare le operazioni FCM/Cron descritte in SETUP_v0.42.md con accessi e autorizzazioni adeguati, poi verificare ricezione su due dispositivi e apertura della schermata corretta. Non è stata attestata l'identità byte-per-byte del codice dispatcher remoto: non è stato cambiato in questa release e, senza segreti/scheduler, le push restano bloccate comunque.

Routing provider/endpoint e dominio HTTPS degli inviti non sono configurati. Non si inventano stime ETA o link HTTPS operativi. Codici e QR esistenti rimangono utilizzabili nelle condizioni già previste.

## Build

`./gradlew.bat -I tools/isolated-v044-build.gradle :app:build --console=plain`

Output debug: `.tools/build-v044/app/outputs/apk/debug/app-debug.apk`. Package com.whereweare.app, versione 0.44, versionCode 13, minSdk 26, target/compile 37. Firma debug esistente; non è una firma di produzione. La directory isolata evita la copia involontaria di artefatti v0.42/v0.43.

La prerelease contiene APK, checksum e BUILD_PROVENANCE.json con commit, variante, package, versioni e certificato. Conservare i dati dell'app aggiornando in-place quando la firma installata coincide; non è proposta alcuna disinstallazione.
