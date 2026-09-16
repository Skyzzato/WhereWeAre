# WhereWeAre

Applicazione Android per condividere volontariamente l’ultima posizione con persone e gruppi. Kotlin, Compose Material 3, Hilt, Fused Location Provider, MapLibre e Supabase. Italiano e inglese. Nessuna cronologia GPS.

**Versione v0.47 (versionCode=16), prerelease di collaudo.** Recupero alla riapertura, Cronologia eventi, nome WhereWeAre, icone verdi e nuova scala avatar. Vedi [note v0.47](RELEASE_NOTES_v0.47.md), [verifiche](VERIFICATION_v0.47.md) e [configurazione server vigente](SETUP_v0.46.md).

La cronologia recuperata, la distinzione fra snapshot reali e v0.22 retrospettiva e i controlli di pubblicazione sono in [RELEASE_NOTES.md](RELEASE_NOTES.md) e [REPOSITORY_RECOVERY.md](REPOSITORY_RECOVERY.md).

Il server verificato il 16/09/2026 restituisce bootstrap **0.46/15**, quota SOS disattivata e migrazione 021 applicata. Test SQL transazionale remoto passato con zero fixture residue. **FCM non ancora configurato: nessuna consegna push reale verificata.** Dettagli: [SETUP_v0.46.md](SETUP_v0.46.md).

## Avvio

1. Installa Android Studio, JDK 21, SDK API 37 e Build Tools 36.0.0. Minimo Android 8/API 26; serve Google Play Services per Fused Location e FCM.
2. Copia `local.properties.example` in `local.properties`: configura SDK e credenziali **pubbliche** Supabase. Non inserire service-role o chiavi private nel client.
3. Applica le migrazioni 001–021 in ordine, verificando prima quelle già presenti. Il progetto configurato ha già ricevuto la 021: [SETUP v0.46](SETUP_v0.46.md).
4. Compila con Android Studio oppure `./gradlew :app:build` (Windows: `.\gradlew.bat :app:build`).
5. APK installabile: [WhereWeAre-v0.47.apk](https://github.com/Skyzzato/WhereWeAre/releases/download/v0.47/WhereWeAre-v0.47.apk), firma debug precedente. Artefatto `.tools/release-v0.47/WhereWeAre-v0.47.apk`; checksum e provenienza negli allegati della prerelease.

La migrazione 005 aggiunge modifica nome/icona e annullamento inviti; mantiene compatibile la v0.3 e il minimo client a 4. Applicarla prima di usare le nuove azioni della v0.31.

## Funzioni

- Condivisione esplicita ON/OFF, servizio foreground e notifica persistente con STOP.
- Precisione alta predefinita solo se non esiste una scelta salvata; aggiornamenti a 5/30/60 secondi, 5/10/30/60 minuti.
- Follow dell’utente, disattivato dal trascinamento; inquadramento di tutti; OpenFreeMap, OpenTopoMap, CyclOSM; popup e link OpenStreetMap.
- Foto profilo ritagliabile, iniziali Unicode, sei temi, quattro dimensioni avatar, raggruppamento dei marker sovrapposti.
- Codici brevi casuali `H7D-F8C`; i precedenti codici a otto caratteri rimangono validi. Un codice consente di richiedere un collegamento, non di leggere automaticamente il GPS.
- Richieste personali, richieste di adesione approvate dall’amministratore, inviti ai gruppi accettati dal destinatario, gestione membri e nome gruppo.
- Visibilità locale di persone/gruppi indipendente dall’autorizzazione a condividere.
- Punto di ritrovo persistente, destinatari deduplicati, banner, linee animate, bengala Canvas e apertura della mappa da notifica.
- Notifiche push predisposte con Firebase, da configurare; link HTTPS predisposti per il futuro dominio, senza associazione verificata attiva.
- Eliminazione account tramite Edge Function esistente, logout, pulizia immagini e cache; la condivisione continua conserva l’ultima posizione; Check-in e SOS sono eventi volontari separati.

## Funzioni incrementali v0.4

Diagnostica e recupero condivisione, dettagli dispositivo, QR Persona/Gruppo,
precisione condivisa applicata sul server, chi può vedermi, richieste posizione,
Check-in, gruppi temporanei, convergenza Bengala, luoghi/regole consensuali,
SOS ai destinatari selezionati, onboarding e Impostazioni riorganizzate.
Le funzioni server vengono mostrate solo quando la relativa migrazione è disponibile.

Il routing Valhalla è configurabile e disabilitato senza endpoint; “Sta arrivando”
resta sperimentale disattivato. Mancato arrivo bloccato dallo scheduler non verificato;
SOS verso volontari vicini aderenti disponibile dalla v0.42 tramite inbox nell'app;
push FCM ancora da configurare. Non è una protezione d’emergenza garantita.

## GPS e riservatezza

Gli intervalli lunghi usano acquisizioni singole con timeout di 30 secondi, rilasciando il provider fra le richieste. La frequenza effettiva dipende da fix disponibili, Android e rete: non è garantita al secondo. Ogni fix valido aggiorna subito lo stato locale prima della pubblicazione.

La RLS verifica relazione/gruppo, consenso, sharing attivo, sessione e timeout del proprietario. Un punto di ritrovo non concede ulteriori diritti sul GPS dei partecipanti. Nascondere localmente un gruppo non revoca la condivisione.

La migrazione 002 disattiva il vecchio cleanup fisso a due ore: non reintrodurlo, perché il timeout personale può arrivare a 24 ore. Le autorizzazioni applicano la scadenza anche senza cancellare la riga; non esiste una cronologia GPS automatica. Le istantanee Check-in e SOS hanno visibilità e consenso separati, descritti nelle note v0.4.

## Cartografia

Motore MapLibre Compose 0.16/Native, cache disco 64 MiB, identificazione `WhereWeAre/0.47` derivata da BuildConfig. OpenFreeMap è il provider standard; OpenTopoMap e CyclOSM non richiedono credenziali. Nessun prefetch offline di regioni. Attribuzioni sempre visibili e link alle licenze nelle impostazioni.

## Test riproducibili

- Android/JVM/lint: `.\gradlew.bat :app:build`.
- SQL completo v0.33: `node supabase/tests/run-v03.mjs --v033`, con PGlite 0.5.8 e pgcrypto nella cartella `.tools/pglite/package` (vedi SETUP).
- SQL incrementale: `node supabase/tests/run-v03.mjs --v04`; baseline immutabile: `node tools/verify-v033-migrations.mjs`.
- Edge: `deno test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts`.
- Test su dispositivo e checklist Firebase: [VERIFICATION.md](VERIFICATION.md).

I report delle versioni precedenti restano come documentazione storica. Le configurazioni locali, i segreti e gli artefatti di build non sono versionati.
