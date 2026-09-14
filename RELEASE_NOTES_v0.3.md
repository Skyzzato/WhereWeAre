# WhereWeAre v0.3 — rapporto di sviluppo

La versione Android è `0.3`, codice numerico `5` dopo la correzione del crash di avvio della build 4. Il lavoro è nel branch locale `codex/v0.3`, a partire dal tag GitHub v0.2 con le modifiche v0.21 conservate. Non è stata pubblicata una release GitHub. L’utente ha applicato la migrazione 004 dal SQL Editor; la verifica successiva della RPC remota `app_bootstrap` conferma `api_version=3` e versione minima 4.

## Correzione avvio — build 5

Il `LocalContext` di Compose veniva sostituito con un contesto di configurazione derivato dall’Application. La factory Hilt cerca invece una ComponentActivity lungo la catena dei ContextWrapper: non trovandola, solleva IllegalStateException prima ancora di mostrare la schermata. Questo errore non emerge dai soli test delle funzioni di dominio o dalla compilazione.

La build 5 usa un ContextThemeWrapper con base Activity per le risorse della UI, conservando un contesto applicativo separato per il catalogo globale delle stringhe. Preserva così sia lingua sia accesso alla schermata e non trattiene l’Activity nel singleton Strings. I nuovi test Robolectric riproducono l’errore della vecchia factory e verificano le risorse italiano/inglese e la catena Activity della correzione. [Configurazione del framework di test](https://robolectric.org/getting-started/).

Nessuna nuova migrazione SQL è richiesta. La versione minima backend resta 4; la build 5 è compatibile con la migrazione già applicata.

## 1. Android

Follow continuo al cambio della propria posizione, disattivazione tramite gesto sulla mappa e stato grafico del pulsante. Viewport mappa ritagliato sotto i banner. Sei temi (Default, Ocean, Sunset, Lavender, Graphite, Dark), quattro dimensioni avatar (75/100/125/150%), bordo primary sottile e aree di tocco minime di 48 dp. I marker vicini sono raggruppati nello spazio dello schermo, con menu per scegliere una persona anche a coordinate identiche.

Persone: controllo di autorizzazione e disponibilità prima di aprire la mappa; in caso contrario compare un messaggio e si resta nella schermata. Gruppi: occhio locale indipendente dai permessi, condivisione personale nel gruppo, rinomina amministratore, richieste e inviti. Conservate foto/ritaglio, provider cartografici, cancellazione account e servizio GPS già sviluppati.

## 2. Supabase

Nuovo protocollo metadati v3. `app_metadata()` restituisce in una chiamata profilo, contatti, richieste, autorizzazioni, gruppi, membri e meeting; le posizioni rimangono una lettura separata protetta da RLS. Gli eventi GPS non ricaricano i metadati. Realtime usa le invalidazioni per account esistenti, senza payload sensibili.

## 3. Migrazioni SQL

Aggiunta `supabase/migrations/004_v0_3.sql`, incrementale e transazionale, dopo 001/002/003. Non ricrea dati esistenti. Mantiene i vecchi codici e le appartenenze; innalza la versione minima client a 4 per introdurre il nuovo flusso di approvazione dei gruppi. Il client v0.3 riconosce il backend non ancora aggiornato.

## 4. Tabelle e colonne

| Area | Aggiunte |
| --- | --- |
| Profili | update_interval_seconds |
| Membri | sharing_enabled |
| Richieste gruppi | group_requests, tipo join/invite, stato e attori |
| Ritrovi | meeting_points, meeting_recipients |
| Dispositivi | device_tokens con accesso attraverso RPC |
| Private | code_policy, invite_links, push_outbox, client_events, daily_usage |
| Configurazione | defaults e features in private.app_bootstrap |

`usage_summary` è una vista privata aggregata. Gli identificativi e i dati personali delle tabelle applicative mantengono cascata alla cancellazione account.

## 5. RPC ed Edge Functions

Nuove RPC principali: `app_metadata`, `contact_profiles_v03`, `set_update_interval`, `set_group_sharing`, `rename_group`, `group_inbox`, `invite_group_member`, `respond_group_request`, `create_meeting`, `remove_meeting`, `meeting_inbox`, `create_invite_link`, `resolve_invite_link`, `register_device`, `unregister_device`, `record_client_event`. `join_group` ora crea una richiesta. `claim_push_batch`/`complete_push` sono solo server.

Nuova Edge Function `send-meeting-push`: coda con lease e retry, OAuth service account e FCM HTTP v1, eliminazione token non registrati. La cancellazione account usa la Edge Function già esistente. FCM è configurabile, non attivo nella build senza valori Firebase.

## 6. RLS e autorizzazioni

Richieste gruppo leggibili da interessato/amministratore; risposta dell’admin per adesioni, del destinatario per inviti. Scritture tramite RPC con autenticazione e ownership. Solo il creatore elimina un meeting; destinatari autorizzati possono leggerlo. Ricevere un meeting non permette di leggere GPS altrimenti vietato.

Le posizioni richiedono sharing attivo, sessione valida, timeout del proprietario e almeno una relazione abilitata o un gruppo con condivisione del proprietario abilitata. Nascondere un gruppo localmente non cambia questi diritti. Tabelle private e funzioni push non sono accessibili con anon/authenticated; i token non sono enumerabili dal client.

## 7. Localizzazioni

Risorse complete in `values` inglese e `values-it` italiano, incluse schermate, errori, ritaglio e notifiche. Sistema italiano → italiano; altra lingua di sistema → inglese. Override persistente Sistema/Italiano/English. Contesto risorse aggiornato in Compose e nei worker; notifica GPS usa la preferenza anche dopo riavvio del processo. Disabilitato lo split per lingua degli App Bundle per rendere disponibile l’override offline.

## 8. GPS precedente e nuovo

La v0.21 già disponeva di flusso continuo per intervalli brevi e acquisizioni singole per quelli lunghi. La v0.3 aggiunge **600 secondi** alla stessa architettura: non avvia un secondo flusso GPS. Alta precisione è il default soltanto in assenza di preferenza esplicita. Ogni fix valido aggiorna immediatamente stato/avatar locale, poi viene pubblicato.

Per intervalli ≥300 s, richiesta singola con timeout 30 s e provider rilasciato fra acquisizioni. Il periodo tiene conto della durata dell’acquisizione; senza fix viene riprovato dopo 30 s. Android, rete e disponibilità GPS possono ritardare pubblicazione e risveglio: la frequenza reale deve essere misurata su dispositivo, senza presentarla come garantita.

## 9. Barra dati remoti

Prima: `Snapshot.offline` diventava true per websocket non sottoscritto oppure errore di lettura. Non confrontava timestamp delle posizioni; una normale riconnessione poteva apparire come dati vecchi.

Ora: **Connessione assente** dipende dalla connettività Internet validata di Android. **Posizioni non aggiornate** confronta recorded_at con il tempo server stimato e l’intervallo dichiarato dal proprietario più stale_grace_seconds (default 180, limite client 30–3600). Un fix vecchio 10 minuti non è segnalato come obsoleto se il periodo scelto è 10 minuti. Gli errori di sincronizzazione HTTP hanno un messaggio distinto; la sola transizione del websocket non è trattata come posizione vecchia.

## 10. Flicker avatar e UI immediata

La precedente invalidazione dei permessi svuotava la mappa dei contatti, azzerando temporaneamente avatar_path e facendo comparire l’iniziale. Ora invalida subito solo le posizioni remote: profili/foto rimangono stabili durante la rilettura delle autorizzazioni.

`OptimisticSnapshots` applica subito permessi, rimozioni, rinomina e risposte; in caso di errore elimina la modifica temporanea e mostra l’errore. Le modifiche vengono ritirate soltanto contro il nuovo snapshot server, senza passaggio intermedio allo stato precedente. L’account è parte della verifica, evitando di applicare modifiche di una sessione a un’altra. La cache immagini e le chiavi avatar id/path sono conservate.

## 11. Errore nella pagina Gruppi

Il fallimento di ricerca gruppo usa `group_not_found`, mappato prima del generico `not_found`, con stringhe **Gruppo non trovato / Group not found**. La ricerca personale mantiene il messaggio specifico per la persona.

## 12. Codici e inviti

Generazione server crittografica, alfabeto senza caratteri ambigui e rejection sampling, sei caratteri mostrati 3-3. Indici univoci normalizzati e retry sulle collisioni. Lookup case insensitive, compatibilità 4-4, limite condiviso 60 tentativi/ora per utente, inclusi codici inesistenti.

Codice persona → richiesta personale e consenso esistente. Codice gruppo → richiesta all’amministratore. Aggiunta a un gruppo → invito da accettare. Le combinazioni ripetute gruppo/destinatario pending vengono deduplicate. Link lunghi separati dai codici brevi, token hash e scadenza; dominio futuro non configurato.

## 13. Punto di ritrovo

FAB bandiera, mirino al centro e trascinamento mappa, scelta Tutti/persone/gruppi. Backend deduplica i destinatari e include il creatore; un meeting attivo per creatore. Il meeting persiste nel database finché viene sostituito o rimosso. Marker e linee animate collegano soltanto partecipanti di cui il viewer vede già legittimamente la posizione.

Banner in-app con nome creatore e apertura della mappa; rimozione notificata. FCM data-only in background con rilettura autenticata e deep link interno. Meeting non accessibili non vengono mostrati da un payload esterno. Deduplicazione persistente di notifiche e animazione.

## 14. Bengala

Disegno nativo Compose Canvas, Animatable e tween di 1300 ms: salita del punto e apertura di dieci raggi, usando il primary del tema. Nessun video o immagine scaricata. L’overlay non gestisce input e non blocca la mappa. ID evento persistito in DataStore prima dell’animazione per evitare ripetizioni da refresh e ricomposizione.

## 15. Analytics

Architettura privata per futura dashboard: conteggi utenti, sharing, gruppi, membri, relazioni, richieste, meeting, utenti con fix recenti, aggiornamenti aggregati per giorno e push in coda. `daily_usage` non contiene ID utente né coordinate.

Eventi client disattivati per default; integrazione tipizzata delle preferenze. Il server filtra chiavi **e valori** con enumerazioni, limita la versione e la frequenza. Nessuna coordinata o testo arbitrario degli errori nelle analytics. Errori backend da osservare tramite log server sanitizzati; dashboard e politica di retention sono configurazioni future, non servizi già pubblicati.

## 16. Remote config

Separati preferenze utente DataStore, defaults globali, feature flags e parametri di sicurezza server. Un nuovo default non sovrascrive la scelta già salvata. Conservati blocco versione, messaggio manutenzione e cache bootstrap con comportamento prudente alla prima connessione. I flag client non sostituiscono RLS e ownership.

## 17. Test

Risultati e limiti aggiornati in [VERIFICATION.md](VERIFICATION.md). Suite SQL locale con regressioni v0.2, richieste/consenso, autorizzazioni GPS, deduplica meeting/push, collisione forzata dei codici e isolamento dei metadati. JVM include iniziali, lingue, soglia 10 minuti, cluster, migrazione backend e successo/rollback della UI immediata. Build Android e lint eseguiti.

Non è disponibile un dispositivo/AVD: gesture, rendering effettivo, consumo batteria, intervallo GPS reale e consegna push richiedono collaudo fisico. Non confondere i test live della precedente v0.21 con verifiche live della v0.3.

## 18. Configurazioni manuali

Applicare migration 004 e distribuire APK v0.3 insieme; creare Firebase, configurare i quattro valori pubblici Android, secret server e scheduler push. In futuro configurare dominio/assetlinks, analytics e retention. Tutti i passaggi sono in [SETUP_v0.3.md](SETUP_v0.3.md). Nessun segreto Firebase è stato inventato o inserito nel client.
