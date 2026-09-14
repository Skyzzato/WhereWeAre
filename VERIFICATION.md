# WhereWeAre — verifiche

## v0.33 — verifica per la pubblicazione del repository

14 settembre 2026, Android `versionName=0.33`, `versionCode=8`. Codice applicativo, risorse, migrazioni e dipendenze invariati rispetto al working tree iniziale, verificati con inventario SHA-256 esterno. Le modifiche di questa attività sono documentazione e `.gitignore`; `ROADMAP.md` invariato.

| Controllo | Esito corrente |
| --- | --- |
| Gradle `:app:build --console=plain` | PASS: debug/release, test e lint; 114 task, 18 eseguiti e 96 aggiornati. JDK 21 Android Studio, wrapper Gradle 9.7.1, SDK 37. |
| Build isolata dai file destinati a Git | PASS: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`; 64 task eseguiti, nessun output di build preesistente nella copia. Configurazione locale copiata separatamente e non versionata; dipendenze dalla cache/download Gradle. |
| Test JVM/Robolectric isolati | **39 test, 0 fallimenti, 0 errori** nelle otto suite esistenti. |
| Lint isolato | **0 errori, 49 warning** preesistenti; presenti anche avvisi Kotlin/API FCM deprecate. Non corretti incidentalmente. |
| SQL `node supabase/tests/run-v03.mjs --v033` | PASS su PGlite 0.5.8 temporaneo: migrazioni 001–007, 007 ripetuta, suite iniziali, storage, v0.3, v0.31, v0.33 e regressione v0.3 finale. |
| Dispatcher push | `deno check --cached-only` PASS; test con `--cached-only --allow-env`, senza permesso rete: **1 passato**. Avviso transitivo `punycode`. |
| Risorse/audio | 251 stringhe EN e 251 IT, stessi identificativi; compilazione risorse riuscita. Tre WAV validati da `tools/verify-audio.mjs`. |
| Manifest/dipendenze | Manifest debug/release uniti e compilati; servizi applicativi non esportati, foreground type location, backup e cleartext disabilitati. Dipendenze risolte, controllo duplicati e metadati AAR passato; nessun aggiornamento introdotto. |
| Versione visibile | Splash, Informazioni e User-Agent usano BuildConfig; APK verificato con aapt: package `com.whereweare.app`, 0.33/8, min SDK 26. Nessun nome della tecnologia backend nelle stringhe IT/EN. |
| Firma/identità APK locale | `apksigner verify` PASS. L'APK debug del progetto ha lo stesso SHA-256 dell'APK presente all'inizio: `748DD9684991CA09F34130924BC5AD5E8CE3F1675CD9CC0C9FECF2F4BEFFC350`. La prova isolata certifica la build da sorgente, senza promessa di identità binaria fra directory diverse. |
| Segreti e cronologia | Controllati 201 blob storici distinti selezionati e file correnti; nessuna chiave privata, JWT o token nei formati cercati. Configurazioni locali, APK e chiavi esclusi dall'indice. Alberi dei quattro commit recuperati identici agli originali. |
| Whitespace | `git diff --check` e controllo dell'indice superati. |

Log locali esclusi da Git: `.tools/repository-v033-build.log`, `.tools/repository-v033-repro.log`, `.tools/repository-v033-sql.log`. Report del collaudo isolato in `.tools/repro-v033/app/build/reports`.

Nessun dispositivo ADB o AVD disponibile. Non eseguiti test fisici di background/schermo spento, fotocamera, audio o scambio fra telefoni. Nessun test live e nessuna modifica al database remoto. Restano configurazione Firebase, eventuale distribuzione App Links e verifica della 007 sul server, come descritto in `SETUP_v0.33.md`. La 006 conserva l'incongruenza storica del bootstrap (code 6 per 0.32); la 007 annuncia correttamente 0.33/8.

Le sezioni sottostanti sono report storici conservati, non risultati rieseguiti in questa attività.

## v0.32 — build 7

Build completa riuscita (`:app:build`): debug/release, test JVM e lint. 31 test superati, 0 errori/fallimenti; lint 0 errori. Suite SQL `node supabase/tests/run-v03.mjs --v032` superata con migrazione 006 e regressione precedente. APK debug: `app/build/outputs/apk/debug/WhereWeAre-v0.32-build7-debug.apk`, SHA-256 `31644E507D034A3F77BE9578766A3B452D7DF4272740A5B06EBBFB6F86A8F10E`.

Realtime: il messaggio precedente nasceva dal riuso di `offline` per `!SUBSCRIBED`, anche dopo REST riuscito. Ora `offline` indica solo fallimenti REST; CONNECTING/RECONNECTING non mostra errore, mentre errori persistenti impostano `syncFailed`. Gli status e gli errori vengono registrati senza dati sensibili. Il bootstrap remoto verificato prima della 006 annuncia ancora 0.31: applicare la migrazione 006 per 0.32.

Deep link: formato tipizzato `whereweare://person/<codice>` e `whereweare://group/<codice>`; HTTPS predisposto come `/join/person/` e `/join/group/`. Persona apre Aggiungi persona precompilato, gruppo Entra con codice; l'azione resta manuale. Il dominio e `assetlinks.json` non esistono nel repository, quindi gli App Links HTTPS verificati richiedono pubblicazione esterna.

