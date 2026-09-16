# WhereWeAre v0.45 — correzione risposta server

Corretto l'errore «La risposta del server non è leggibile» che interrompeva la sincronizzazione e mostrava il banner rosso sopra la mappa, anche con server raggiungibile.

La risposta `app_metadata` può contenere eventi con `recipients: null`: PostgreSQL restituisce null da `jsonb_agg` quando non ci sono destinatari ordinari, per esempio con un SOS solo nelle vicinanze. Il modello Android richiedeva una lista e rifiutava l'intera risposta. La v0.45 interpreta solo questo campo null come lista vuota; gli altri dati obbligatori restano validati. Il controllo sul progetto Supabase ha confermato la presenza di questo formato.

APK debug aggiornabile dalla v0.44: versionName 0.45, versionCode 14, stessa firma. Nessuna migrazione SQL richiesta e nessuna modifica ai dati del server. Commit e checksum sono negli allegati della prerelease.

Test di regressione con JSON prodotto dalle migrazioni 001–020: fallimento riprodotto prima della correzione, lettura riuscita dopo. Verificati destinatari presenti, assenti e null, rifiuto dei dati malformati e recupero della diagnostica. Risultati completi in [VERIFICATION_v0.45.md](VERIFICATION_v0.45.md).

Prerelease: nessun telefono collegato per verificare l'installazione e il comportamento sul dispositivo dell'utente. Restano i limiti già documentati nella v0.44, inclusa la configurazione push FCM incompleta.
