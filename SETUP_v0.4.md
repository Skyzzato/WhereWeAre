# Distribuzione incrementale v0.4

La versione Android resta 0.33 (8) finché tutti i blocchi richiesti non sono
completati. Questo documento descrive soltanto i blocchi implementati.

Applicare in ordine, dopo le migrazioni 001–007 immutate:

* 008_device_status.sql: dettagli opzionali dispositivo durante condivisione.
* 009_shared_precision.sql: protezione raw, posizioni approssimate e destinatari.
* 010_location_requests.sql: richieste posizione nella tabella share_requests,
  distinte dai collegamenti reciproci, e ampliamento della coda push esistente.
* 011_checkins_events.sql: snapshot Check-in, consegne autorizzate per
  destinatario e inbox eventi riutilizzabile.

* 012_temporary_groups.sql: scadenza opzionale dei gruppi, controllata nelle
  autorizzazioni anche senza job di pulizia.

* 013_flare_convergence.sql: stati autorizzati dei partecipanti e completamento
  automatico del Bengala su pubblicazione posizione, Check-in o lettura inbox.

Le migrazioni sono state provate localmente; non sono state applicate al server
remoto da questo sviluppo. Le capability vengono abilitate dalle migrazioni.

Con la 010 distribuire anche la versione aggiornata di `send-meeting-push`,
incluso `payload.ts`. Il dispatcher gestisce Bengala e richieste posizione con
gli stessi lease/retry. Firebase, secret e scheduler descritti in SETUP_v0.3.md
restano necessari: l'esistenza dei file non dimostra che siano configurati.
Non sono stati inventati o salvati nuovi segreti. In assenza di push, la inbox
e il richiamo nell'app si aggiornano tramite il normale refresh/realtime.

I messaggi FCM contengono solo tipo, destinatario e identificatore. Il worker
ricontrolla sessione/account e legge la inbox autorizzata prima di notificare;
non espone richieste rifiutate, scadute o ricevute da un altro account.
La notifica apre Persone e richiede l'azione dell'utente; non avvia GPS in background.

Con la 011 ridistribuire anche il dispatcher: gestisce il tipo Check-in con
event_id e apre lo snapshot autorizzato sulla mappa. Coordinate e messaggi non
sono nel payload FCM. Lo schema privato conserva evento e consegne; la inbox
autenticata applica scadenza 24 ore e visibilità. La scadenza è di visibilità,
non una promessa di cancellazione fisica automatica: configurare la retention
server secondo la propria politica operativa.

La precisione massima di un Check-in è fissata alla creazione; nuovi permessi
più precisi non svelano il raw del vecchio snapshot. Restrizioni successive
rendono più grossolana anche la rilettura degli snapshot passati. I permessi
dei gruppi selezionati vengono ricontrollati. Un invio personale volontario è
un consenso allo snapshot separato dal toggle continuo; il mittente può
rimuoverlo. La rimozione cancella contenuto/consegne e conserva un identificatore
senza coordinate per impedire che retry tardivi ricreino il Check-in.

Per una richiesta posizione: una richiesta pendente per direzione, riuso dello
stesso identificatore sui retry, una voce outbox per richiesta, scadenza 24 ore,
almeno 10 minuti tra richieste alla stessa persona e massimo 30 nuove richieste
all'ora per mittente. La scadenza riguarda la richiesta, non impone una durata
alla condivisione accettata. L'accettazione non abilita il consenso inverso e
preserva gli override di precisione esistenti.

Prima della distribuzione completa provare su due telefoni: permessi GPS e
notifiche negati, accetta/rifiuta, doppio tocco/retry, cambi account, ricezione
in background, deep link, stop e condivisione già attiva. Le prove locali SQL,
ViewModel e dispatcher non sostituiscono una consegna FCM reale.
