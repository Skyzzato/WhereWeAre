# WhereWeAre

Current development baseline: **v0.49**

versionCode: **18**

stato: **prerelease**

branch canonico: **main**

Regole correnti: [VERSIONING.md](VERSIONING.md). Audit e verifiche del consolidamento: [REPOSITORY_AUDIT_2026-09-18.md](REPOSITORY_AUDIT_2026-09-18.md).

La v0.49 include le correzioni successive al tag v0.48: gate prima dell'onboarding, recupero della condivisione indipendente dalla UI e fix con età monotona. I tag e gli APK storici restano immutati.

Applicazione Android per condividere volontariamente l’ultima posizione con persone e gruppi. Kotlin, Compose Material 3, Hilt, Fused Location Provider, MapLibre e Supabase. Italiano e inglese. Nessuna cronologia GPS.

**Versione v0.49 (versionCode=18), prerelease di collaudo.** Ritrovi di massimo un'ora, cancellazione per destinatari autorizzati con evento durevole, ricentramento dopo 20 secondi e musica originale solo in primo piano. Default GPS di cinque secondi e preferenze conservate. [Note](RELEASE_NOTES.md), [verifiche e stato server](VERIFICATION_v0.49.md).

La cronologia recuperata, la distinzione fra snapshot reali e v0.22 retrospettiva e i controlli di pubblicazione sono in [RELEASE_NOTES.md](RELEASE_NOTES.md) e [REPOSITORY_RECOVERY.md](REPOSITORY_RECOVERY.md).

Deploy del 13/10/2026: migrazioni 024–028 applicate, cleanup ritrovi e dispatcher push ogni minuto, test SQL remoto passato con zero fixture residue. La 028 corregge l'errore del servizio durante le letture: aggiornare i dati o riaprire l'app, senza reinstallare. Dopo download e installazione dell'APK pubblico: **latest 0.49/18 e minimo 18**, riletti dal server; v0.48 bloccata e v0.49 ammessa nell'emulatore. FCM configurato: 22 job completati, 20 accettazioni provider, zero pendenti. [Report](VERIFICATION_v0.49.md). **Visualizzazione push e collaudo prolungato su telefoni reali non eseguiti.**

## Avvio

1. Installa Android Studio, JDK 21, SDK API 37 e Build Tools 36.0.0. Minimo Android 8/API 26; serve Google Play Services per Fused Location e FCM.
2. Copia `local.properties.example` in `local.properties`: configura SDK e credenziali **pubbliche** Supabase. Non inserire service-role o chiavi private nel client.
3. Confronta schema e migrazioni prima di applicarle. La storia live era manuale; 024–028 applicate e verificate. La 027 e la 028 sono interventi server successivi al tag APK. Il reset locale 023 non appartiene al deploy. [Politica](VERSIONING.md) e [report](VERIFICATION_v0.49.md).
4. Compila con Android Studio oppure `./gradlew :app:build` (Windows: `.\gradlew.bat :app:build`).
5. APK: [WhereWeAre-v0.49.apk](https://github.com/Skyzzato/WhereWeAre/releases/download/v0.49/WhereWeAre-v0.49.apk), firma debug precedente verificata. Artefatto `.tools/release-v0.49/WhereWeAre-v0.49.apk`; checksum e provenienza negli allegati della [prerelease](https://github.com/Skyzzato/WhereWeAre/releases/tag/v0.49).

La migrazione 005 aggiunge modifica nome/icona e annullamento inviti; mantiene compatibile la v0.3 e il minimo client a 4. Applicarla prima di usare le nuove azioni della v0.31.

## Funzioni

- Condivisione esplicita ON/OFF, servizio foreground e notifica persistente con STOP.
- Precisione alta predefinita solo se non esiste una scelta salvata; aggiornamenti a 5/30/60 secondi, 5/10/30/60 minuti.
- Follow dell’utente, disattivato dal trascinamento; inquadramento di tutti; OpenFreeMap, OpenTopoMap, CyclOSM; popup e link OpenStreetMap.
- Foto profilo ritagliabile, iniziali Unicode, sei temi, quattro dimensioni avatar, raggruppamento dei marker sovrapposti.
- Codici brevi casuali `H7D-F8C`; i precedenti codici a otto caratteri rimangono validi. Un codice consente di richiedere un collegamento, non di leggere automaticamente il GPS.
- Richieste personali, richieste di adesione approvate dall’amministratore, inviti ai gruppi accettati dal destinatario, gestione membri e nome gruppo.
- Visibilità locale di persone/gruppi indipendente dall’autorizzazione a condividere.
- Punto di ritrovo di massimo un'ora, destinatari deduplicati, cancellazione condivisa autorizzata, banner, linee animate, bengala Canvas e apertura della mappa da notifica.
- Musica lounge originale, disattivabile e sospesa al blocco/background; ricentramento automatico dopo 20 secondi di mappa vuota senza gesti.
- Notifiche push Firebase configurate e accettazione provider verificata; visualizzazione sul telefono da collaudare. Link HTTPS predisposti per il futuro dominio, senza associazione verificata attiva.
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
push FCM configurato con la v0.49, da verificare sui telefoni. Non è una protezione d’emergenza garantita.

## GPS e riservatezza

Gli intervalli lunghi usano acquisizioni singole con timeout di 30 secondi, rilasciando il provider fra le richieste. La frequenza effettiva dipende da fix disponibili, Android e rete: non è garantita al secondo. Ogni fix valido aggiorna subito lo stato locale prima della pubblicazione.

La RLS verifica relazione/gruppo, consenso, sharing attivo, sessione e timeout del proprietario. Un punto di ritrovo non concede ulteriori diritti sul GPS dei partecipanti. Nascondere localmente un gruppo non revoca la condivisione.

La migrazione 002 disattiva il vecchio cleanup fisso a due ore: non reintrodurlo, perché il timeout personale può arrivare a 24 ore. Le autorizzazioni applicano la scadenza anche senza cancellare la riga; non esiste una cronologia GPS automatica. Le istantanee Check-in e SOS hanno visibilità e consenso separati, descritti nelle note v0.4.

## Cartografia

Motore MapLibre Compose 0.16/Native, cache disco 64 MiB, identificazione `WhereWeAre/0.49` derivata da BuildConfig. OpenFreeMap è il provider standard; OpenTopoMap e CyclOSM non richiedono credenziali. Nessun prefetch offline di regioni. Attribuzioni sempre visibili e link alle licenze nelle impostazioni.

## Test riproducibili

- Android/JVM/lint: `.\gradlew.bat :app:build`.
- SQL completo v0.33: `node supabase/tests/run-v03.mjs --v033`, con PGlite 0.5.8 e pgcrypto nella cartella `.tools/pglite/package` (vedi SETUP).
- SQL incrementale: `node supabase/tests/run-v03.mjs --v04`; baseline immutabile: `node tools/verify-v033-migrations.mjs`.
- Edge: `deno test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts`.
- Test su dispositivo e checklist Firebase: [VERIFICATION.md](VERIFICATION.md).

I report delle versioni precedenti restano come documentazione storica. Le configurazioni locali, i segreti e gli artefatti di build non sono versionati.
