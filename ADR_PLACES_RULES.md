# Luoghi e regole — privacy e monitoraggio

Le coordinate dei quattro luoghi appartengono solo al proprietario. Tabelle
private senza grant diretti, RLS owner-only e RPC autenticate controllano ogni
operazione. Le notifiche non includono coordinate, raggio o identificatore del
luogo: condividono nome scelto, soggetto, ingresso/uscita e orario.

Le regole ingresso/uscita sono armate esplicitamente per la propria posizione.
Avvisami quando arriva usa soltanto le posizioni già autorizzate dal soggetto
al proprietario del luogo. La revoca o la scadenza del consenso bloccano la
valutazione; con precisione approssimata si usa il centro autorizzato e tutta
l’incertezza, mai il raw per dedurre un arrivo preciso.

La prima rilevazione stabilisce la situazione senza notificare. Una transizione
richiede due rilevazioni concordi distanti almeno 30 secondi; una zona incerta
azzera il candidato. Isteresi esterna 30 m, cooldown 15 minuti, fix recenti entro
2 minuti, reset dopo 10 minuti senza campioni o cambio sessione. Non vengono
ricostruiti spostamenti avvenuti con condivisione spenta. Il server valuta
soltanto sulle pubblicazioni della sessione attiva e deduplica i timestamp.

## Android e limiti

Non sono introdotti Geofencing API, ACCESS_BACKGROUND_LOCATION o un secondo
servizio GPS. La normale condivisione visibile deve essere attiva; l’app lo
spiega quando si arma una regola. Consumo aggiuntivo GPS nullo: sono riutilizzati
gli aggiornamenti esistenti. Con app chiusa valgono i limiti del servizio di
condivisione, della rete e dei permessi già documentati. Frequenze oltre 10
minuti non consentono di confermare una transizione; frequenze lente ritardano
gli avvisi. Queste regole non sono un sistema di allarme garantito.

La documentazione Android richiede ACCESS_BACKGROUND_LOCATION per geofencing
su Android 10+ e indica ritardi delle transizioni in background. Non abbiamo
introdotto quel percorso o richiesto il relativo consenso:
[geofencing](https://developer.android.google.cn/develop/sensors-and-location/location/geofencing?hl=en),
[accesso in background](https://developer.android.com/develop/sensors-and-location/location/background).

Gli avvisi riutilizzano eventi, outbox e worker autenticato. Il payload push
contiene solo identificatori; lettura dell’evento e controllo account precedono
la notifica. Gli eventi durano 24 ore nella inbox, come i Check-in. Applicare la
014 e distribuire il dispatcher aggiornato prima del collaudo su due telefoni.
La consegna remota FCM non è stata verificata durante lo sviluppo locale.

## v0.43: luoghi dinamici

Superato il limite di quattro slot. Icona privata persistente e selettore riutilizzato dai gruppi, con stato bozza indipendente. Nomi normalizzati univoci per proprietario, indice e guardia server con conservazione dei duplicati storici. Regole modificabili senza rimuovere il luogo; resta il limite tecnico di 20 regole. Rimozione atomica di luogo, regole e push collegati ancora in coda. Nessuna Geofencing API aggiunta. Migrazione 020 applicata; dettagli in SETUP_v0.43.md.
