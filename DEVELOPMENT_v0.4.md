# WhereWeAre — Troviamoci.

## Blocco 01 — audit della baseline

Base: `origin/main` aggiornato con fetch il 14 settembre 2026,
`26fe0bc` (aggiunge esclusivamente i criteri JVM Gradle alla v0.33 `fda3dd4`).
Branch di sviluppo: `codex/v0.4`. Nessuna riscrittura della storia.

Confermati direttamente in sorgenti e manifest: versione 0.33, codice 8,
minSdk 26, compileSdk/targetSdk 37, Compose, Hilt, DataStore, WorkManager,
Fused Location, MapLibre, cartografie OSM, PostgreSQL, REST/realtime e FCM.
I link custom person/group/meeting sono registrati; HTTPS rimane subordinato
alla configurazione esterna. Portrait e lingue IT/EN presenti.

LocationForegroundService è foreground di tipo location, START_STICKY e
stopWithTask=false. SharingController conserva sessione e revisione in DataStore;
StopSharingWorker ritenta lo stop. ACCESS_BACKGROUND_LOCATION non è richiesto.
Gli stili Bengala sono 50, verificati anche da V033Test.

Differenza dalla descrizione attesa: la baseline conserva soltanto l'ultima
posizione, non una cronologia GPS. Non introdurre né promettere uno storico
implicitamente. La predisposizione FCM non prova che Firebase e dispatcher
remoti siano configurati.

Migrazioni 001–007 immutabili: nuove modifiche solo da 008. Il manifest degli
hash Git è in `supabase/tests/baseline-v033.json`; il controllo locale
`node tools/verify-v033-migrations.mjs` ne verifica l'integrità.

Verifiche baseline:

- `gradlew.bat :app:build`: BUILD SUCCESSFUL; molte task già aggiornate,
  quindi non equivale a una nuova compilazione integrale. Log locale:
  `.tools/v04-baseline-build.log`.
- `node supabase/tests/run-v03.mjs --v033`: PASS, incluse regressioni,
  RLS e riapplicazione 007 su database locale PGlite. Nessuna migrazione remota.
- `adb devices -l`: nessun dispositivo; `emulator -list-avds`: nessun AVD.
- Nessuna occorrenza del nome della tecnologia backend nelle stringhe UI.

## Progressione

Versione e bootstrap rimangono 0.33/8 fino alla verifica della release completa.
Blocchi 01–03 completati nelle verifiche automatiche; blocchi 04–18 ancora da completare. Questo documento è
un registro di sviluppo, non una dichiarazione di disponibilità della v0.4.

Rischi da risolvere nel Blocco 02: la riconciliazione avviene nella Mappa e può
ordinare uno stop prima del recovery; lo stato locale diventa ON prima dell'ACK;
lo stop remoto viene osservato dal publisher soltanto al successivo fix; occorre
verificare la cancellazione fra vecchio servizio e nuova sessione. Non costruire
diagnostica o funzioni di sicurezza su stati ambigui.

## Blocco 02 — affidabilità della condivisione

- Eliminato lo stop implicito dalla Mappa. Il recovery da Activity visibile è
  indipendente dalla tab e riusa esclusivamente sessione/revisione persistite.
- Stato ON soltanto dopo conferma dell'avvio; avvio annullabile e notifica
  coerente. I timeout RPC lasciano il servizio in attesa di riconnessione.
- Controllo dello stato/sessione server ogni 30 secondi circa anche senza fix;
  sessione sostituita o stop remoto terminano il vecchio servizio.
- La cancellazione del vecchio servizio non richiama lo stop della nuova
  istanza e conserva la sessione per START_STICKY.
- Stop automatici persistiti con sessione: non possono fermare una sessione
  successiva di un altro dispositivo. I vecchi stop user-only e lo stop
  esplicito dell'utente restano compatibili. DataStore aggiunge soltanto una
  chiave opzionale; nessuna migrazione distruttiva.
