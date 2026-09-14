# Aggiornamento WhereWeAre v0.31

Per il database v0.3 già aggiornato, eseguire **soltanto** `supabase/migrations/005_v0_31.sql` nel SQL Editor del proprio progetto Supabase: copiare tutto il file, incluso `begin`/`commit`, e premere Run una volta. L'esito atteso è `Success. No rows returned`. La migrazione è stata verificata localmente; non è stata applicata al progetto remoto da questa sessione, che non dispone di credenziali amministrative.

Non rieseguire le migrazioni 001–004 già applicate. La 005 usa una transazione e non ricrea tabelle, non elimina dati esistenti, non cambia codici o appartenenze. In caso di errore la transazione non applica parzialmente le modifiche: conservare il messaggio prima di riprovare.

Controllo facoltativo dopo Run:

```sql
select public.app_bootstrap();
select to_regprocedure('public.edit_group(uuid,text,text)'),
       to_regprocedure('public.cancel_group_invitation(uuid)');
```

Il bootstrap deve annunciare `0.31`/codice `6`; entrambe le funzioni devono esistere. Le mutazioni richiedono un utente autenticato, quindi non provarle senza identità nel SQL Editor.

Installare poi `app/build/outputs/apk/debug/WhereWeAre-v0.31-build6-debug.apk` sopra la build 5, senza disinstallare l'app. La firma debug resta la stessa. La build release compilata è unsigned e non sostituisce l'APK debug installabile.

Non servono nuovi secret o Edge Function per questa versione. Il trigger degli inviti usa `account_events`, già pubblicato in Realtime dalla migrazione 002. Firebase rimane da configurare secondo `SETUP_v0.3.md`: le notifiche a processo chiuso non sono verificabili senza il progetto Firebase.

## Verifiche su due telefoni

1. A crea un ritrovo: bengala immediato su A, poi su B alla sincronizzazione; nessun secondo bengala su A. Ripetere invertendo A/B. Il marker deve restare.
2. A invita B da Seleziona membri: A vede Invito inviato; B vede Invito ricevuto. Annullare da A: la richiesta sparisce anche su B. Ripetere accettando, poi con un nuovo invito rifiutando.
3. Modificare nome e icona: controllare B e Persona → Aggiungi al gruppo. Verificare anche con un membro già presente e un destinatario ancora in attesa.
4. Scatta foto → ritaglio → conferma; controllare impostazioni, riavvio e avatar su B. Ripetere con Scegli file e Rimuovi foto. Provare un upload senza rete: la bozza deve restare disponibile per il retry.
5. Durante la fotocamera ricreare l'Activity (opzione sviluppatore Non conservare attività): al ritorno deve riapparire il ritaglio nelle impostazioni. Controllare i tre pulsanti su schermo piccolo e caratteri ingranditi.

Questi controlli fisici sono ancora da eseguire: i test automatici del repository coprono dati, autorizzazioni e componenti Android locali, non due telefoni reali.
