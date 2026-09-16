# Audit regressioni v0.44

Data: 16 settembre 2026. Baseline: v0.43, `639031f`. La segnalazione dell'utente riguarda v0.43; non è stato possibile leggere la build installata sul suo telefono. Questo rapporto distingue sorgente, comportamento testato e limiti operativi.

## Provenienza

Working tree iniziale pulita, ramo `codex/v0.43`, uguale a `origin/codex/v0.43`. Fetch completato prima delle modifiche. Nessun AGENTS.md trovato nel repository o nei suoi antenati. Nuovo ramo `codex/v0.44` dalla v0.43, senza merge o cherry-pick. Tag/release v0.44 non presenti alla verifica Git/GitHub. Nessuna riscrittura di release precedenti.

| Passaggio | Merge-base, uguale alla testa del ramo precedente |
| --- | --- |
| v0.33 → v0.4 | fda3dd4104732097000affd9f43ca735998ecb8d |
| v0.4 → v0.41 | 633966498ee4dc31ce199dd553e186bf009b68c2 |
| v0.41 → v0.42 | 8d68ebdf0fd1b91aef92190ef265597c504df878 |
| v0.42 → v0.43 | 1444c9ba70f29b10c040280a8c603df1609bf700 |

`origin/main` è alla v0.4, antenato della consegna; prima delle correzioni era indietro di sei commit rispetto a v0.43. I rami locali v0.21/v0.3/v0.31/v0.32 puntano ancora a b7a3212: non sono fonti più recenti. La ricostruzione storica v0.2–v0.33 rimane quella di REPOSITORY_RECOVERY.md; non si attribuiscono date originali ai commit recuperati. Non risultano patch dei cinque rami prioritari da recuperare attraverso merge.

I file APK locali v0.42 e v0.43 hanno esattamente gli SHA-256 pubblicati da GitHub: rispettivamente `51dc3a8b2b498bae0447d9a9461cfbcdcd36311d6cf35053a55dd838b0159c1c` e `4e58147076b63d34e9de7c1c6695fe94ff1c678ac4811bfbb1a5f6958cd04e95`. Questo verifica quegli artefatti, non l'installazione sul telefono. La nuova build usa una directory isolata v044 e viene copiata dal suo output Gradle, non dalle cartelle delle release precedenti.

## Cause accertate e correzioni

1. **Funzioni nascoste al fallimento iniziale.** MapScreen subordinava il pulsante SOS a `snapshot.sosAvailable` fin dal commit `285640d`. SharingRepository pubblicava tutte le capability soltanto dopo metadata, richieste e posizioni. Un fallimento delle letture successive lasciava le capability iniziali false. Non è una cancellazione degli SOS nella v0.43. Ora i metadata autenticati vengono pubblicati separatamente; SOS, la sua inbox e Luoghi rimangono raggiungibili. L'invio SOS resta disabilitato con spiegazione e refresh se il server non ne ha confermato la disponibilità. I vicini non sono un requisito per l'invio ordinario. Le impostazioni SOS non scompaiono in attesa dei metadata.
2. **Riconnessione falsa o infinita nelle letture.** Il catch generico impostava `offline=true,syncFailed=true`; MapScreen, PeopleScreen e GroupsScreen mostravano sempre `sync_waiting`, anche dopo la sospensione dei retry. La v0.43 (`7387aab`) aggiungeva backoff e blocco per alcuni status, ma i segnali Realtime potevano comunque provocare nuove letture. Ora categoria e tentativo in corso sono separati; parsing e altri errori permanenti sospendono i tentativi automatici, incluso il percorso Realtime. Il refresh utente resta disponibile. Una risposta 401 permette un recupero sessione limitato, senza ciclo infinito.
3. **Seconda origine del messaggio: servizio GPS.** SharingController manteneva `waiting=true` dopo qualsiasi errore e ritentava ogni tre secondi. Ora gli errori transitori usano backoff 2/4/8/16/30 secondi; quelli permanenti terminano il servizio e conservano l'errore e l'intento di stop server. Il testo di riconnessione compare soltanto durante un tentativo effettivo. La frequenza GPS normale non cambia. Gli errori della telemetria dispositivo restano nella diagnostica della relativa RPC, senza rendere fallita la pubblicazione posizione già riuscita.
4. **Diagnostica troppo aggregata.** ConnectionDiagnostics classificava anche parsing/contratti come server irraggiungibile e una richiesta riuscita cancellava qualsiasi errore precedente. Adesso gli errori persistono per operazione; decodifica metadata/posizioni è misurata, Realtime resta separato, una richiesta dichiarata pubblica non verifica la sessione. Log con operazione, correlazione, durata ed esito della singola richiesta; nessun payload, token o coordinate.
5. **Discontinuità dei marker accertata nel flusso dati.** SharingRepository cancellava tutti i marker remoti a ogni UPDATE di sharing_status, anche quando la condivisione rimaneva attiva. Ora uno stato attivo mantiene i marker; uno stop rimuove il soggetto interessato; una variazione di autorizzazione invalida il proprietario coinvolto. Le revisioni account senza payload continuano a invalidare conservativamente tutti i dati remoti, perché possono rappresentare revoche di gruppi o precisione. MapUiOptions viene mantenuto stabile. Non è stata dimostrata una ricreazione della MapView né riprodotto lo sfarfallio grafico sul telefono: non si dichiara risolto ogni possibile lampeggiamento.

