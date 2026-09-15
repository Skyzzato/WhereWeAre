# WhereWeAre — Troviamoci. · v0.4 in sviluppo

**Non è una release completata.** Stato locale del 16 settembre 2026.
Branch `codex/v0.4`, baseline `26fe0bc` (v0.33); versione Android ancora
`versionName=0.33`, `versionCode=8`, minSdk 26, target/compileSdk 37.
Nessun incremento a 0.4/9, tag di release, push GitHub o deploy backend eseguito.

## Risultato

Implementati i blocchi 1–12, SOS per destinatari autorizzati, onboarding e
consolidamento UX. Il rilevatore del blocco 13 è predisposto e disattivato.
Il blocco 14 è fermo: non è stato verificato uno scheduler server affidabile;
vedi [prerequisiti](BLOCKED_OVERDUE_ALERTS.md). La finalizzazione è quindi sospesa.

- Condivisione: recovery foreground/sessioni/ACK e diagnostica, senza nuovo
  tracking nascosto o cronologia automatica.
- Privacy: precisione server-side, nessun raw per destinatari approssimati,
  consensi sovrapposti e scadenza gruppi verificati dal server.
- Eventi: richieste posizione, Check-in, regole e SOS con inbox autorizzata,
  ID idempotenti e outbox/lease. Registrazione, accettazione push e lettura
  nell’app sono stati distinti.
- Bengala: tutti i 50 stili, avanzamento autorizzato e completamento prudente,
  animazione collaborativa originale.
- UI: quattro tab, QR, dettagli posizione, luoghi/regole, SOS separato,
  onboarding con inviti persistenti e Impostazioni a sezioni; 466 stringhe IT/EN.

## Migrazioni aggiunte

| Numero | Contenuto |
|---|---|
| 008 | Stato dispositivo opzionale |
| 009 | Precisione condivisa, protezione raw e audience |
| 010 | Richieste posizione |
| 011 | Check-in e infrastruttura eventi |
| 012 | Gruppi temporanei |
| 013 | Convergenza Bengala |
| 014 | Luoghi privati e regole consensuali |
| 015 | SOS, risposte, chiusura, accettazione push |
| 016 | Date finite per la scadenza gruppi |

001–007 sono invariati rispetto alla baseline. Le nuove migrazioni sono provate
localmente e vanno applicate in ordine; non sono state distribuite sul server.
[Setup incrementale](SETUP_v0.4.md).

## Dipendenze e permessi

Runtime aggiunti: ZXing core 3.5.4; CameraX camera2/lifecycle/view 1.6.2.
Test: Mockito 5.23.0 e kotlinx-coroutines-test 1.11.0.
Unico nuovo permesso Android: CAMERA, contestuale; hardware camera opzionale.
Nessun ACCESS_BACKGROUND_LOCATION, CALL_PHONE o Google Maps SDK aggiunto.
Foreground service e START_STICKY preservati. [Valutazione QR](ADR_QR.md).

## Routing e funzioni non operative

Valhalla scelto per il primo adapter, dopo confronto con GraphHopper e OSRM.
ROUTING_PROVIDER e ROUTING_ENDPOINT sono vuoti nella build verificata: niente
routing reale, endpoint demo o ETA inventate. Restano le distanze in linea d’aria.
[ADR routing](ADR_ROUTING.md).

“Sta arrivando” è disattivato anche dopo la sola configurazione di un endpoint:
servono integrazione operativa e collaudo. [ADR](ADR_ARRIVING.md).
SOS vicini è OFF/non disponibile, senza ricerca o coordinate di sconosciuti;
antiabuso/opt-in/discovery non verificati. Segnale sonoro locale opzionale non
implementato. [ADR SOS](ADR_SOS.md). Gli avvisi di mancato arrivo non sono attivi.
Le capability server abilitano soltanto i blocchi presenti; nessuna funzione
di sicurezza viene dichiarata ricevuta dal 112 o dai soccorsi.

## Verifiche

Build debug/release, 83 test Android/JVM/Robolectric e lint passati. SQL 001–016,
riapplicazione delle nuove migrazioni, regressioni di autorizzazione e hash
baseline passati. Tre test Deno: accesso dispatcher, payload e accettazione.
Nessun telefono/AVD operativo per questa verifica; non sono certificati UI reale,
GPS/OEM/background, scanner, notifiche FCM o routing sul territorio.
[Dettagli e checklist](VERIFICATION_v0.4.md).

## Commit

- `2afaa51` — v0.4 block 01 - baseline and migration safeguards
- `f369aee` — v0.4 block 02 - background sharing recovery and server state
- `247a3ef` — v0.4 block 03 - location and connection diagnostics
- `abe949e` — v0.4 block 04 - authorized device status and location details
- `9d49b26` — v0.4 block 05 - person and group QR
- `74b2dc7` — v0.4 block 06 - shared precision and visibility
- `b332352` — v0.4 block 07 - location requests
- `538980d` — v0.4 block 08 - check-ins and event infrastructure
- `a6cca0b` — v0.4 block 09 - temporary groups
- `fc8ee07` — v0.4 block 10 - evolved flare convergence
- `93088fa` — v0.4 block 11 - configurable routing and ETA
- `83c4e1f` — v0.4 block 12 - private places and consensual rules
- `bd15788` — v0.4 block 13 - gated experimental arrival detector
- `2977a33` — v0.4 block 14 - document required deadline infrastructure
- `285640d` — v0.4 block 15 - consented SOS with server acknowledgement
- `0ee7399` — v0.4 block 16 - onboarding preserving pending invites
- `4aa76ae` — v0.4 block 17 - consolidate settings and map actions

Segue il commit di verifica/documentazione finale dello sviluppo locale, non
un commit di rilascio. Lo SHA corrente è ottenibile con `git rev-parse HEAD` ed
è indicato nel resoconto della task. Nessun push è stato effettuato.