Stato GPS: la UI distingue processo locale attivo, sessione remota attiva e sessione remota da interrompere; non combina più OFF con Interrompi. La riconciliazione usa lo stop persistente e WorkManager già presenti.

Gruppi: Modifica gruppo è stata spostata nella testata, accanto a nome/icona e data di creazione; la card grigia resta dedicata al codice.

Limiti: nessun telefono/AVD collegato; audio reale, fotocamera, background e test con due account fisici restano da eseguire. Firebase e la migrazione 006 remota richiedono configurazione/applicazione esterna.

## v0.31 — build 6

`versionName=0.31`, `versionCode=6`, branch `codex/v0.31`.

- `:app:build`: riuscito, compilazione debug e release, test JVM e lint.
- **31 test superati**, 0 fallimenti/errori: 25 regressioni esistenti più 4 test bozza/avatar e 2 test bengala.
- Lint debug: **0 errori, 33 warning** (risorse non usate, suggerimenti KTX/aggiornamento dipendenza e scrittura sincrona delle preferenze). Il riferimento alla bozza viene persistito prima del passaggio alla fotocamera.
- `node supabase/tests/run-v03.mjs --v031`: riuscito. Migrazioni 001–005; suite precedenti, 4 utenti/ruoli v0.31 e suite v0.3 rieseguita dopo la 005.
- `git diff --check`: nessun errore di whitespace.
- `adb devices` e lista AVD: nessun dispositivo/emulatore disponibile. Nessuna prova completa su due telefoni, fotocamera esterna o Firebase eseguita.

APK installabile: `app/build/outputs/apk/debug/WhereWeAre-v0.31-build6-debug.apk`. Firma v2 verificata, stesso certificato debug della build 5 (`acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6`); package/versione verificati tramite aapt. SHA-256 APK: `6E91AC576C7EA8B792F47308676F13298BEBBDA5F757EC5006C114AE176DF888`. Log: `.tools/v031-build.log`, `.tools/v031-sql.log`. Migrazione remota 005 ancora da applicare: `SETUP_v0.31.md`. Report completo: `RELEASE_NOTES_v0.31.md`.

Le sezioni successive sono lo storico delle versioni precedenti.

## Correzione crash di avvio — build 5

La build 4 aveva un errore runtime non coperto dai test di dominio: il contesto localizzato dell’Application veniva passato alla factory Hilt, che richiede un’Activity. L’esame della dipendenza Hilt 1.4.0 conferma l’eccezione `Expected an activity context for creating a HiltViewModelFactory`.

