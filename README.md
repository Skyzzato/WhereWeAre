# WhereWeAre v0.31

Applicazione Android per condividere volontariamente l’ultima posizione con persone e gruppi. Kotlin, Compose Material 3, Hilt, Fused Location Provider, MapLibre e Supabase. Italiano e inglese. Nessuna cronologia GPS.

Versione `0.31`, `versionCode=6`, sviluppata sulla v0.3 build 5, conservando la correzione del crash di avvio. Vedi [rapporto v0.31](RELEASE_NOTES_v0.31.md), [aggiornamento server](SETUP_v0.31.md), [configurazione Firebase](SETUP_v0.3.md) e [verifiche](VERIFICATION.md).

## Avvio

1. Installa Android Studio, JDK 21, SDK API 37 e Build Tools 36.0.0. Minimo Android 8/API 26; serve Google Play Services per Fused Location e FCM.
2. Copia `local.properties.example` in `local.properties`: configura SDK e credenziali **pubbliche** Supabase. Non inserire service-role o chiavi private nel client.
3. Applica le migrazioni Supabase in ordine: `001_initial_schema.sql`, `002_v0_2.sql`, `003_storage_upload_guard.sql`, `004_v0_3.sql`, `005_v0_31.sql`. Su un progetto v0.3 già aggiornato applica soltanto la 005.
4. Compila con Android Studio oppure `./gradlew :app:build` (Windows: `.\gradlew.bat :app:build`).
5. Installa `app/build/outputs/apk/debug/WhereWeAre-v0.31-build6-debug.apk` se presente, oppure `app-debug.apk`. È una build con firma debug. La release per distribuzione richiede la propria chiave di firma.

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
- Eliminazione account tramite Edge Function esistente, logout, pulizia immagini e cache; solo l’ultima posizione viene conservata.

## GPS e riservatezza

Gli intervalli lunghi usano acquisizioni singole con timeout di 30 secondi, rilasciando il provider fra le richieste. La frequenza effettiva dipende da fix disponibili, Android e rete: non è garantita al secondo. Ogni fix valido aggiorna subito lo stato locale prima della pubblicazione.

La RLS verifica relazione/gruppo, consenso, sharing attivo, sessione e timeout del proprietario. Un punto di ritrovo non concede ulteriori diritti sul GPS dei partecipanti. Nascondere localmente un gruppo non revoca la condivisione.

La migrazione 002 disattiva il vecchio cleanup fisso a due ore: non reintrodurlo, perché il timeout personale può arrivare a 24 ore. Le autorizzazioni applicano la scadenza anche senza cancellare la riga; non esiste uno storico.

## Cartografia

Motore MapLibre Compose 0.16/Native, cache disco 64 MiB, identificazione `WhereWeAre/0.3`. OpenFreeMap è il provider standard; OpenTopoMap e CyclOSM non richiedono credenziali. Nessun prefetch offline di regioni. Attribuzioni sempre visibili e link alle licenze nelle impostazioni.

## Test riproducibili

- Android/JVM/lint: `.\gradlew.bat :app:build`.
- SQL: `node supabase/tests/run-v03.mjs`, con PGlite e pgcrypto nella cartella `.tools/pglite/package` (vedi SETUP).
- Edge: `deno check supabase/functions/send-meeting-push/index.ts`.
- Test su dispositivo e checklist Firebase: [VERIFICATION.md](VERIFICATION.md).

I report delle versioni precedenti restano come documentazione storica. Le configurazioni locali, i segreti e gli artefatti di build non sono versionati.
