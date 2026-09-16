# SOS — consenso, conferme e limiti

SOS è distinto dal Bengala. Il pulsante rosso apre una schermata: categoria,
Persone/Gruppi, spiegazione del consenso alla migliore posizione disponibile,
countdown 5 secondi e annullamento. Uscire o mandare in pausa la schermata durante
il countdown impedisce l’invio. ACTION_DIAL apre 112 senza CALL_PHONE o chiamate
automatiche. WhereWeAre non è collegato a soccorsi o autorità.

Registrazione: ID stabile per retry, un SOS attivo, cooldown 5 minuti e massimo
6 nuovi SOS al giorno. L’ACK segue la transazione server; errori/timeout non
mostrano successo. GPS tentato per massimo 8 s, poi ultima posizione disponibile
entro 24 ore o nessuna posizione. La precisione Android e il timestamp restano
visibili. Nessun servizio continuo viene avviato per l’SOS.

La migliore posizione è un consenso esplicito separato dalla precisione normale,
limitato ai destinatari selezionati e a massimo 6 ore. Accessi tramite soli
gruppi temporanei cessano alla loro scadenza. La chiusura risolto/sto bene/errore
rimuove le coordinate dal contenuto server e invia un evento conclusivo a tutti
i destinatari. Tombstone e stato di audit impediscono la resurrezione da retry.

La UI distingue registrazione, accettazione FCM e visualizzazione nell’app.
accepted_at viene scritto soltanto dal dispatcher dopo una risposta FCM positiva;
lista dispositivi vuota o token non valido non sono accettazione. Neanche FCM
positivo prova la ricezione del telefono. Visualizzazione e risposte sono RPC
verificate per destinatario; non sono ricevute dai servizi pubblici d’emergenza.
La notifica SOS usa un canale di importanza alta, soggetto ai permessi/silenziamenti
Android. Non è una consegna garantita. Il dispatcher aggiornato richiede la 015.

## Decisione storica v0.4–v0.41: utenti vicini non disponibili, default OFF

Nessuna ricerca di sconosciuti, elenco coordinate, identità o posizione precisa
viene esposta. Il controllo sperimentale è visibile ma disabilitato e
NEARBY_SOS_ENABLED=false. L’assenza di un registro opt-in e di un servizio di
ricerca/antiabuso verificato impedisce di attivarlo in sicurezza.

Per una futura attivazione, PostGIS/geography con indice può selezionare candidati
server-side; celle/geohash sarebbero solo un prefiltro e richiederebbero controlli
sui bordi. In entrambi i casi servono opt-in revocabile, disponibilità recente,
RPC non enumerabile, limiti per mittente/destinatario, payload anonimo approssimato
prima di Posso intervenire e consenso per identificazione/precisione successive.
Nessuna estensione o condivisione con sconosciuti è stata attivata implicitamente.
Il segnale acustico locale opzionale non è implementato; è distinto dalle notifiche.

Test locali: countdown/cancellazione, ACK/errore/GPS assente, accessi e risposte,
precisione temporanea, scadenza gruppi, cooldown, chiusura/idempotenza, payload
FCM privo di coordinate e accettazione distinta dalla coda. Mancano test FCM
reali e su due telefoni, permessi negati, background, rete intermittente e
processo terminato. Nessuna funzione o migrazione remota è stata distribuita.

## v0.42: adesione privata e ricerca server attive

La precedente limitazione statica è superata dalla migrazione incrementale 018,
applicata il 16/09/2026 al progetto configurato `vqvouzpsgbuaddcyitzg`.
La 019 allinea il bootstrap a 0.42/11 senza cambiare il minimo client (4).
Nessun account esistente viene iscritto: la tabella privata parte vuota.

