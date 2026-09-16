# Versione corrente v0.45 — prerelease di collaudo

Note attuali: [RELEASE_NOTES_v0.45.md](RELEASE_NOTES_v0.45.md). Verifiche: [VERIFICATION_v0.45.md](VERIFICATION_v0.45.md). Audit precedente: [AUDIT_v0.44.md](AUDIT_v0.44.md). Configurazione e limiti: [SETUP_v0.44.md](SETUP_v0.44.md).

La sezione seguente conserva la ricostruzione storica.

# WhereWeAre — evoluzione fino alla v0.33

Questa pubblicazione conserva il codice locale della v0.33, senza sviluppo della v0.4. I commit delle tappe recuperate vengono creati durante il recupero: non sono commit originali datati retroattivamente. Provenienza e limiti: [REPOSITORY_RECOVERY.md](REPOSITORY_RECOVERY.md).

| Versione | Stato della ricostruzione | Evoluzione documentata |
| --- | --- | --- |
| v0.2 | Commit originale `b7a3212`, tag già pubblicato | Posizione, gruppi, avatar, bootstrap e controlli di accesso. |
| v0.21 | Albero Git recuperato, Android 0.21/3 | Splash proporzionato, rimozione avatar, coordinate decimali/OpenStreetMap, mappe alternative, codice personale, Account e Impostazioni. Recupero password solo predisposto. |
| v0.22 | Solo retrospettiva; nessun albero autonomo identificato | Refresh remoto, avatar/bordi, codici a sei caratteri, richieste e inviti gruppo, statistiche, italiano/inglese e selezione lingua. Questi contenuti sono riscontrabili nello snapshot v0.3; la loro esatta separazione temporale non è ricostruibile. |
| v0.3 | Albero Git recuperato, Android 0.3/5 | Bengala e punto di ritrovo come una funzione, destinatari deduplicati, metadata e predisposizione push; include gli sviluppi attribuiti alla v0.22. Recuperata la build 5 con correzione del contesto Activity/Hilt, non la build 4 precedente. |
| v0.31 | Albero Git recuperato, Android 0.31/6 | Animazione Bengala, modifica nome/icona gruppo, ergonomia membri e codice, copie/condivisione, gestione bozza avatar e inviti. |
| v0.32 | Albero Git recuperato, Android 0.32/7 | Trenta stili Bengala, audio locale/notifica, correzioni avatar, inviti e stato condivisione. Selezionata la variante con correzioni audio. |
| v0.33 | Codice locale finale, Android 0.33/8 | Ricerca Persone/Gruppi, salvataggio di persone conosciute nei gruppi senza consenso GPS implicito, card compatte e Centra, errori posizione/rete, inviti tramite codice, razzi (50 stili), stelline e punto di ritrovo. |

I report dettagliati `RELEASE_NOTES_v0.21.md`, `RELEASE_NOTES_v0.3.md`, `RELEASE_NOTES_v0.31.md`, `RELEASE_NOTES_v0.32.md` e le sezioni storiche di `VERIFICATION.md` sono conservati. Le loro dichiarazioni su branch, push, stato remoto e test descrivono il momento della redazione, non certificano lo stato attuale dei servizi.

## Migrazioni server

Le migrazioni già pubblicate 001–003 sono immutate. Le quattro aggiunte sono conservate integralmente e si applicano in ordine lessicografico:

| File | Contenuto |
| --- | --- |
| `004_v0_3.sql` | Codici brevi, ingressi/inviti gruppi, metadata, meeting/Bengala, coda push privata e statistiche. Comprende anche evoluzioni attribuite retrospettivamente alla v0.22. |
| `005_v0_31.sql` | Modifica gruppo, annullamento inviti e notifiche metadata. |
| `006_v0_32.sql` | Stili Bengala 1–30. Il bootstrap storico dichiara code 6, mentre lo snapshot Android finale è code 7: incongruenza preesistente conservata. |
| `007_v0_33.sql` | Contatti salvati con RLS del proprietario, consenso GPS separato, stili 1–50 e idempotenza; bootstrap finale 0.33/8, minimo client 4. |

La sequenza 001–007 ricostruisce lo schema locale richiesto e supera le suite di sicurezza esistenti; la 007 viene testata anche ripetuta. Non sono state accorpate o riscritte migrazioni. Nessuna migrazione è stata applicata al server remoto durante questa pubblicazione Git.

## Limiti della v0.33

- Firebase deve essere configurato per ricevere Bengala a processo chiuso; la condivisione posizione usa il servizio foreground esistente.
- Schermo spento, Doze, audio fisico, fotocamera e flussi fra telefoni richiedono collaudo su dispositivo. I test automatici non certificano questi comportamenti.
- Gli inviti condivisi usano codici senza hosting; HTTPS/App Links richiedono dominio e associazione pubblicati. Il template web rimane locale.
- Verificare che il server di destinazione abbia ricevuto la 007 prima di usare le funzioni che la richiedono; lo stato remoto non viene dedotto dal push Git.
- La build release richiede una firma di distribuzione; APK e configurazioni locali sono esclusi dal repository.
