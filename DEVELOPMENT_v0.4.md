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
Blocchi 01–04 completati nelle verifiche automatiche; blocchi 05–18 ancora da completare. Questo documento è
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

## Blocco 04 — dettagli posizione e dispositivo

Il popup avatar mostra anche batteria, servizi posizione e timestamp separato
dello stato dispositivo. Il dato scade dopo 10 minuti; una posizione vecchia è
etichettata esplicitamente e conserva data/ora. Card scorrevole per contenere i
nuovi dettagli. Aggiunto Apri su Google Maps come intent esterno con
[Maps URL](https://developers.google.com/maps/documentation/urls/get-started),
senza SDK o API key; OpenStreetMap preservato.

Migrazione nuova `008_device_status.sql`: colonne opzionali in latest_locations,
RPC update_device_status autenticata e vincolata alla sessione attiva, timestamp
server, batteria 0–100 e nessun accesso più ampio rispetto alla RLS posizione.
Il cambio sessione azzera i vecchi dettagli, evitando di attribuire la batteria
di un dispositivo al successivo. Nessuna riscrittura delle migrazioni 001–007.

Capability `features.device_status`: false sul client per server pre-008, true
solo dopo la migrazione. I client precedenti restano compatibili; la versione
bootstrap non viene ancora promossa a v0.4. Campionamento al massimo ogni minuto;
invio se varia o dopo cinque minuti, soltanto durante la condivisione. Nessun
permesso batteria aggiunto. Dati mancanti o scaduti non sono stimati.

`node supabase/tests/run-v03.mjs --v04`: PASS (001–008, riapplicazione 008,
test dispositivo e regressioni). Test: autorizzati/estranei, revoca, stop,
sessione sostituita, input invalido, privilegi anon e scrittura diretta.
`gradlew.bat :app:build`: PASS, debug/release, **56 test**, lint senza errori.
Log locale `.tools/v04-block04-build.log`. Hash 001–007 invariati.
La 008 è stata verificata solo localmente, **non applicata al server remoto**.

Da verificare su due telefoni: lettura percentuale reale, servizi disattivati,
scadenza metadati, cambio telefono, popup con font grandi e apertura dei due
provider esterni. Nuove dipendenze e permessi Android: nessuno.

## Blocco 05 — QR persone e gruppi

Mostra QR nelle card codice personale e gruppo. Finestra chiara con codice,
X/Back, luminosità massima e schermo acceso solo mentre visibile; ripristino
su pausa e uscita. Scanner da Persone e Gruppi, con instradamento per tipo
tramite InviteStore e conferma nel normale flusso inviti. Nessun consenso GPS
automatico. Codici manuali, copia e condivisione restano disponibili.

CameraX 1.6.2 + ZXing core 3.5.4: decoding interamente locale, senza modelli
da scaricare. CAMERA richiesto contestualmente; negazione e impostazioni
gestite anche nella foto avatar. Hardware fotocamera/autofocus opzionale.
Valutazione alternative e misura dimensione in ADR_QR.md: +4,04 MiB circa
per APK debug (+6,2%). Nessuna migrazione per questo blocco.

Build Android debug/release, test e lint: PASS, 60 test senza fallimenti,
nessun errore lint. Quattro test QR coprono bitmap realmente decodificate,
rotazione, codici attuali/legacy, payload estranei o tecnici, buffer camera
con padding/stride e luminosità/flag ripristinati ripetutamente.
SQL 001–008 e regressioni: PASS; hash 001–007 invariati. Risorse IT/EN allineate.

Restano prove manuali su telefono: fotocamera, luce scarsa, permesso
negato/revocato, cambio app, luminosità automatica, cross-navigation e foto
profilo. Nessun dispositivo collegato disponibile per eseguirle qui.

## Blocco 06 — precisione condivisa e destinatari

Nuova migrazione 009: default e override Persona/Gruppo 0/250/500/1000 m,
autorizzazione effettiva server-side, RLS raw limitata ai destinatari esatti,
RPC esplicita per posizioni approssimate e pubblico attuale. Trasformazione
stabile a griglie annidate e limiti documentati in ADR_SHARED_PRECISION.md.
Nuove invalidazioni senza coordinate per i destinatari approssimati.

Impostazioni, Persone e Gruppi mostrano le scelte quando la capability è
disponibile. Mappa con area geografica tratteggiata e testo esplicito, senza
pin/avatar o coordinate esatte fittizie. Pannello destinatari deduplicato con
origini e precisione effettiva, timestamp e gestione indisponibilità.
La precisione di rilevamento GPS rimane indipendente.

`gradlew.bat :app:build`: PASS, debug/release, 62 test e nessun errore lint.
Log `.tools/v04-block06-build.log`. Test Android per mapping e geometria area.
SQL 001–009: PASS, 009 riapplicata e regressioni precedenti rieseguite.
Test sicurezza: raw negato ad approssimati/estranei, proprietario esatto,
override e default, permessi sovrapposti, revoca esatta, stop/scadenza,
privilegi diretti, stabilità e limiti geometrici anche ai poli/cambio data.
Hash 001–007 invariati, risorse IT/EN allineate. Nessuna nuova dipendenza o
autorizzazione Android. La 009 non è ancora applicata al server remoto.

Prove manuali residue: due account con realtime remoto, cambi precisione
durante ricezione, aree mappa a diversi zoom/font e pannello destinatari
durante perdita rete. La baseline non contiene una history GPS da migrare.

## Blocco 07 — richieste posizione

Estesa share_requests con purpose, mantenendo un solo ciclo di vita richieste.
La richiesta di posizione non è un collegamento reciproco: accettazione a
senso unico, precisione preservata e avvio del normale SharingController solo
dopo permesso GPS e risposta server positiva. Rifiuto o permesso negato non
avviano tracking. Nessuna durata obbligatoria per la condivisione.

La 010 introduce deduplicazione, cooldown, scadenza richiesta e notifica
idempotente nella push_outbox esistente. Dispatcher esteso con payload tipizzato;
notifica verificata dal worker tramite inbox autenticata, apertura Persone,
richiamo nell'app e scelta Rifiuta/Condividi. I vecchi client continuano a
ricevere solo le richieste di collegamento nella loro inbox.

Build debug/release, 65 test Android e lint: PASS. Tre nuovi test ViewModel
verificano ordine ACK/avvio, rifiuto e permesso negato. Log
`.tools/v04-block07-build.log`. Due test Deno dispatcher/payload: PASS.
SQL 001–010 e regressioni: PASS, incluse riapplicazione 010, idempotenza,
privilegi, consenso non reciproco, precisione, cooldown e lease outbox.
Hash 001–007 invariati. Nessuna nuova dipendenza o autorizzazione Android.

Istruzioni incrementalmente aggiornate in SETUP_v0.4.md. Migrazione e dispatcher
non distribuiti da questo sviluppo; restano da verificare FCM reale e UX su
due dispositivi. Le credenziali/scheduler non sono stati inventati o modificati.

## Blocco 08 — Check-in e infrastruttura eventi

Check-in Sono qui / Sono arrivato / Tutto bene, messaggio facoltativo e selezione
Persone, Gruppi o partecipanti a un Bengala. Menu compatto delle azioni Mappa,
inbox senza nuova tab, apertura/centratura dello snapshot e rimozione da parte
del mittente. Acquisizione singola GPS con timeout: non avvia né ferma il servizio
continuo e non scrive latest_locations.

Migrazione 011: eventi e consegne in schema privato, payload autorizzato per
destinatario, scadenza di visibilità 24 ore, idempotenza e tombstone senza
coordinate dopo rimozione. Le letture non aumentano mai la precisione rispetto
all'invio e applicano restrizioni successive; i gruppi vengono ricontrollati.
Il trasporto riutilizza outbox/lease/dispatcher, con un payload FCM senza
coordinate e un worker che verifica l'inbox autenticata. Notifica e richiamo
in primo piano aprono la mappa; le aree approssimate non diventano pin esatti.

Build debug/release, 68 test Android e lint: PASS. Nuovi test per parsing,
scadenza, precisione snapshot, mancato avvio/stop tracking, GPS non disponibile
senza pubblicazioni. SQL 001–011 e regressioni: PASS, incluse riapplicazione,
destinatari, assenza accesso raw, upgrade/downgrade precisione, gruppi/Bengala,
scadenza, rimozione e retry. Due test Deno aggiornati: PASS. IT/EN allineate,
hash 001–007 invariati. Nessuna dipendenza o autorizzazione Android aggiunta.

Restano test fisici GPS/permessi e FCM remoto; SETUP_v0.4.md distingue visibilità
da retention fisica. Migrazione e dispatcher non sono stati distribuiti.


## Blocco 09 — Gruppi temporanei

Scadenza opzionale in creazione/modifica, selettori data/ora locali, indicazione
permanente/temporaneo nella pagina Gruppi. Conferma dopo ACK, errori nel dialogo,
controlli date future e orari DST inesistenti/ambigui. Compatibilità con server
precedenti tramite capability; nessuna nuova tab, dipendenza o autorizzazione.

Migrazione 012: controlli di scadenza in appartenenza, visibilità, precisione,
inbox/inviti e snapshot riservati ai gruppi. Nessuna dipendenza da cron o pulizia.
I gruppi scaduti non sono riattivabili; permessi personali indipendenti restano
validi. La mappa esclude localmente i fix privi di un consenso ancora attivo.

Build debug/release, 70 test Android e lint PASS (.tools/v04-block09-build.log).
SQL 001–012, riapplicazione 012, revoca raw/RPC/check-in/inviti e regressioni PASS;
hash 001–007 invariati. Restano prove fisiche dei selettori e della scadenza
su due telefoni. Nessuna migrazione applicata al server remoto.


## Blocco 10 — Bengala evoluto

Riepilogo dei partecipanti dal pin/deep link: Arrivato, In arrivo, posizione
non recente, incerta o indisponibile; distanza in linea d’aria esplicitamente
etichettata, nessuna ETA inventata. Raggio iniziale 100 m. I 50 stili rimangono.
L’animazione originale mostra tre persone che sollevano insieme un pin su una
piattaforma circolare, senza riferimenti militari.

Migrazione 013: progresso calcolato dal server con la precisione autorizzata
per ciascun destinatario; GPS recente entro 2 minuti e intera incertezza entro
il raggio, oppure Check-in Arrivato esplicitamente associato al Bengala.
Il completamento richiede che tutti, creatore compreso, risultino arrivati
secondo i permessi di ogni partecipante: non rivela coordinate nascoste tramite
un esito derivato. Scatta su pubblicazione GPS, Check-in e lettura inbox, senza
cron; timestamp persistito e notifica riutilizzano la chiusura esistente.

Build debug/release, 72 test Android e lint PASS (.tools/v04-block10-final.log).
SQL 001–013 e regressioni PASS: missing/stale/approximate/consensi mancanti,
Check-in e completamento diretto su GPS, idempotenza migrazione. Hash 001–007
immutati. Nessuna dipendenza o permesso aggiunto. Da provare su dispositivi:
fluidità animazione, notifiche remote e comportamento con molti partecipanti.
Migrazione non distribuita; nessuna garanzia di consegna FCM verificata.


## Blocco 11 — Routing ed ETA

RoutingRepository separa UI e provider. Adapter Valhalla HTTP/JSON, selezione
piedi/auto/bici nel dettaglio Bengala, distanza percorso e tempo stimato solo
da risposta valida. Configurazione compilazione ROUTING_PROVIDER/ENDPOINT;
valori assenti per default, nessuna richiesta a servizi demo. Coordinate solo
su azione esplicita, origine precisa recente con accuratezza <=100 m, HTTPS,
nessun ID/token account, redirect negati, timeout/risposta limitati.

ADR_ROUTING.md confronta Valhalla, GraphHopper e OSRM con fonti primarie,
licenze, hosting, limiti, privacy e requisiti di verifica sui sentieri.
L’endpoint di produzione rimane da scegliere e configurare: routing reale non
attivato nella build corrente. Nessuna nuova dipendenza, permesso o migrazione.

Build debug/release, 75 test Android e lint PASS (.tools/v04-block11-build.log).
Test profili, conversione unità, risposta malformata/errore, assenza richieste
per origine vecchia/approssimata e provider disabilitato. Hash 001–007 invariati.
Restano collaudo endpoint reale e qualità ETA montagna; nessuna SLA dichiarata.


## Blocco 12 — Luoghi e regole

Impostazioni ospita Casa, Lavoro e due luoghi personalizzati, coordinate/raggio,
CRUD e regole ingresso/uscita/arrivo di una persona, armabili e sospendibili.
Le regole usano solo la condivisione normale già attiva; nessun monitoraggio
aggiuntivo o nuovo permesso. Il testo di armamento spiega il limite.

Migrazione 014: dati privati owner-only, RPC con controllo proprietario,
consensi del soggetto ricontrollati con precisione autorizzata. Prima posizione
baseline, due fix concordi separati da 30 s, isteresi 30 m e cooldown 15 minuti;
reset sessione/gap lungo. Eventi privi di coordinate dei luoghi, push di soli ID,
inbox/richiamo/deep link e worker autenticato riutilizzati. ADR_PLACES_RULES.md
spiega privacy, frequenza di campionamento e limiti background.

Build debug/release, 77 test Android e lint PASS (.tools/v04-block12-final.log).
SQL 001–014/regressioni PASS, inclusi raw negato, proprietari separati,
nessuna coordinata nei messaggi, debounce/dedup, precisione e nuova sessione.
Due test Deno dispatcher/payload PASS; hash 001–007 invariati. Nessuna dipendenza
o autorizzazione aggiunta. Da collaudare movimento reale, UX e FCM su telefoni;
nessuna migrazione o funzione remota distribuita.


## Blocco 13 — Sta arrivando (sperimentale disattivato)

Predisposto il rilevatore puro con ETA routing obbligatoria, tre campioni su
60 s, diminuzione ETA/percorso, movimento coerente oltre incertezza, freshness,
isteresi e cooldown. Nessuna automazione è armabile: manca un servizio routing
approvato e verificato. ARRIVING_EXPERIMENTAL_ENABLED=false e UI esplicita;
ADR_ARRIVING.md distingue algoritmo predisposto da funzione operativa.

Build debug/release, 79 test Android e lint PASS (.tools/v04-block13-final.log).
Test ETA assente, sequenza coerente, fermo, stale, allontanamento e dedup del
candidato. Nessuna migrazione, dipendenza o permesso; hash 001–007 invariati.
Integrazione operativa rinviata fino a infrastruttura e collaudo sul campo.


## Blocco 14 — Mancato arrivo: bloccato dall’infrastruttura

Nessuno scheduler remoto monitorato è stato verificato. Il setup esistente
contiene istruzioni di configurazione, non evidenza di operatività. Come
richiesto dal prompt, il blocco viene fermato senza timer locale sostitutivo,
nuova funzione attiva o promessa di protezione. BLOCKED_OVERDUE_ALERTS.md
specifica prerequisiti, evidenza e test necessari. Nessuna migrazione abilitante,
permesso o dipendenza. Si continua con i blocchi indipendenti; questo requisito
fondamentale impedisce la finalizzazione della v0.4 e l’incremento versione.


## Blocco 15 — SOS per destinatari autorizzati

Pulsante rosso separato, schermata consenso, categorie, countdown annullabile
5 s e dialer 112. Nessun CALL_PHONE o avvio tracking. Invio anche senza GPS,
stati invio/confermato/non confermato, ID stabile e guardia cambio account.
SOS in mappa/inbox, destinatari, visualizzazioni/risposte, chiusure notificate.

Migrazione 015: migliore posizione per consenso esplicito limitato a 6 ore,
scadenza gruppi ricontrollata, cooldown/audit, ACL e raw privati; chiusura cancella
coordinate e retry non resuscita eventi. Il dispatcher registra accettazione
FCM separatamente dal completamento coda; nessuna prova fittizia di consegna.
Nessun destinatario 112/soccorsi. ADR_SOS.md documenta il limite delle notifiche.
SOS sconosciuti vicini resta OFF/non disponibile: ricerca opt-in e antiabuso
non verificati; segnale acustico locale opzionale non implementato.

Build debug/release, 82 test Android e lint PASS (.tools/v04-block15-build.log).
SQL 001–015/regressioni PASS; tre test Deno dispatcher/payload/accettazione PASS.
Test countdown/cancel/ACK/errore/GPS assente, permessi SQL, precisione SOS,
risposte, cooldown, chiusura/retry e scadenza gruppi. Hash 001–007 invariati.
Nessuna dipendenza o autorizzazione Android aggiunta. Test remoti FCM/dispositivi
ancora necessari; nessuna distribuzione backend effettuata.
