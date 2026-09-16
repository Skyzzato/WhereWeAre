# WhereWeAre - Troviamoci · v0.44 (13)

**Prerelease di collaudo. Non ancora certificata come versione stabilizzata su telefoni.** APK debug installabile con la firma precedente; package com.whereweare.app, Android 8 o successivo.

- SOS e Luoghi rimangono raggiungibili anche se il caricamento fallisce. L'invio SOS richiede comunque disponibilità confermata, destinatari e consenso; nuovo accesso “Aggiornamenti SOS”, mantenendo “Aggiornamenti check-in”.
- Metadata pubblicati senza attendere le letture di richieste/posizioni. SOS ordinario indipendente dalla disponibilità della ricerca vicini.
- Errori rete, sessione, autorizzazione e risposta non leggibile distinti. “Riconnessione” soltanto durante tentativi effettivi, con errore persistente e refresh nelle schermate dati.
- Retry del servizio di condivisione con backoff per guasti transitori e arresto su errori permanenti; frequenza GPS normale invariata. Recupero sessione limitato, senza retry automatici degli SOS.
- Aggiornamenti di stato sharing attivo non cancellano più tutti i marker. Revoche e stop continuano a invalidare i dati; opzioni della mappa stabili. Sfarfallio grafico sul telefono ancora da riprodurre.

Verifiche: suite Android/Robolectric e SQL, test di interazione SOS, prove SQL remote con mittente/destinatario/estraneo e rollback, controlli firma e package. Conteggi finali, SHA-256 e commit negli allegati della release.

**Limiti confermati:** nessun dispositivo ADB/AVD disponibile; manca collaudo multiutente tramite login reali e installazione di aggiornamento. Push FCM non operative: nessun custom secret server né scheduler Cron. Routing ETA e dominio inviti HTTPS non configurati. Nessuna chiamata al 112 né SOS inviato a utenti reali durante i test.

Ramo `codex/v0.44`, discendente diretto di v0.43; `main` rimane alla v0.4, senza merge o force-push. Nessuna migrazione distribuita o riscritta in questa release.

[Matrice e cause](AUDIT_v0.44.md) · [Verifiche e scenari](VERIFICATION_v0.44.md) · [Server e configurazione](SETUP_v0.44.md)