L'adesione revocabile, il fix privato più recente e gli inviti per evento sono
separati. La UI salva le modifiche di consenso pendenti per account in DataStore;
ritenta ogni 15 secondi quando l'app è aperta, anche dopo riavvio/accesso allo
stesso account. Fino all'ACK mostra lo stato server precedente e la modifica
pendente. La revoca cancella il fix e ritira tutti gli inviti, anche accettati;
aderire nuovamente non ripristina vecchi accessi. Non avvia la condivisione normale.

La disponibilità si rinnova con un'acquisizione puntuale esplicita. Non è stato
aggiunto un servizio GPS continuo. Timestamp Android basato sull'età monotona
del fix; il server conserva separatamente acquisizione e ricezione, rifiuta
fix vecchi/futuri oltre tolleranza, non finiti o senza precisione utilizzabile.
Ritrasmettere lo stesso fix non rinnova la disponibilità. Non c'è cronologia GPS
dei volontari. Il server controlla separatamente disponibilità e freschezza.

Configurazione privata unica: disponibilità 900 s, fix 900 s, raggio 2000 m,
massimo 20 inviti/evento, precisione massima 1000 m, mittente 300 s e 6 SOS/giorno,
destinatario 6 inviti/ora. Sono limiti amministrativi, non parametri client.
Si usa la distanza sferica senza nuova estensione: adatta al volume di sviluppo.
La ricerca è parte della registrazione autenticata di un SOS; non esiste RPC
geografica libera. Il lock transazionale serializza registrazione, adesione e
risposta; PK, indice univoco outbox e ID stabile impediscono duplicati da retry.
Non esiste nel progetto una funzione di blocco utenti separata da rimozione
contatti, quindi non è stata inventata una lista di blocco.

Gli inviti vicini sono in una tabella privata separata dalle concessioni ordinarie.
Prima dell'accettazione l'inbox restituisce categoria, cella 0,01° (circa 1 km,
dimensione longitudinale variabile) e identità anonima, senza coordinate precise.
Il mittente non legge candidati, conteggi, distanze o nomi degli invitati.
Dopo «Posso intervenire» entrambi vedono il nome necessario al singolo evento;
il volontario vede il fix SOS, il mittente non vede la posizione del volontario.
Il ritiro, la revoca, la chiusura e la scadenza interrompono l'accesso. Gli inviti
pendenti scadono anche con la disponibilità; l'intervento già accettato resta
valido fino a ritiro/revoca/chiusura/scadenza evento. Nessuna relazione permanente.

Contatti/gruppi mantengono il flusso precedente; sono deduplicati prima degli
inviti vicini. Un fix vecchio fino a 24 ore può servire al flusso ordinario, ma
non alla ricerca vicini. In tal caso il server registra l'evento e dichiara la
ricerca non eseguita. L'assenza di risposte non rivela il numero di candidati.

Dispatcher distribuito: verifica `push_job_authorized` immediatamente prima di
ogni richiesta FCM. L'app recupera nuovamente l'inbox autenticata. Payload solo
identificativi; accettazione FCM, visualizzazione e risposta restano distinti.
La coda non garantisce exactly-once verso FCM in caso di crash dopo l'invio:
l'identificativo stabile e la deduplicazione Android limitano i duplicati visibili.

Verifica remota transazionale PASS con fixture temporanee e rollback: default OFF,
adesione, selezione anonima, accettazione, revoca e retry. Nessun SOS di prova è
stato inviato ad altri utenti. I test non certificano ricezione su telefoni,
background o notifiche push. Stato operativo e blocchi FCM: SETUP_v0.42.md.

## v0.43: recupero e rinnovo disponibilità

L’intento SOS è conservato per account prima della scrittura; il singleton sopravvive alla navigazione, il riavvio verifica solo l’ID. Nessun reinvio tardivo: retry esplicito entro 5 minuti. Rifiuti certi e risposte perse sono distinti e persistenti. Adesione permanente invariata; rinnovo puntuale 300 s in primo piano e riuso di fix già prodotti in background. TTL disponibilità/fix 900 s, limiti antiabuso invariati. Migrazione 020 applicata e verificata con rollback; dettagli in SETUP_v0.43.md.