I punti 1–4 spiegano percorsi verificabili capaci di produrre i sintomi. Non è disponibile il log della sessione utente per attribuire il suo errore iniziale a una RPC, una risposta malformata o un problema di rete specifico. Il server attuale risponde al bootstrap e i metadata SQL riescono sui quattro account presenti, con SOS/Luoghi abilitati.

## Mappa dei moduli

Mappa/camera/azioni: MapScreen, MapViewModel, MapStyle, Avatar, ApproximateAreas. Dati/sessione: SharingRepository, ConnectionDiagnostics, AuthRepository, BootstrapRepository, NetworkMonitor. Background: SharingController, LocationForegroundService, StopSharingWorker. SOS: SosUi, SosRecipients, SosOperationRepository, NearbySosRepository, migrazioni 015/018/020. Eventi: CheckinUi, AppEvent, EventNotificationWorker, dispatcher. Luoghi: PlacesUi, GroupEditor, migrazioni 014/020. Privacy/gruppi: PrecisionUi, PeopleScreen, GroupsScreen e migrazioni 009/010/012/016. Identità: SettingsScreen, AvatarEditor, QrScreens, MainActivity, InviteStore.

## Matrice funzionale

Le prove indicate sono quelle effettivamente eseguite nella suite finale; una verifica di dominio o SQL non equivale a una prova completa su telefono. “UI sì” indica un percorso presente nel sorgente, salvo le interazioni SOS esplicitamente testate. Stati riferiti alla baseline v0.43; esito/intervento riferiti alla consegna.

