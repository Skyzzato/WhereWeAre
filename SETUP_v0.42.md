# WhereWeAre v0.42 — ambiente di test

Android `versionName=0.42`, `versionCode=11`, package `com.whereweare.app`.
Baseline locale v0.41/10, commit `8d68ebd`; nessun ripristino dal remoto.

## Stato verificato il 16/09/2026

- Destinazione confrontata con `local.properties`: `vqvouzpsgbuaddcyitzg`, progetto
  WhereWeAre collegato a Skyzzato/WhereWeAre. Nessun altro progetto server modificato.
- Strutture 012/015 e bootstrap 017 presenti; 018 assente prima del deploy.
  Applicate via SQL Editor le migrazioni **018_nearby_sos.sql** e **019_v0_42.sql**.
  Il progetto non mostrava una cronologia CLI delle migrazioni: non è stata
  inventata né riscritta. Non rieseguire la 018 sul progetto già aggiornato.
- Bootstrap remoto HTTP 200: **0.42/11**, `nearby_sos=true`, minimo client invariato.
- Posizioni volontari private, RPC anonime e accesso client alla coda negati:
  verificati i privilegi sul server. Nessuna adesione automatica (registro vuoto).
- `remote_v042_smoke.sql` PASS sul database remoto, con ruoli autenticati e
  fixture transazionali: adesione, anonimato, selezione, accettazione, revoca,
  retry. Rollback finale di tutto: nessun SOS o utente di prova persistito.
- `send-meeting-push` prima assente, ora distribuita con `verify_jwt=false`
  e controllo applicativo obbligatorio `PUSH_DISPATCH_SECRET`. Deploy dall'editor
  usando la concatenazione di payload.ts, delivery.ts, index.ts, eliminando
  solo i due import locali. Una chiamata non autenticata restituisce HTTP 401.

## Push: configurazione ancora necessaria

Creato su autorizzazione il progetto Firebase `whereweare-c34e0` (WhereWeAre),
piano gratuito Spark, senza Analytics. Registrata l'app `com.whereweare.app`;
FCM HTTP v1 risulta abilitato nella console. I quattro parametri Android FCM
sono stati salvati in `local.properties`, escluso da Git, e l'APK è stato
ricompilato con successo. Il nuovo artefatto locale è descritto in VERIFICATION_v0.42.md.
Nella dashboard server non risultano custom secret: mancano
`FIREBASE_SERVICE_ACCOUNT` e `PUSH_DISPATCH_SECRET`. Non è stato configurato
uno scheduler nuovo. Il deploy del codice **non equivale a push operative**.

Per completare il collegamento server, in attesa della conferma specifica
per la creazione dell'identità e il trasferimento della chiave nei secret Supabase:

1. Creare l'identità server dedicata `whereweare-push-dispatcher` con il solo
   ruolo Firebase Cloud Messaging API Admin e generare la chiave privata.
   Salvare il JSON privato esclusivamente nel secret server
   `FIREBASE_SERVICE_ACCOUNT`. Creare un segreto casuale per `PUSH_DISPATCH_SECRET`
   e conservarne la copia per lo scheduler in Vault, mai nel client o nel Git.
2. Configurare un POST ogni minuto al dispatcher del progetto verificato con
   `Authorization: Bearer <segreto dispatcher>` usando Cron/Vault. Verificare
   HTTP 200, esecuzioni recenti, accettazione FCM e ricezione su account di test.
3. Completare le verifiche del nuovo APK prima della pubblicazione.
   Non sostituire silenziosamente asset già pubblicati.

La ricezione **nell'app aperta** usa già inbox, refresh/realtime esistenti e non
richiede Firebase. Aprire gli aggiornamenti nella mappa per vedere il SOS.

## Parametri server e prova con due account

In `private.nearby_config`: disponibilità 900 s, fix 900 s, raggio 2000 m,
massimo 20 destinatari vicini/evento, precisione accettata fino a 1000 m;
cooldown mittente 300 s, 6 SOS/giorno, 6 inviti/ora per destinatario.
Il server consente di ridurre il cooldown per lo sviluppo fino a 10 s e
aumentare i limiti entro i vincoli della tabella. Nessuna opzione client li aggira.
Non cambiare questi parametri nel progetto pubblico per effettuare prove.

1. Usare due account **esplicitamente di test**, senza coinvolgere altri utenti.
2. Sul telefono B: Impostazioni → Condivisione e posizione → SOS →
   Ricevi SOS nelle vicinanze. Attendere conferma, poi «Aggiorna disponibilità
   e posizione». Consenti il GPS; verifica la disponibilità confermata.
3. Entro 15 minuti e a meno di 2 km, sul telefono A: Mappa → SOS,
   categoria e opzione «Invia anche ai volontari nelle vicinanze». Leggere
   il consenso e avviare il countdown annullabile. Contatti e gruppi sono
   selezioni separate e non obbligatorie quando si scelgono i vicini.
4. Su B aprire gli aggiornamenti: prima solo area indicativa e categoria;
   «Posso intervenire» rivela il nome di B ad A e nome/fix di A a B per il solo SOS.
5. Provare ritiro, revoca, chiusura e scadenza. La revoca offline resta pendente
   e si sincronizza alla riconnessione con l'app aperta, anche dopo un riavvio.
   Nessuna posizione del volontario viene condivisa con il mittente.
6. Rispettare i limiti e chiudere ogni SOS prima di crearne un altro. Riprovare
   lo stesso invio usa lo stesso ID. Un fix vecchio impedisce solo la ricerca
   vicini, non l'invio ai destinatari ordinari.

L'app non sostituisce i servizi di emergenza. Non considerare registrazione o
accettazione FCM come prova di consegna. Background/telefoni reali restano da collaudare.

## Riprodurre il deploy su un ambiente già verificato

Le migrazioni 001–017 restano immutate. Per un ambiente che le ha già applicate,
eseguire 018 e 019 in ordine con SQL Editor, oppure con accesso DB amministrativo:

```powershell
psql "$env:WWA_DEV_DB_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/018_nearby_sos.sql
psql "$env:WWA_DEV_DB_URL" -v ON_ERROR_STOP=1 -f supabase/migrations/019_v0_42.sql
supabase functions deploy send-meeting-push --project-ref vqvouzpsgbuaddcyitzg
```

Verificare prima progetto e migrazioni già presenti; nessun reset DB. Il DSN e
le credenziali devono essere configurati localmente, senza inserirli nei log.
