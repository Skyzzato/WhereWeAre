# Verifiche v0.46

Baseline reale: ramo `codex/v0.45`, commit `6d28b36`, upstream `origin/codex/v0.45`, albero pulito e fetch effettuato. Versione 0.45/14 portata a 0.46/15. Nessun reset, force push o cambio arbitrario di ramo.

## Verifiche reali sul server

Migrazione 021 applicata dal SQL Editor il 16/09/2026: successo. Prima: bootstrap 0.43/12 e tabella rimozioni assente. Dopo: 0.46/15, quota `false`, tabella presente. Test `v046.sql` eseguito sul server con tre profili sintetici in un’unica transazione con rollback: PASS; controllo successivo, zero profili `f0460000-*` residui. Nessun token dispositivo o chiamata al dispatcher; nessun SOS inviato a estranei.

La prova verifica rimozione del destinatario e mittente, ricaricamento tramite metadata, isolamento dell’estraneo, mantenimento dell’SOS attivo e della sua azione di chiusura, soppressione del push pendente, deduplicazione, otto invii aggiuntivi oltre la vecchia quota, riattivazione della quota, consenso persistente, OFF salvato, revoca e freschezza distinta dalla vecchia scadenza manuale. Si tratta di ruoli SQL autenticati simulati, non sessioni Auth/PostgREST di due utenti reali.

## Verifiche automatizzate locali

- `node supabase/tests/run-v044.mjs`: regressioni storiche 001–020 e suite v0.4 superate sul loro schema storico. La suite è ora ancorata a 020, così non ripropone quota/default vecchi sul nuovo schema.
- `node supabase/tests/run-v046.mjs`: migrazioni 001–021 e dodici suite superate, comprese condivisione, revoche, precisione, richieste posizione, check-in, gruppi temporanei, Bengala, luoghi e test v046.
- Android: `gradlew.bat -I tools/isolated-v044-build.gradle :app:build`: **PASS, 129 test JVM/Compose, zero fallimenti/errori/skipped; lint zero errori, 69 warning nel report XML finale**. Risultati riportati anche in BUILD_REPORT.json allegato alla prerelease.
- Test mirati: quattro stati della card SOS, bidone accessibile su SOS ricevuto con conferma separata, deduplicazione e ordine, verifica iniziale condivisione e rispetto OFF, distinzione errori schema, rinnovo concorrente sessione, timeout persistente e impossibilità di reinvio dopo verifica fallita anche chiudendo l’avviso.
- `node tools/verify-v033-migrations.mjs`: PASS, migrazioni 001–007 inalterate rispetto alla baseline recuperata. Ispezionato anche il rendering Robolectric italiano della card incerta con font 160%: titolo, testo e azioni leggibili senza tagli.

La prima build in sandbox non poteva utilizzare correttamente SDK/licenze del profilo proprietario. Build successive eseguite con il profilo configurato; nessuna nuova licenza accettata. Un passaggio lint durante modifiche ai sorgenti ha avuto un errore interno Kotlin; non è stato disabilitato lint e viene rieseguito sui sorgenti finalizzati.

## Copertura e limiti

| Criterio | Prova disponibile | Limite |
|---|---|---|
| Elenco unificato | Test dominio e Compose; SQL locale/remoto | Nessun telefono |
| Bidone e persistenza | Compose simulato; dati server, metadata e isolamento SQL reali | Nessun test UI tra due dispositivi |
| Esito SOS | Quattro card Compose; timeout/retry e stato persistente con mock | Rete mobile reale non simulata dal test |
| Condivisione | Stato/default/OFF e controller JVM con rete/permessi simulati; regressioni SQL | Installazione pulita, aggiornamento e riavvio su telefono non eseguiti |
| Quota SOS | Invii sintetici reali nel database, rollback | Nessun destinatario fisico |
| Vicinanze | Consenso/revoca/freschezza e vecchia scadenza SQL locale/remoto | GPS/background Android reale non verificato |
| Luoghi | Regressioni SQL delle icone e rendering esistente; revisione UI | Nessun test manuale su telefono |
| Server/mappa | Rinnovo concorrente, recupero diagnostica, revoca marker e retry JVM | Lampeggiamento e riconnessione su telefono non verificati |

ADB ha restituito un elenco vuoto: nessuna installazione fisica o prova end-to-end con due telefoni. FCM non completamente configurato. Firma e metadati APK sono verificati con gli strumenti SDK; questo dimostra un APK firmato installabile, non un’installazione eseguita.

## Problema server individuato

`AuthRepository.recoverSession` usava `tryLock` e tornava immediatamente quando un rinnovo era già in corso: un chiamante poteva ripetere la lettura con il vecchio token. Ora attende il rinnovo e riutilizza la nuova sessione. Test concorrente deterministico. Errori RPC 404 o codici schema noti sono classificati come configurazione, senza nascondere guasti o dichiarare connessione sana. I log conservano operazione, correlazione, durata e categoria, senza token o coordinate.

Conservata la correzione v0.45 dei destinatari null e i test del contratto server. Non sono disponibili i log del telefono dell’utente per attribuire ogni precedente avviso generico a questa condizione di concorrenza. Non viene dichiarata una riproduzione end-to-end dell’errore intermittente.
