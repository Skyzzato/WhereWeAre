# Precisione condivisa

La migrazione 009 applica l'autorizzazione nel database. Precisione GPS e
precisione condivisa restano distinte. Default 0 preserva il consenso esistente;
override persona e propria appartenenza al gruppo sono null per ereditare.
Tra origini abilitate prevale il raggio minore (0 = precisa). Non si modificano
i permessi di altre persone né si abilita condivisione impostando un raggio.

`latest_locations` resta la singola posizione corrente, senza history GPS.
La policy raw consente proprietario o destinatario con precisione effettiva 0,
oltre ai controlli preesistenti di sessione attiva, consenso e scadenza.
La stessa RLS protegge REST e messaggi realtime. `visible_locations()` restituisce
un DTO esplicito: ai destinatari approssimati arrivano centro area e raggio,
mai coordinate originali, sessione, velocità o direzione. Nessuna funzione
accetta un destinatario arbitrario: l'identità deriva dalla sessione autenticata.
Future history/eventi non possono riutilizzare una policy raw più permissiva.

Riferimento per il comportamento del trasporto:
[Postgres Changes e RLS](https://supabase.com/docs/guides/realtime/postgres-changes).
Con RLS il vecchio record contiene soltanto la chiave primaria; il client non
sottoscrive DELETE. I test locali verificano policy/RPC, non un WebSocket remoto:
prima della distribuzione serve una prova con due account anche sul trasporto.

La trasformazione usa una griglia cartesiana tridimensionale sulla sfera
terrestre (raggio 6.371.000 m). La cella ha lato raggio × 0,9 / √3; il centro
quantizzato viene riproiettato sulla sfera. Il margine 0,9 mantiene lo
spostamento entro il raggio dichiarato anche dopo proiezione. Non è una stima
dell'accuratezza fisica del GPS: mostra l'area di approssimazione della misura.
I test includono equatore, poli e cambio data per 250/500/1000 m.

L'origine è persistente per proprietario, custodita in schema privato; le tre
griglie sono annidate con fattore 2. Non si applica jitter per campione/sessione:
la stessa cella produce lo stesso centro e non offre campioni indipendenti da
mediare. Più punti reali corrispondono al medesimo output. L'origine non è un
segreto crittografico: la sicurezza non dipende dal nascondere l'algoritmo.
Come ogni quantizzazione geografica, attraversamenti ripetuti dei confini,
conoscenze esterne e movimento possono restringere l'area plausibile. Non si
promette anonimato o differential privacy. Cambiare permesso non cancella
informazioni già legittimamente ricevute da un destinatario prima della revoca.

Per gli approssimati, gli aggiornamenti generano solo invalidazioni account
senza coordinate. Il client rilegge l'RPC, invalida subito cache in memoria
su revoca e conserva un polling di recupero ogni 30 s. Nessuna cache persistente
di posizioni altrui. Server precedenti alla 009 mantengono il percorso legacy,
senza mostrare comandi di precisione; dopo la capability non si ripiega su raw
in caso di errore RPC. Vecchi client ottengono solo righe esatte autorizzate.

La mappa mostra aree tratteggiate e diciture esplicite, senza avatar-pin,
coordinate o link che suggeriscano un punto esatto. Il pannello destinatari
elenca una sola volta ogni persona e tutte le origini Persona/Gruppo, con
precisione effettiva, timestamp dell'ultima verifica e nessuna inferenza su
chi stia effettivamente guardando la mappa. Polling solo mentre il pannello è
visibile; errori mostrano indisponibilità, non un falso elenco vuoto.

Distribuzione: applicare 009 dopo 008. Testata localmente; la migrazione non è
stata applicata automaticamente al server remoto. Migrazioni 001–007 immutate.