| Funzione / atteso | Riferimento | Stato v0.43 | UI | Server | Prova eseguita | Esito | Intervento / limite |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Stili, mirino, pan/zoom e follow volontario | v0.21/v0.33/v0.41 | NON ANCORA VERIFICATA su GPU | Sì | Tile provider | Revisione MapScreen, test dominio mappe | Parziale | Opzioni stabili; manca test cartografico nativo |
| Marker continui durante stato sharing attivo | v0.33/v0.43 | REGREDITA nel flusso di invalidazione | Sì | Realtime | V044Test, 30 cicli | PASS dati | Stop mirato; revoche generiche restano conservative |
| ON/OFF, stop persistente e ripresa servizio | v0.4 | PRESENTE E VERIFICATA nei test repository | Sì | Revisioni sessione | SharingRecoveryTest + sharing_recovery.sql | PASS | Backoff e arresto errori permanenti; OEM non testato |
| Ultima posizione, scadenza e nessuna cronologia GPS | v0.2/v0.33 | PRESENTE E VERIFICATA nel dominio | Sì | Visibilità/RLS | DomainTest, SQL sicurezza e scadenze | PASS | Nessuna cronologia introdotta |
| Avatar, stelline, popup coordinate/OSM | v0.21/v0.41 | NON ANCORA VERIFICATA end-to-end | Sì | Storage e grant | AvatarDraftTest, V021/V041, audit UI | Parziale | Fotocamera/file e ritorno reale da collaudare |
| Precisione assente: mai ± null m | v0.42 | PRESENTE E VERIFICATA | Sì | ACK posizione | AccuracyTextTest, V041Test | PASS unità | Invariato |
| Persone, gruppi, richieste, revoche e rimozioni | v0.2–v0.33 | PRESENTE E VERIFICATA SQL | Sì | RLS/RPC | Suite sicurezza storica e corrente | PASS SQL | Scambio su telefoni non eseguito |
| Codici, copia, WhatsApp, invito attraverso login | v0.33/v0.4 | BLOCCATA DA CONFIGURAZIONE per link HTTPS | Sì | INVITE_BASE_URL | OnboardingTest, audit TypedInvites | Parziale | Codici/QR presenti; dominio inviti non configurato |
| QR persone/gruppi, full screen, luminosità | ADR_QR/v0.4 | PRESENTE E VERIFICATA nel dominio | Sì | Nessuna extra | QrTest | PASS unità | Scanner/camera reale non testato |
| Nome/icona gruppo, reinserimento, approvazioni | v0.31/v0.42 | PRESENTE E VERIFICATA SQL | Sì | edit_group e membership | V03/V033 e suite SQL | PASS | Nessun vecchio pulsante reintrodotto |
| Gruppi temporanei e scadenze | v0.4 | PRESENTE E VERIFICATA SQL | Sì | 012/016 | GroupExpiryTest + SQL | PASS | Invariato |
| Precisa/250/500/1000 m, persona e gruppo | ADR_SHARED_PRECISION | PRESENTE E VERIFICATA SQL | Dipende metadata | 009, RLS | SharedPrecisionTest, shared_precision.sql | PASS | Approssimazione server, non solo disegno client |
| Chi mi vede: card e destinatari autorizzati | v0.42 | PRESENTE E VERIFICATA nei test | Dipende metadata | location_audience | V042 rendering, SQL precisione | PASS limitato | Mantiene spiegazioni e timestamp; multiutente UI non testato |
| Ritrovo/bandierina e Bengala distinto da SOS | v0.3/v0.41 | NON ANCORA VERIFICATA end-to-end | Sì | meeting RPC | MeetingFeedbackTest, FlareProgressTest, SQL | PASS dominio/SQL | Animazione, suono e background fisici non testati |
| Razzo 46 default, preferenze esplicite conservate | v0.41 | PRESENTE E VERIFICATA | Sì | Stile meeting | V041Test, V033Test | PASS | Nessun reset preferenze |
| Convergenza, Gruppo riunito, bandiera WWA | ADR_ARRIVING/v0.4 | PRESENTE E VERIFICATA SQL | Sì | 013 | flare_convergence.sql, FlareProgressTest | PASS logica | Animazione multiutente nativa non testata |
| ETA reale | ADR_ROUTING | BLOCCATA DA CONFIGURAZIONE | Predisposta | Provider routing | RoutingTest e presenza config | BLOCCATO | Nessun ETA inventato |
| Check-in volontario 24 h, spunta e aggiornamenti | v0.4/v0.41 | PRESENTE E VERIFICATA SQL | Dipende metadata | 011 | CheckinViewModelTest, AppEventTest, checkins.sql | PASS | Nome Aggiornamenti check-in conservato |
| Richiedi posizione con consenso destinatario | v0.41 | PRESENTE E VERIFICATA SQL | Sì | 010 + share_requests | LocationRequestTest e SQL | PASS | Conservata lettura degli esiti inviati/ricevuti; nessun avvio dal mittente |
| Luoghi dinamici, unicità, icona e regole | ADR_PLACES_RULES/v0.43 | PRESENTE MA NASCOSTA/IRRAGGIUNGIBILE se metadata fallisce | Menu/settings | 014/020 | PlacesTest, v043.sql, smoke remoto | PASS SQL | Entrata persistente; cancellazione regole testata localmente |
| SOS ordinario, selettore/countdown/annullo/112 volontario | ADR_SOS/v0.41 | PRESENTE MA NASCOSTA/IRRAGGIUNGIBILE se metadata fallisce | Condizionale | 015/018 | V044InteractionTest, SosTest, SQL remoto A/B/C | PASS UI locale/SQL | Entrata persistente; nessuna chiamata al 112 effettuata |
| SOS esito incerto, doppio tap, retry ID, ripresa e chiusura | v0.43 | PRESENTE E VERIFICATA con mock/SQL | Schermata SOS | sos_registered | V043Test e smoke remoto | PASS | Nessun reinvio automatico tardivo; registrazione ≠ consegna |
| SOS vicino, consenso persistente, fix e disponibilità separati | ADR_SOS/v0.42/v0.43 | PRESENTE E VERIFICATA SQL | Settings | 018/020 | nearby_sos/limits/v043; smoke remoto senza ricerca vicini | PASS SQL | Ricerca testata solo DB locale isolato, mai su estranei reali |
| Inbox SOS distinta e reperibile | v0.41 | PRESENTE MA NASCOSTA/IRRAGGIUNGIBILE semanticamente sotto check-in | Menu check-in | event_inbox | Audit filtro, AppEventTest, SQL remoto | PASS logica | Aggiunta voce Aggiornamenti SOS; inbox conserva anche check-in/luoghi |
| Push SOS/check-in/Bengala a schermo spento | SETUP_v0.42/43 | BLOCCATA DA CONFIGURAZIONE | Android predisposto | FCM, segreti, scheduler | Deno 3 test, dashboard attuale | BLOCCATO | Nessun custom secret, Cron e Vault vuoti; ricezione non certificata |
| Riconnessione e diagnostica per servizio | v0.4/v0.43 | PRESENTE MA NON FUNZIONANTE nei casi descritti | Banner/settings | REST/Auth/Realtime | ConnectionDiagnosticsTest, V044Test, SharingRecoveryTest | PASS nei casi locali | Causa della specifica sessione utente non riprodotta |
| Logout / Esci, nome app, IT/EN, preferenze/privacy | v0.42 | PRESENTE E VERIFICATA nei test locali | Sì | Logout/stop/availability | LocalizedActivityTest, rendering V042/43, audit stringhe | PASS limitato | Cambio account con JWT reali non eseguito |
| Mancato arrivo automatico e rilevamento sperimentale | BLOCKED_OVERDUE_ALERTS/ADR_ARRIVING | RICHIESTA MA MAI COMPLETATA | Spiegazione/flag | Scheduler assente | Audit documenti e Cron | BLOCCATO | Non reintrodotti come funzioni operative |

## Limite di stabilizzazione

Questa consegna corregge difetti riproducibili nel codice e nei test, ma non soddisfa la certificazione funzionale completa richiesta: assenti telefono/AVD, credenziali dei tre utenti di test per login API, FCM server e riproduzione video della mappa. La release è dichiarata **prerelease di collaudo**, non versione stabilizzata. Non sono stati creati progetti, identità amministrative, costi o concessioni RLS.
