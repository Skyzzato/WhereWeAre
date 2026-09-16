# Verifiche v0.48

## Baseline e metodo

Repository Skyzzato/WhereWeAre, branch codex/v0.45, baseline 30aba60269af4a92eea9a59448c9035cbe6c7755, working tree iniziale pulita. Nessun AGENTS.md nel repository o nelle directory genitrici controllate. Ricognizione limitata a PlacesUi, mappa, ViewModel, preferenze, controller posizione e rispettive dipendenze. Fetch completato, HEAD coincidente con origin/codex/v0.45. Prima della pubblicazione tag v0.48 assente e API release HTTP 404.

## Diagnosi e implementazione

La UI Luoghi inseriva esplicitamente state.groups e apriva GroupEditor, pur leggendo i luoghi corretti da places_rules. Rimosso quel percorso dalla schermata: gruppi reali e autorizzazioni restano invariati nelle sezioni dedicate. SavedPlace e PlaceRule erano già distinti; save_place_v043 e save_place_rule restano le RPC reali. Un luogo senza regole resta valido. Il sottotitolo sotto Aggiungi regola era place.name, non una relazione server con un gruppo: rimosso, preservando place.id e selezione destinatari.

Selettore mappa dedicato MapLibre con centro/mirino, nessun ViewModel di condivisione o comando check-in/Bengala. La conferma modifica soltanto lat/lon del draft; chiusura e back non invocano conferma. Inizializzazione da coordinate valide del modulo, altrimenti ultimo fix o Roma come vista iniziale esplorabile. Salvataggio sempre subordinato alla validazione del modulo. Nome, raggio e icona rimangono negli stessi rememberSaveable.

Marker principali letti dalla medesima RPC dei luoghi, ricaricati alla ripresa. Preferenza hidden_places per utente e ID, assenza = visibile. Centra attende il salvataggio della visibilità, disattiva follow e imposta centered, evitando la ricentratura al successivo fix. Identità Compose per ID e stato mappa non dipendente da scala/visibilità. Icone emoji senza Surface o sfondo; dimensione autonoma persistente. Minimo tocco 48 dp, padding e linea proporzionata per dimensioni grandi. Nessun cambiamento a marker di persone, gruppi, eventi o Bengala.

Intervallo: PreferencesRepository e stati iniziali ora 5, GlobalDefaults 5. Le preferenze memorizzate non vengono migrate. Bootstrap preesistente a 60 non annulla il nuovo default client. Non esiste un comando di ripristino globale nella schermata corrente; cancellare la preferenza torna a 5. SharingController usa lo stesso flow per location.fixes e set_update_interval; LocationRequest moltiplica seconds*1000L, senza batching e senza rallentamento intenzionale in background. Aggiunto distinctUntilChanged alla coppia precisione/intervallo: scrivere visibilità o aspetto non ricrea il flusso GPS. Invii seriali e cancellazione flatMapLatest conservati.

## Check-in e regressioni

Verifica statica: MapScreen usa CheckCircle per i check-in confermati dell’inbox e ancora il marker a payload.longitude/latitude, mai alle coordinate successive della persona. Il callback di successo resta dopo la RPC create_checkin; nessun marker ottimistico. Cronologia eventi, filtro active, dismiss_event, precisione autorizzata e destinatari invariati.

SQL locale: node supabase/tests/run-v046.mjs, PASS su 12 suite / migrazioni 001–021, comprese checkins, luoghi/regole, condivisione, precisione, richieste posizione, gruppi temporanei, Bengala e v046 SOS. Checkins verifica idempotenza, destinatari autorizzati/estranei, snapshot, cancellazione e scadenza. Si tratta di PGlite con ruoli sintetici, non di sessioni Auth/PostgREST reali.

## Verifiche Android

Build completa :app:build con tools/isolated-v044-build.gradle: PASS. 152 test JVM/Compose, zero fallimenti/errori/skipped; lint zero errori e 70 warning. I primi tentativi hanno rilevato errori nei nuovi test (firma API Robolectric e un intervallo di test non supportato), corretti prima della verifica finale. Rendering ispezionati visivamente nei sei temi: verde comune al pulsante Avvia condivisione, simboli neri, icone luogo senza contenitore bianco e senza tagli. Report analitico BUILD_REPORT.json allegato.

Nuovi test: preferenze su DataStore reale riaperto, isolamento fra utenti, visibilità di default e ripristino, dimensione luogo indipendente dall’avatar, nuovo default con bootstrap legacy e conservazione dell’intervallo esplicito; inizializzazione mappa e validazione; editor luogo, rimozione icona, informazione raggio e comando mappa senza GPS; nessun riavvio acquisizione per preferenze aspetto. Rendering componenti reali su Robolectric nei sei temi: pulsanti e quattro dimensioni luogo/avatar. Nessuna rete finta nei flussi dell’app; mock usati soltanto nei test.

## Limiti delle prove

ADB devices: elenco vuoto. Nessuna installazione su telefono/emulatore, nessuna navigazione cartografica GPU reale, nessuna misura di sfarfallio, nessun E2E tra account reali o SOS inviato. Selezione cartografica, conferma/annullamento, ritorno alla mappa e centratura verificati staticamente; test Compose coprono il modulo, non il motore MapLibre. Frequenza richiesta 5.000 ms verificata nel codice; frequenza osservata, background e schermo spento non misurati. FCM resta come nella v0.47, senza nuova verifica consegna. Nessuna migrazione server richiesta; bootstrap remoto non promosso a stabile.

## Artefatto

APK debug installabile con applicationId com.whereweare.app, versione 0.48/17. Build finale dopo il commit, con --rerun-tasks; verifica firma contro SHA-256 storico acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6. La procedura di packaging richiede working tree pulita, commit uguale a quello registrato prima della build, metadati corretti, test e lint superati. Provenienza e checksum allegati alla pre-release. Nessuna chiave locale versionata.
