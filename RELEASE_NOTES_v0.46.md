# WhereWeAre v0.46

**Prerelease di collaudo — WhereWeAre - Troviamoci, Android 0.46 / versionCode 15.**

- Unica sezione «Aggiornamenti check-in» per check-in, luoghi e SOS; notifiche e collegamenti evento aprono lo stesso elenco. Identità, ordine cronologico e azioni SOS restano distinti.
- Bidone su ogni aggiornamento con conferma e rimozione personale persistente sul server. Nessuna cancellazione globale, annullamento SOS o revoca della condivisione altrui. Un SOS attivo nascosto resta accessibile dal pulsante SOS.
- Card visibili per registrazione in corso, registrazione confermata, fallimento certo ed esito incerto. Intento SOS persistente e ID stabile; una verifica non riuscita impedisce il reinvio. Chiudere un avviso incerto non dimentica il tentativo.
- Stato e pulsante condivisione coerenti durante verifica, avvio, invio sospeso e arresto. Preferenza per account; lo stato mai inizializzato si distingue dall’OFF già salvato, anche nelle versioni precedenti tramite revisione server. L’avvio richiede onboarding, permessi, localizzazione e app visibile; resta limitato ai destinatari autorizzati.
- Quota funzionale SOS disattivata nella configurazione server autorevole. Autenticazione, autorizzazioni, idempotenza, un solo SOS attivo e limiti tecnici dei destinatari rimangono.
- Ricezione SOS vicini preselezionata per profili senza preferenza, operativa soltanto dopo conferma e con posizione recente. OFF esistenti rispettati. Nessun rinnovo manuale: consenso persistente, acquisizioni automatiche consentite e controllo della freschezza effettiva.
- Rimossi i PIN generici dagli accessi Luoghi; conservate icone personalizzate e fallback singolo.
- Rinnovi sessione concorrenti serializzati: le richieste attendono il rinnovo in corso. Errori di schema/configurazione separati da sessione, autorizzazioni, rete e guasti transitori. Conservate la correzione dei destinatari JSON null della v0.45 e la stabilità della mappa.

La migrazione incrementale **021_v0_46.sql è applicata al progetto configurato**. Verifica remota: bootstrap 0.46/15, quota false, tabella rimozioni presente; prova transazionale con utenti sintetici superata e zero profili di prova residui. Non occorrono ulteriori interventi SQL su quel progetto; altre installazioni server devono applicare 001–021 in ordine, senza rieseguire migrazioni già applicate.

Build completa superata: **129 test JVM/Compose, zero fallimenti; lint zero errori e 66 warning**. Verifiche e limiti sono descritti in [VERIFICATION_v0.46.md](VERIFICATION_v0.46.md). Le prove SQL remote usano ruoli autenticati simulati e rollback: non equivalgono a login reali o invio/ricezione su due telefoni. Nessun dispositivo ADB disponibile. Configurazione FCM incompleta: nessuna consegna push reale dichiarata. La causa dell’errore intermittente sul dispositivo dell’utente non è attribuibile con certezza senza i relativi log; è stata corretta una condizione di concorrenza effettivamente presente nel codice.

Distribuito APK **debug firmato**, con la firma già usata per v0.45. SHA-256, commit, certificato e risultati di build sono negli allegati SHA256SUMS.txt e BUILD_PROVENANCE.json. Non è una release stabile.
