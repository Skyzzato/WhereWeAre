# WhereWeAre — Troviamoci. · v0.41

Android `versionName=0.41`, `versionCode=10`, dalla baseline v0.4/9
(commit `6339664`). APK debug con la firma debug esistente. Nessuna nuova
libreria, modifica dell’orientamento o migrazione SQL.

## Modifiche

- SOS: selettore ampio con schede Persone/Gruppi, ricerca, avatar, icone,
  checkbox, riepilogo e conferma/annullamento. Selezioni conservate tra schede;
  gruppi scaduti o senza altri membri non selezionabili. Nessuna preselezione.
  Restano conferma della posizione, countdown annullabile, idempotenza e
  limiti del server. Verificata la deduplicazione persona + gruppo e outbox.
- Mappa: bandierina per avviare il flusso esistente del punto di ritrovo/Bengala,
  spunta per Check-in, mirino con tooltip e descrizione accessibile in basso
  a destra. Posizioni vecchie segnalate; nessun cambio implicito del GPS.
- Check-in: si continuano a usare gli snapshot persistiti dell’inbox autorizzata,
  con durata 24 ore, precisione e revoche esistenti. Raggruppamento dei marker
  vicini sullo schermo con scelta dei singoli eventi; refresh dopo ACK protetto
  dalle letture concorrenti e polling di recupero anche per le capability eventi.
  Il menu è «Aggiornamenti check-in»; SOS e Luoghi restano nello stesso elenco,
  riconoscibili tramite i rispettivi testi e dettagli.
- Richieste posizione: la v0.4 filtrava `purpose=location` dai metadata legacy e
  mostrava solo le ricevute pendenti in un elenco separato. V0.41 legge la tabella
  `share_requests` con la sessione autenticata e la RLS esistente durante il
  refresh dei metadata. Purpose e data passano nel modello: inviate/ricevute,
  accettate/rifiutate/annullate e pendenti scadute sono distinguibili. Nessun
  secondo archivio. «Accettata» non implica posizione ricevuta. Le cancellazioni
  già registrate dal vecchio server restano cancellazioni: non si inventa se
  fossero scadenze automatiche o azioni esplicite.
- Bengala: default persistente non impostato e fallback a ID **46 = Razzo 46**,
  reso dal preset con ID 46 (indice 45). Gli ID salvati validi, incluso 1,
  rimangono invariati anche riaprendo l’app. Tutti i 50 stili restano disponibili.
- Gruppi: selettore icone condiviso da creazione/modifica più ampio, colonne
  adattive, celle di almeno 48 dp, scorrimento su schermi piccoli/testo grande.
  Formatter data/ora neutro, anno a quattro cifre e fuso locale, etichette
  separate per creazione e scadenza in IT/EN.
- Impostazioni: Profilo con avatar e nome sulla stessa riga, dischetto attivo
  solo per modifiche valide, blocco invii duplicati e feedback dopo risposta
  remota. Codice personale dentro Profilo; funzioni avatar, QR/copia/condivisione
  conservate. Poi Condivisione e posizione (inclusa informazione SOS), card
  Luoghi e regole, Aspetto (tema, mappa, avatar, stile/suono Bengala, lingua),
  Account, Diagnostica, Versione, Privacy/informazioni/copyright.
  Luoghi accessibile anche dal menu Mappa usando la stessa schermata.
  Licenze e attribuzioni consolidate, comprese CameraX/ZXing; attribuzioni
  cartografiche sulla mappa conservate.
- Informazioni contestuali uniformi con «i» cerchiata per precisione condivisa,
  soglia accuratezza GPS in metri e frequenza reale di acquisizione/pubblicazione.
  Le spiegazioni non modificano impostazioni o autorizzazioni.
- Stellina gialla con tratto nero di 1 dp, dentro l’angolo superiore destro,
  proporzionata all’avatar. La mappa usa lo stesso componente Compose, senza
  una seconda bitmap dei marker da correggere.
- «Chi mi vede adesso?»: riepilogo condivisione, avatar, origini e precisione,
  timestamp in fondo riferito all’ultima RPC riuscita. Gli errori distinguono
  indisponibilità da dati precedentemente verificati. Il server deduplica
  le persone mantenendo tutte le origini. Nessuna inferenza su chi guarda.
- Card avatar: icone e blocchi posizione/dispositivo, movimento solo se presente
  e preciso, link cartografici conservati. Timestamp dispositivo in fondo,
  distinto dalla misura. Nessun campo altitudine inventato.
- L’avviso periodico sopra Interrompi derivava da `pendingUpload`, impostato
  a ogni fix e rimosso all’ACK, non da un’operazione utente Busy. Gli invii
  ordinari non mostrano più quell’avviso; errori/ritardi significativi e stop
  mantengono uno spazio stabile e leggibile. Diagnostica trasporto invariata.

## Server e SOS di prossimità

Controllo remoto in sola lettura del 16/09/2026: `app_bootstrap` HTTP 200,
versione **0.4/9**, capability `sos`, `checkins`, `shared_precision`,
`location_requests`, `temporary_groups`, `flare_convergence`, `places_rules`,
`device_status` e `meeting_points` true; **`nearby_sos=false`**.
Questa osservazione aggiorna il vecchio report 0.33/8 senza riscriverlo.

Android mantiene `NEARBY_SOS_ENABLED=false`. La 015 mantiene la capability false;
API e dispatcher implementano destinatari autorizzati, non un servizio di
prossimità. Mancano registro di adesione esplicita/revocabile, disponibilità
recente, ricerca server non enumerabile, protezioni antiabuso e protocollo di
consenso per identità/precisione successivi. La UI lo spiega senza un interruttore
apparentemente utilizzabile. Non sono stati iscritti o contattati sconosciuti.

Non sono state applicate migrazioni, distribuite funzioni o modificate
configurazioni remote. Il bootstrap non certifica dispatcher FCM, secret,
scheduler o consegne. Nessun accesso amministrativo al database è stato usato.
La versione dell’APK non aggiorna automaticamente la versione bootstrap.

Controllo della configurazione di questa build: i quattro campi Firebase client
non sono tutti presenti; `WhereWeAreApplication` quindi non inizializza Firebase.
**Questo APK non è configurato per ricevere push FCM.** Refresh/realtime nell’app
restano disponibili. Anche routing provider/endpoint non sono configurati.
Nessuna credenziale o servizio esterno è stato inventato per aggirare il limite.

## Verifiche e consegna

Risultati finali, comandi, hash e limiti in [VERIFICATION_v0.41.md](VERIFICATION_v0.41.md).
APK e checksum sono artefatti esterni al repository sorgente; nessun keystore,
segreto o configurazione locale è incluso nei commit.

Restano necessarie prove su due telefoni con account reali: invio/ricezione,
FCM, notifiche negate, background/processo terminato, rete intermittente,
consenso e revoche durante l’uso, riapertura e cambio stile della mappa,
accessibilità e sovrapposizioni con cartografia reale. Nessuna consegna garantita.
