# Aggiornamento a WhereWeAre v0.33

Versione Android 0.33, codice 8. Il progetto conserva la configurazione Supabase esistente e la firma debug locale. Non disinstallare la vecchia app per aggiornare se la firma coincide: installare l’APK sopra la versione precedente.

## Database

Su un database già aggiornato alla v0.32 eseguire **solo** `supabase/migrations/007_v0_33.sql` nel SQL Editor Supabase. Se manca la 006, eseguire prima `006_v0_32.sql`. Non rieseguire le migrazioni iniziali su un database esistente. La 007 è transazionale e può essere ripetuta; il test locale la applica due volte.

La 007 aggiunge `saved_people` con RLS per il proprietario e le RPC `save_group_person` e `request_saved_person`. Il salvataggio non crea righe `location_shares`, non abilita visibilità di gruppo e non estende `private.related` o `can_read_location`. Il consenso GPS continua a passare dal flusso esistente. Le RPC metadata/profili includono i contatti salvati; eliminazione collegamento e notifiche di aggiornamento profilo sono coerenti. `create_meeting_styled` accetta 1–50 e preserva lo stile originario nei retry. I vecchi client continuano a usare le API precedenti; minimo supportato 4.

La verifica con PGlite è locale: **non equivale all’applicazione sul progetto Supabase remoto**. Non è stata eseguita una migrazione remota in questa iterazione. La build necessita della 007 per “Aggiungi a Persone” e per i nuovi razzi.

## Inviti: scelta concordata senza hosting

Lasciare `INVITE_BASE_URL=` vuoto. Le azioni Condividi inviano un codice utilizzabile, il nome/emoji del gruppo quando pertinente e istruzioni di inserimento. Nessun dominio fittizio e nessuna promessa di apertura automatica da WhatsApp. I codici personali si inseriscono in Persone → Aggiungi persona; quelli gruppo in Gruppi → Entra con codice. Copia continua a copiare il solo codice.

L’app riconosce già gli URI `whereweare://person/CODICE` e `whereweare://group/CODICE`, utili ai test e al futuro fallback, ma non li condivide come link universali. L’invito validato viene conservato localmente prima del login e sopravvive alla ricreazione del processo; viene rimosso dopo invio riuscito oppure chiusura esplicita. Ricevere un nuovo invito sostituisce quello pendente; una vecchia callback non può cancellare il nuovo.

Per abilitare in futuro HTTPS:

1. Scegliere e rendere operativo un hosting HTTPS reale, con una base opzionale come `/invite`.
2. Pubblicare `web-invites/index.html` come fallback per i percorsi `person/CODICE` e `group/CODICE` sotto quella base. È un template locale, non un sito già pubblicato; non ha link di installazione inventati.
3. Pubblicare `/.well-known/assetlinks.json` con relazione `delegate_permission/common.handle_all_urls`, namespace `android_app`, package `com.whereweare.app` e SHA-256 del certificato **effettivamente usato per la distribuzione**. Il certificato debug non è quello di una futura release/store.
4. Impostare `INVITE_BASE_URL` alla base HTTPS reale e ricompilare. Query, frammenti, credenziali e porte esplicite non sono supportati.
5. Verificare Android App Links su dispositivo con `adb shell pm verify-app-links --re-verify com.whereweare.app` e `adb shell pm get-app-links com.whereweare.app`. Verificare anche il fallback senza app e i due flussi prima/dopo login.

Riferimenti ufficiali: [associazione del dominio](https://developer.android.com/training/app-links/configure-assetlinks), [verifica App Links](https://developer.android.com/training/app-links/verify-applinks).

## Background e notifiche

La condivisione posizione usa già un foreground service `location`, avviato dal pulsante dell’utente. Non richiede Firebase. La notifica persistente offre STOP; da Android 13 la sua presenza nel cassetto dipende anche dal permesso notifiche. Il permesso viene richiesto quando si avvia la condivisione.

Firebase è invece necessario per i bengala ricevuti con app chiusa. Non è ancora configurato: seguire `SETUP_v0.3.md` per client Firebase, credenziali server e dispatcher. Una build senza Firebase non può ricevere push a processo chiuso. L’animazione in foreground usa gli eventi Supabase e le risorse audio locali.

Android/OEM possono ritardare CPU e rete con schermo spento, Doze e risparmio energetico, soprattutto negli intervalli lunghi. Non è garantita la frequenza al secondo, la ripartenza dopo un arresto OEM o il funzionamento dopo Forza arresto. Nessuna esenzione batteria forzata o workaround introdotto. Riferimenti: [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby), [restrizioni all’avvio dei servizi](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Verifiche riproducibili

- `.\gradlew.bat :app:build` — compilazione, test JVM/Robolectric, lint e APK debug/release non firmata.
- `node supabase/tests/run-v03.mjs --v033` — schema iniziale, regressioni precedenti, 007 ripetuta e sicurezza v0.33. Usa PGlite/pgcrypto in `.tools/pglite/package`.
- `node tools/verify-audio.mjs` — formato, durata, energia non nulla e hash dei tre WAV.
- `deno test --allow-env --allow-net supabase/functions/send-meeting-push/index_test.ts` — rifiuto richieste non autorizzate e configurazione Firebase mancante/invalida.

Per riprodurre il test SQL su un checkout nuovo, predisporre PGlite **0.5.8**, la versione usata nella verifica. Dalla radice del progetto, in PowerShell:

```powershell
New-Item -ItemType Directory -Force .tools/pglite | Out-Null
npm pack @electric-sql/pglite@0.5.8 --pack-destination .tools/pglite
tar -xzf .tools/pglite/electric-sql-pglite-0.5.8.tgz -C .tools/pglite
node supabase/tests/run-v03.mjs --v033
```

Il test del dispatcher passa anche senza `--allow-net`: `deno test --allow-env supabase/functions/send-meeting-push/index_test.ts`. Dopo aver popolato la cache delle dipendenze, `--cached-only` permette di eseguirlo senza scaricamenti. Non usare i runner `live-*` per una verifica offline: creano dati su un servizio remoto.

Per il test dispositivo usare un codice reale nei comandi `adb shell am start -a android.intent.action.VIEW -d "whereweare://group/CODICE" com.whereweare.app` e nell’equivalente `person`. I codici dimostrativi dei test non identificano utenti reali.
