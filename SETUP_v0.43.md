# Server e parametri v0.43

Il 16/09/2026 è stata applicata la sola migrazione 020_v0_43.sql al progetto vqvouzpsgbuaddcyitzg, già a 0.42/11. SQL Editor: successo. Non è stata inventata una cronologia CLI delle migrazioni. Non rieseguire la 020 sullo stesso schema: contiene nuovi indici, trigger e colonne.

Le migrazioni 001–019 restano immutate. Nessun reset, eliminazione di luoghi esistenti o iscrizione automatica. L’indice univoco owner_id/name_key arbitra le scritture concorrenti; il trigger e le RPC controllano tutti i nomi normalizzati, compresi eventuali duplicati preesistenti. Tali duplicati conservano nome, ID e regole: uno mantiene la chiave indicizzata e gli altri restano con chiave NULL; per modificarli occorre scegliere un nome distinto. Le nuove creazioni non possono sfruttare questa eccezione. Il vecchio save_place resta compatibile e conserva l’icona.

Nuove RPC autenticate: sos_registered, save_place_v043, end_nearby_availability. Nessun accesso anonimo o lettura geografica libera. Rate limit, cooldown, RLS e protezioni di prossimità invariati.

Consenso: permanente fino a revoca, senza TTL. Disponibilità e fix: 900 secondi ciascuno. Rinnovo Android: acquisizione singola bilanciata ogni 300 secondi con Activity visibile, rete e permessi; altrimenti riuso dei fix che la condivisione già attiva produce, senza nuovo GPS continuo. Frequenze lente, assenza di rete, permessi negati o sistema in background possono far scadere la disponibilità; non revocano il consenso. Il logout richiede ACK della fine disponibilità prima di completare, e non disattiva l’ultimo fix attribuito a un’altra sessione. Non è garantita disponibilità continua in background.

Luoghi: nessun nuovo limite numerico. Rimane il limite tecnico preesistente di 20 regole per account, mostrato nella UI, e di 50 destinatari per regola. Il monitoraggio usa il servizio di condivisione e la valutazione server esistenti; non ci sono geofence Android da deregistrare. La cancellazione serializza le regole in corso e rimuove gli avvisi non ancora spediti collegati tramite source_place_id. Eventi storici e notifiche già consegnate non vengono ritirati; gli eventi precedenti alla migrazione non avevano tale collegamento.

SOS: GPS fino a 8 secondi, ultima posizione entro 24 ore come prima. RPC fino a 12 secondi; timeout o risposta persa attivano verifica tramite ID. Il recupero al riavvio legge soltanto. Retry esplicito entro 5 minuti; successivamente l’utente deve verificare/chiudere l’esito e preparare un nuovo SOS. ACK server non significa consegna o lettura.

Connessione: timeout RPC 12 secondi; fino a tre retry ravvicinati delle letture temporaneamente fallite, backoff 2/4/8 secondi più jitter, poi polling normale a 30 secondi. Errori HTTP 400/401/403/404 sospendono il polling automatico finché interviene rete/ripresa/refresh. Realtime riconnette con backoff fino a circa 33 secondi; lo stato REST rimane separato. Nessun retry automatico generico delle scritture.

Restano necessari: configurazione FCM e scheduler descritti in SETUP_v0.42.md e collaudo su due dispositivi di test. Nessun secret Firebase nuovo creato o trasferito in questa versione.
