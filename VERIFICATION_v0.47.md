# Verifiche v0.47

## Baseline e ambito

Ramo previsto mantenuto: codex/v0.45. Baseline 28a24816bccf900c27a31e311f84982ccc79f38d, working tree iniziale pulita, fetch origin e tag eseguito; remoto coincidente. Nessun reset o riscrittura della cronologia. Nessun AGENTS.md trovato nel repository o nella gerarchia di lavoro.

Versione precedente effettiva: 0.46/15, confermata dal file BUILD_PROVENANCE.json della pre-release pubblicata: SHA-256 3ce20b95b5462927f2f43a57ac594096e96d82abe920f89fba2aafd9ef5aa671, uguale al digest dell'asset GitHub. Nuova versione: 0.47/16. Prima della pubblicazione: tag v0.47 assente, release API HTTP 404.

## Causa verificabile e limite della diagnosi

La stringa segnalata corrisponde a connection_service, usata anche per eccezioni generiche del controller. Non sono disponibili log del telefono e non è stata riprodotta una riapertura autenticata sul server reale: non attribuiamo con certezza quella specifica segnalazione a un unico errore HTTP o SQL.

Sono stati individuati difetti concreti nel percorso:

1. ownSharingStatus leggeva direttamente sharing_status, senza attesa interna dell'autenticazione né rinnovo su 401. Il controller di ripresa non disponeva del recupero implementato dal caricamento generale.
2. resumeFromVisibleActivity salvava failure dopo una lettura fallita e non la cancellava in caso di successiva lettura riuscita senza avvio del servizio. Mancavano serializzazione e controllo di generazione dopo la lettura: un errore tardivo poteva riscrivere lo stato dopo stop(). Questi due difetti sono riprodotti dai test del controller.
3. I caricamenti di Snapshot verificavano la generazione per i risultati riusciti, ma non per gli errori; anche la diagnostica poteva essere sovrascritta da un errore di una richiesta più vecchia.
4. Il rinnovo sessione esponeva l'errore prima di tentare il recupero. Polling, retry realtime e servizio potevano continuare a tentare senza un limite di errori consecutivi.

Correzione: lettura autenticata con un rinnovo massimo, due retry transitori e deadline 30 secondi; stato neutro durante l'operazione; reset dell'errore al successo; ripresa serializzata e invalidazione delle risposte obsolete. Quattro fallimenti consecutivi fermano i tentativi autonomi dei cicli di sincronizzazione/servizio; ripresa, refresh manuale o transizione offline→online permettono un nuovo ciclo. Il canale realtime fallito viene rimosso prima dell'attesa, con timeout di cleanup. Restano i normali aggiornamenti periodici di una sessione sana.

Le categorie distinguono rete/timeout, sessione, accesso negato, configurazione/schema, risposta non valida, posizione disabilitata, permessi e guasto server. I log non stampano eccezioni complete, token, identità o coordinate: operazione, correlazione, durata e categoria.

## Flussi controllati

| Flusso | Comportamento | Evidenza e limiti |
|---|---|---|
| Avvio a freddo | Bootstrap e sessione prima della UI autenticata; lettura condivisione attende la sessione, con deadline | Test sessionRead con ripristino sospeso; nessuna installazione fisica |
| Ritorno dal background | Ripresa Activity e refresh; una verifica condivisione per volta; recupero 401 limitato | Test concorrente, rinnovo e cancellazione; lifecycle Android reale non provato |
| Ricreazione del processo | TrackingSession persistita riutilizzata; nessun nuovo ON dopo OFF o stop remoto | Test sticky recreation, revisione persistita, stop remoto e preferenza OFF |
| Offline e riconnessione | Avviso rete, nessun falso ON; transizione online aggiorna Snapshot e verifica visibile; successo elimina failure | Test IOException/recupero, backoff e stato controller; nessuna rete mobile reale |

## Verifiche automatiche e statiche

- Android: `.\gradlew.bat -I tools/isolated-v044-build.gradle :app:build --console=plain`: PASS. **144 test JVM/Compose, zero fallimenti/errori/skipped; lint zero errori e 69 warning** nel report XML finale. Report analitico BUILD_REPORT.json allegato. Primo tentativo in sandbox bloccato dall’accesso a SDK/licenze; build eseguita con il profilo configurato, senza installare SDK o accettare nuove licenze.
- Test mirati: ripristino sessione, rinnovo singolo 401, 401 permanente, massimo retry e deadline, cancellazione lifecycle, categorie permessi/sessione, successo dopo errore, ripresa concorrente, errore tardivo dopo stop, OFF persistente, diagnostica non sovrascritta, round-trip preferenze avatar.
- Regole UI: verde semantico comune al pulsante e alle icone, contrasto almeno 3:1 sul contenitore FAB di tutti i sei temi. Stato disabilitato del pulsante continua a usare i colori disabled Material; le tre FAB non avevano uno stato disabled e conservano le condizioni di disponibilità.
- Rendering Robolectric nativo: app/build/reports/v047-ui/default.png e dark.png, ispezionati visivamente; nome, cronologia, icone, messaggio neutro, quattro dimensioni e stelline senza tagli. Il test mostra componenti reali, non una sessione MapLibre con server.
- Mappa: nessuna modifica a camera, chiavi di remember, apertura popup o ancoraggio placedAt. Avatar e distanza di raggruppamento usano la nuova scala; area minima di tocco conservata a 48 dp. Assenza di sfarfallio su GPU/dispositivo non verificata.
- Cronologia: test Compose esistente del bidone SOS con conferma separata, deduplicazione/ordine e regressioni SQL superati. Nessuna modifica allo schema, alla retention o all'autorizzazione di cancellazione.
- Ricerca statica: nessun nome esteso WhereWeAre + Troviamoci né vecchia etichetta della cronologia nei sorgenti attivi e web-invites; app_name governa etichetta e notifiche. Documentazione storica conservata.
- node supabase/tests/run-v046.mjs: PASS, dodici suite sulle migrazioni 001–021, inclusi condivisione, precisione, richieste posizione, eventi, SOS, gruppi, luoghi e Bengala. Database locale PGlite, non server remoto.

## Artefatto e distribuzione

APK debug installabile con applicationId com.whereweare.app e certificato storico SHA-256 acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6. Metadati, apksigner, hash e provenienza verificati prima della pubblicazione. L'APK finale viene rigenerato dopo il commit; tag v0.47 e provenienza devono identificare quel medesimo commit. Nessuna chiave o configurazione locale versionata.

## Limiti espliciti

ADB: nessun dispositivo collegato; nessun emulatore avviato. Nessun test end-to-end fra utenti reali, nessun SOS reale e nessuna nuova prova SQL/Auth/PostgREST sul server remoto in questa versione. FCM resta incompleto come già documentato. Non serve alcuna migrazione server per queste correzioni; le policy rimangono inalterate. Bootstrap remoto v0.46/15 compatibile, non promosso con questa pre-release.
