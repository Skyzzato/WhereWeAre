# WhereWeAre - Troviamoci · v0.42

Android **0.42 (11)**. APK debug aggiornabile con la firma di test esistente.

- Impostazioni: «Chi mi vede adesso?» diventa una card coerente con Luoghi e
  regole, con spiegazione su autorizzazioni e consultazione della mappa, in IT/EN.
  Logout / Esci, Logout / Sign out, nome prodotto esatto e versione separata.
- Luoghi e regole: accesso ai gruppi modificabili con lo stesso editor già
  usato in Gruppi. Selettore icone sempre presente, rimozione realmente salvata,
  normalizzazione null/vuoto e successivo reinserimento sullo stesso gruppo.
- Posizione: testo numerico solo con precisione finita e non negativa;
  attesa prima del primo ACK, precisione non disponibile se manca il dato.
  La precisione mostrata riguarda il fix effettivamente pubblicato, non un
  aggiornamento locale ancora in coda. Nessuno zero inventato.
- SOS vicini: opt-in server inizialmente OFF, revoca persistente anche offline,
  disponibilità e fix con scadenza, ricerca server entro 2 km, massimo 20 inviti,
  limiti per mittente/destinatario, idempotenza e inviti deduplicati.
- Richiesta anonima con area indicativa prima di «Posso intervenire»;
  identità e posizione del mittente solo dopo consenso per quel SOS.
  Nessuna condivisione della posizione del volontario o relazione permanente.
- Controlli di accesso applicati anche alla coda push e all'inbox autenticata.
  Registrazione, accettazione FCM, visualizzazione e risposta restano distinti.

**Backend aggiornato realmente:** migrazioni 018/019 applicate al progetto
WhereWeAre, bootstrap 0.42/11 con `nearby_sos=true`, dispatcher distribuito.
Test transazionale remoto passato senza inviare notifiche o conservare fixture.

**Limite operativo:** le push FCM non sono ancora configurate. Manca un progetto
Firebase autorizzato, i parametri pubblici Android, i secret server e lo scheduler.
La funzione è testabile tramite la inbox con l'app aperta; non è certificata
la ricezione in background. Non usare questa release come canale di soccorso affidabile.

Istruzioni, parametri e test su due account: [SETUP_v0.42.md](SETUP_v0.42.md).
Verifiche e dati APK: [VERIFICATION_v0.42.md](VERIFICATION_v0.42.md).
Decisioni e precedente blocco conservato: [ADR_SOS.md](ADR_SOS.md).