- Separati assenza Internet, errore REST, realtime indisponibile, fix locale
  vecchio, fix remoto vecchio e pubblicazione pendente. Una conferma relativa
  al fix precedente non sovrascrive un fix locale più recente.
- Intervalli FLP, 50 stili Bengala, permessi e migrazioni 001–007 preservati.

Test SQL aggiunti in `supabase/tests/sharing_recovery.sql`, eseguibili con
`node supabase/tests/run-v03.mjs --v033 --sharing-recovery`: PASS.
I test Android del controller sono in `SharingRecoveryTest.kt` (rete, ACK,
ricreazione, sessione sostituita, stop persistiti e avvio foreground rifiutato).
Verifica finale del 15 settembre 2026: `gradlew.bat :app:build` riuscito,
debug e release, **45 test superati**, zero fallimenti/errori, lint senza errori.
Log locale: `.tools/v04-block02-final.log`. Le prove su dispositivo sono ancora
da eseguire con la checklist; non equivalgono ai test Robolectric SDK34.

Nuove dipendenze esclusivamente di test: Mockito core 5.23.0 e
kotlinx-coroutines-test 1.11.0. Nessun nuovo permesso Android, provider esterno,
feature flag o migrazione SQL. Decisioni Android e prove manuali:
`VERIFICATION_v0.4_BACKGROUND.md`.

## Dipendenze esterne dei blocchi successivi

Il repository contiene un dispatcher meeting che richiede uno scheduler esterno
e configurazione Firebase. `SETUP_v0.3.md` descrive come configurarli, ma non
dimostra che siano operativi. Non dichiarare Check-in/SOS/notifiche di scadenza
collaudati end-to-end senza verificarli. Per il Blocco 14 occorre un deadline
server affidabile indipendente dal telefono; lo scheduler non è verificato.
Non è configurato un provider di routing: la scelta tecnica e l'hosting del
Blocco 11 restano da affrontare. Questi limiti non certificano né completano
alcuno dei blocchi 03–18.

## Blocco 03 — diagnostica

Due viste accessibili dalle Impostazioni, chiudibili con X e Indietro. Diagnostica
posizione: fix/età/precisione, coordinate decimali, altitudine, velocità, direzione,
provider, satelliti, stato reale foreground/condivisione/recovery, invii/conferme
e ottimizzazione batteria. Il listener GNSS è passivo, esiste soltanto mentre la
vista è osservata e non avvia acquisizioni; i campioni satellitari scadono dopo
10 secondi. Altitudine e altri campi assenti restano Non disponibile.

Diagnostica connessione: rete validata/tipo, ultima raggiungibilità e sessione
verificata, realtime, letture/scritture confermate, latenza, richieste in corso,
pubblicazione e stop pendenti. La telemetria osserva le richieste già presenti
nel SharingRepository, non introduce polling. Il pulsante ESEGUI TEST legge
lo stato proprio dal server con timeout di 12 secondi, senza inviare coordinate.
Un HTTP 401 dimostra raggiungibilità ma segnala sessione rifiutata. Nessun errore
grezzo, token, URL o nome backend è passato alla UI. I tempi riguardano questo
processo e sono esplicitamente ultime osservazioni, non disponibilità garantita.
Cambio account azzera la telemetria e ignora risposte tardive del precedente.

Verifica del 15 settembre 2026: `gradlew.bat :app:build` PASS (debug/release,
**52 test**, zero fallimenti/errori; lint senza errori). Log:
`.tools/v04-block03-build.log`. Parità risorse IT/EN e hash migrazioni verificati.
Nessuna nuova migrazione, dipendenza o autorizzazione Android in questo blocco.

Da provare su telefono: entrambi i pannelli in IT/EN e font ingranditi, chiusura
X/Indietro, GNSS all'aperto durante condivisione, permessi negati/revocati,
servizi posizione spenti, connessione assente/server non disponibile e callback
rilasciati dopo uscita dal pannello. La UI non richiede nuove tab principali.
