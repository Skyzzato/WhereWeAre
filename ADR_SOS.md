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

## Utenti vicini: non disponibile, default OFF

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
