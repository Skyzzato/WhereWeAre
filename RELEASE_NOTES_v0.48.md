# WhereWeAre v0.48 — pre-release

Android **0.48**, **versionCode 17**. Nome, applicationId e certificato di firma debug storico invariati.

- Luoghi e gruppi distinti: rimosse le card dei gruppi da Luoghi e regole; Modifica luogo apre il luogo e conserva nome, icona, coordinate e raggio. Nessuna conversione o cancellazione di gruppi, nessuna migrazione server.
- Scegli sulla mappa disponibile in aggiunta e modifica: cartografia navigabile con mirino centrale, conferma delle sole coordinate e annullamento senza modifiche al modulo. Inserimento manuale sempre disponibile anche senza GPS.
- Informazione accessibile sul raggio in metri, senza modifiche agli algoritmi delle regole.
- Centra sulla mappa apre la cartografia principale, rende visibile un luogo nascosto e interrompe il seguito della propria posizione. Occhio persistente per utente/luogo, visibile per default.
- Marker con icona trasparente, senza contenitore avatar o PIN aggiuntivo; dimensione dedicata in Aspetto (24/36/52/72), indipendente dagli avatar, con area di tocco minima 48 dp. Per luoghi senza icona resta un simbolo luogo di riserva.
- Aggiungi regola senza sottotitolo; ID del luogo, destinatari e condizioni conservati.
- Default acquisizione posizione **5 secondi (5.000 ms)** in assenza di intervallo salvato. Tutti gli intervalli già salvati sono conservati, inclusi eventuali vecchi default indistinguibili da scelte manuali. Il vecchio default remoto non sostituisce quello nuovo del client. Nessun nuovo timer o avvio automatico della condivisione; cambi di aspetto non riavviano il GPS.
- Bengala, check-in e menu: sfondo verde dello stesso token di Avvia condivisione, simboli neri, nei sei temi.
- Check-in: spunta a V già presente (CheckCircle), conservata sulle coordinate registrate nell’evento, senza seguire la persona; invariati Cronologia eventi, destinatari, scadenze e cancellazione.
- Traduzioni IT/EN aggiornate; funzionalità v0.47 conservate.

Verifiche e limiti: [VERIFICATION_v0.48.md](VERIFICATION_v0.48.md). Nessun dispositivo ADB disponibile: cadenza reale, GPU, background/schermo spento e sessioni con due account non misurati. La richiesta di 5 secondi non è una garanzia di aggiornamenti effettivi ogni 5 secondi.

APK: [WhereWeAre-v0.48.apk](https://github.com/Skyzzato/WhereWeAre/releases/download/v0.48/WhereWeAre-v0.48.apk). Commit, firma, checksum e risultati analitici negli allegati BUILD_PROVENANCE.json, SHA256SUMS.txt e BUILD_REPORT.json.
