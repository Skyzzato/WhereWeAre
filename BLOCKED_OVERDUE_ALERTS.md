# Blocco 14 — Avvisali se non arrivo: non attivato

Stato al 16 settembre 2026: BLOCCATO dall’infrastruttura esterna non verificata.
Non esiste un timer di mancato arrivo presentato all’utente come funzionante.
Non è stato sostituito da WorkManager, allarmi Android o un processo del telefono.

## Evidenza disponibile

SETUP_v0.3.md, punto 5, chiede di configurare Cron/Vault oppure uno scheduler
server che invochi send-meeting-push ogni minuto. Il dispatcher verifica un
secret e gestisce lease/retry, ma i file locali non attestano una pianificazione
attiva, un heartbeat recente, monitoraggio degli errori o una SLA. Non è stata
verificata una configurazione remota. È stata richiesta all’utente indicazione
dell’infrastruttura disponibile, senza chiedere segreti.

Il prompt master richiede esplicitamente di fermare questo blocco se manca un
meccanismo schedulato affidabile. Nessuna migrazione abilita la funzione e
nessuna dicitura suggerisce protezione mentre il telefono è spento.

## Prerequisiti per riprendere

1. Scheduler server amministrato con cadenza <=1 minuto, credenziali in secret
   store, controllo accessi, heartbeat e allarme operativo su run mancati.
2. Destinazioni/deadline persistenti e transazioni server per armamento,
   proroga, annullamento e deduplicazione della scadenza. ACK solo dopo commit.
3. Controllo salute dello scheduler prima di consentire un nuovo armamento;
   stato esplicito quando la garanzia operativa non è disponibile.
4. Outbox atomica per promemoria circa 5 minuti prima e mancato arrivo, con
   destinatari autorizzati, ultima posizione disponibile, timestamp e precisione
   applicata per destinatario. Una posizione vecchia non è prova di arrivo.
5. Test concorrenti proroga/scadenza/arrivo, retry del job, revoche, UTC/DST,
   indisponibilità push; prova reale con telefono mittente spento e destinatario
   su un altro dispositivo. Distinguere registrazione server da ricezione.

Servizio pubblico d’emergenza non coinvolto. Nessuna chiamata automatica.
Questo blocco impedisce di dichiarare completati tutti i requisiti del prompt.
Su successiva richiesta esplicita dell’utente viene pubblicata la versione 0.4 (9),
con questa funzione non disponibile. Il cambio di versione non attiva uno scheduler.