La build 5 mantiene l’Activity nella catena ContextWrapper e usa un contesto applicativo separato per Strings. Test dedicati in `LocalizedActivityTest`: riproduzione della precedente IllegalStateException e conservazione di Activity/risorse italiano e inglese. I test usano [Robolectric](https://robolectric.org/getting-started/) sul PC, senza dispositivo.

Esito build 5: `assembleDebug`, `testDebugUnitTest` e `lintDebug` riusciti; **25 test superati**, inclusi entrambi i test Android di regressione. Lint: 0 errori, 24 warning. Firma v2 verificata, stesso certificato debug della build precedente, package `com.whereweare.app`, versione `0.3`, codice `5`. SHA-256 APK: `ED54316BDCB6B5C4399E757196858F74553B73B41639B03AF48D63886FFD1A1A`. Nessuna verifica su telefono fisico: il difetto precedente è riprodotto nel test locale, non tramite log del dispositivo dell’utente.

Verifica backend successiva alla segnalazione: la RPC pubblica remota `app_bootstrap` restituisce `api_version=3`, versione 0.3 e minimo 4; la suite SQL locale completa passa. Non è necessario rieseguire la migrazione 004 per installare la build 5.

L’APK corretto è `app/build/outputs/apk/debug/WhereWeAre-v0.3-build5-debug.apk`. I log aggiornati sono in `.tools/v03-hotfix-build.log`. Le verifiche e l’hash riportati sotto sono lo storico della **build 4**, precedente alla correzione e da non reinstallare.

## Rapporto storico — build 4

Data: 12 settembre 2026. Branch locale `codex/v0.3`. Versione APK `0.3`, versionCode `4`, package `com.whereweare.app`.

## Risultati automatici

| Verifica | Esito |
| --- | --- |
| Gradle `:app:build` | Build debug e release, test JVM e lint |
| JVM | **23 test, 0 fallimenti, 0 errori**: DomainTest 6, V02Test 6, V021Test 5, V03Test 6 |
| SQL/PGlite 0.5.8 + pgcrypto | **PASS**: tutte le migrazioni e le suite security, security_v02, storage_guard, security_v03 |
| Edge Function | `deno check` superato, Deno 2.9.6 / TypeScript 6.0.3 |
| Test dispatcher | **1 test superato**: GET, POST non autenticato, secret errato →401; Firebase assente/invalido →503, nessun segreto nell’errore; nessuna rete richiesta |
| Localizzazione | 225 stringhe inglesi e 225 italiane; stessi identificativi, risorse compilate |
| Lint | **0 errori, 23 warning**: risorse precedenti inutilizzate e suggerimenti KTX |
| Firma APK | `apksigner verify`: firma **v2 valida**, certificato Android Debug |
| Metadati APK | `aapt dump badging`: versione 0.3/4, min SDK26, target SDK37 |
| Whitespace Git | `git diff --check` senza errori; soli avvisi di conversione LF/CRLF del sistema |

La compilazione segnala anche API FCM deprecate, ancora disponibili nell’SDK fissato. Il controllo Deno segnala la deprecazione `punycode` in una dipendenza transitiva. Non sono errori bloccanti.

## Copertura aggiunta

JVM: codici 3-3 e precedenti 4-4, rifiuto caratteri invalidi, iniziale Angelo→A e Unicode, fallback sistema tedesco→inglese, override, soglia 600+180 secondi e confine, backend precedente bloccato, raggruppamento marker a diverse scale, UI immediata con successo, rollback, conservazione foto e isolamento account.

SQL: creazione codice breve, normalizzazione/case e codici precedenti; collisione gruppo forzata con retry; limite lookup che persiste anche sui tentativi inesistenti; richiesta di adesione senza accesso GPS prima del consenso; admin/invitato corretti, rifiuto e accettazione; inviti e uscita; disabilitazione condivisione gruppo; meeting con destinatari deduplicati, idempotenza, ownership e isolamento; nessun nuovo diritto GPS; link con anteprima e conferma; impossibilità di enumerare token o leggere analytics; privilegi esclusivi push server; cascata account; metadati visibili soltanto ai membri autorizzati; conteggio aggregato aggiornamenti.

Il database SQL è temporaneo e locale. **La migrazione v0.3 non è stata applicata al Supabase remoto.** Le prove live citate nei vecchi report appartengono alla v0.21, non alla v0.3.

## Artefatto

`app/build/outputs/apk/debug/WhereWeAre-v0.3-debug.apk`

- 67.849.880 byte (circa 64,7 MiB).
- SHA-256: `A27CED10F670736EB5D361207F97EDE7E9BEC74D1340A28F0BF301676CACA320`.
- Certificato debug SHA-256: `acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6`.
- Release compilata in `app/build/outputs/apk/release`; la distribuzione richiede la propria firma.

Log locali: `.tools/v03-build.log`, `.tools/v03-sql.log`, `.tools/v03-edge-final.log`, `.tools/v03-edge-test.log`, `.tools/v03-apk-signature.log`. Report JVM/lint in `app/build/reports`.

## Ambiente e limite del collaudo

Windows, JDK21 Android Studio, SDK37, Gradle Wrapper del progetto. Windows assegnava l’attributo ReadOnly ad alcune directory generate da Hilt; durante il collaudo l’attributo è stato rimosso esclusivamente dentro `app/build`, senza modificare sorgenti o permessi di sistema.

`adb devices -l` non mostra dispositivi e `emulator -list-avds` non mostra AVD. Non è quindi stata eseguita una verifica visiva/strumentata Android. Firebase non esiste ancora e non sono stati inviati messaggi reali. I test automatici non certificano rendering, gesture, batteria o puntualità GPS/FCM.

## Checklist manuale prima della distribuzione

1. Applicare migration 004 in staging e usare almeno tre account reali con i consensi indicati nelle suite SQL; controllare Realtime e storage.
2. Verificare foto invariata mentre si consente/revoca la visibilità; iniziale Angelo→A; sei temi, scala75–150%, cluster e hitbox; mappa sotto ogni banner.
3. Muovere la posizione: follow ricentra anche fuori viewport, drag lo disattiva, pulsante lo riattiva. Verificare passaggio fra provider.
4. Impostare 10 minuti, provare foreground/background e blocco schermo per almeno tre cicli; confrontare i timestamp con un tempo affidabile, senza conservare coordinate nei log. Provare rete assente, app riaperta, stop e cambio account.
5. Provare richieste gruppo, admin, rifiuto, inviti destinatario, rimozione e gruppo inesistente; distinguere occhio locale da autorizzazione server.
6. Creare meeting per persone/gruppi sovrapposti: un destinatario una volta; linee solo per GPS già autorizzato, bengala una volta, rimozione solo creatore, apertura da banner.
7. Configurare Firebase e scheduler; verificare notifica in background, permesso negato, deep link, duplicati, logout e cambio account. Verificare che il contenuto dell’account precedente non resti visibile. FCM non consegna dopo force-stop finché l’app non viene riaperta.
8. Provare sistema italiano/inglese/tedesco e override; controllare notifiche GPS e meeting anche dopo riavvio processo.
9. Simulare errori HTTP durante modifiche: UI aggiornata subito, rollback e messaggio coerenti, nessuna foto persa.
10. Verificare eliminazione account con avatar e meeting, cleanup server e revoca immediata delle letture.

I passaggi di configurazione sono in [SETUP_v0.3.md](SETUP_v0.3.md); il riepilogo richiesto in 18 punti è in [RELEASE_NOTES_v0.3.md](RELEASE_NOTES_v0.3.md).
