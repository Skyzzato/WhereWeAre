# WhereWeAre v0.43

Android 0.43 / versionCode 12, com.whereweare.app. APK debug di test.

## Correzioni

- SOS: invio gestito dal repository di applicazione, ID e selezioni persistenti per account prima della richiesta; verifica server dopo errori e al riavvio. Stato confermato solo dopo ACK o verifica positiva. Rifiuto certo distinto da esito incerto, scheda persistente ambra/rossa con verifica e chiusura esplicita. Doppio tap escluso; retry esplicito con stesso ID, nessun reinvio automatico. Oltre 5 minuti è consentita solo la verifica, poi una nuova richiesta consapevole.
- Adesione vicini: consenso permanente già presente nel database, ora spiegato separatamente dalla disponibilità. Aggiornamento puntuale ogni 5 minuti in primo piano, con rete e permessi; in background vengono riutilizzati soltanto fix della condivisione già attiva. Consenso e revoca pendente restano per account. Logout interrompe la disponibilità della sessione senza revocare il consenso.
- Luoghi: aggiunta senza limite di quattro slot, nome univoco normalizzato per account, icona selezionabile/rimovibile/riselezionabile. Modifica delle regole esistenti, rimozione con nome e conseguenze; cancellazione atomica di regole e avvisi ancora in coda associati al luogo. Nessuna automazione attivata creando un luogo.
- Connessione: ripristino sessione atteso prima delle RPC; timeout trattati come errori temporanei e non come cancellazioni definitive dei flussi. Refresh su ritorno in primo piano e cambio rete, backoff con jitter, conservazione dei dati dello stesso account, REST e realtime distinti. Diagnostica con operazione, durata, categoria e correlazione, senza token o coordinate.
- Avvio: inizializzazione Firebase protetta dalla doppia inizializzazione. Un timeout non lascia più le azioni bloccate in corso.
- Italiano/inglese conservati; versione e splash derivati da BuildConfig. Dicitura: WhereWeAre - Troviamoci.

## Evidenze e limiti

Il vecchio messaggio SOS veniva generato da qualsiasi eccezione di sendSos, comprese autorizzazione, cooldown, risposta persa o decodifica. Il solo testo non provava la mancata registrazione. L’ID era limitato alla schermata e resetSos cancellava l’esito alla riapertura. Il consenso server non aveva scadenza; il client aggiornava la disponibilità solo su richiesta manuale. Icona assente da DTO, editor e tabella dei luoghi; UI e constraint limitati a quattro slot.

I test hanno riprodotto la doppia inizializzazione Firebase. Il codice dei flussi trattava TimeoutCancellationException come chiusura definitiva; ora distingue i timeout dalla cancellazione di lifecycle. Non è stata ricostruita una causa unica per ogni disconnessione osservata sul telefono: mancano i log di quelle sessioni. Il rinnovo token resta coordinato dall’SDK Auth condiviso, senza un secondo refresh manuale concorrente.

Migrazione 020 applicata al progetto verificato; test SQL remoti con ruolo authenticated e rollback superati. Bootstrap HTTP 200: 0.43/12, minimo client 4. Nessun utente di prova persistito. Questo non equivale a una prova di login/refresh JWT da telefono o di consegna push.

Verifiche: 101 test Android PASS, lint senza errori, suite SQL locale/remota e 3 test dispatcher PASS.

Dettagli: [verifiche](VERIFICATION_v0.43.md), [server e parametri](SETUP_v0.43.md). FCM server/scheduler ancora da completare come documentato nella v0.42. Nessun test fisico multiutente, cambio Wi-Fi/dati, Doze o ricezione FCM dichiarato.

APK: WhereWeAre-v0.43-debug.apk nella release GitHub v0.43; applicationId invariato, firma debug verificata uguale all’APK locale v0.42. Non disinstallare l’app per aggiornare.
