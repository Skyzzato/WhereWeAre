# Verifica v0.49 — Android 0.49 / 18

Data: 13 ottobre 2026. Prerelease di collaudo, non validazione su telefoni reali.
Il commit della build è identificato dal tag immutabile `v0.49` e dall'allegato
`BUILD_PROVENANCE.json`. Il resoconto dell'attivazione live viene aggiunto su main
dopo la pubblicazione, senza spostare il tag.

## Resoconto successivo alla pubblicazione

Prerelease pubblicata il **13/10/2026 alle 14:52:26 UTC**:
[v0.49](https://github.com/Skyzzato/WhereWeAre/releases/tag/v0.49),
[APK](https://github.com/Skyzzato/WhereWeAre/releases/download/v0.49/WhereWeAre-v0.49.apk).
Commit/tag APK: `c2a76bec200ed704cb89b7eb52ba9a9c4377c357` / `v0.49`;
albero `032ad43d75e1c6fc53b448ac1200f1ae0bb0e5cc`. Artefatto ricompilato da zero
dopo il commit, 42 task eseguiti, 74.632.632 byte. SHA-256:
`5e818780fc1ebb1eae3733a7e9da8d2c18a9d8a788dfdc6181dea33782bccbfc`.
Download pubblico senza autenticazione, confronto hash e installazione del file
scaricato **PASS**. Release `draft=false`, `prerelease=true`; commit remoto e tag
coincidenti. La successiva modifica documentale su main non sposta il tag.

Solo dopo questi controlli sono stati attivati e riletti via pannello **e RPC
HTTP pubblica**: latest **0.49/18**, minimo **18**, URL della prerelease v0.49.
Manutenzione OFF, api 3, defaults e feature invariati rispetto al deploy
funzionale. Il default GPS server storico 60 non viene applicato sopra il default
client 5 o le scelte salvate. Test SQL live ripetuto dopo l'attivazione: **PASS**,
zero fixture residue, minimo finale ancora 18. Ultima esecuzione cron osservata
`succeeded` alle 14:49:00 UTC; job ogni minuto attivo.

Sul medesimo emulatore API 30, l'APK storico v0.48/code17 mostra **Update required**
e mantiene quel gate dopo riavvio dell'app in modalità aereo senza Wi-Fi, avendo
già letto il minimo. Reinstallando l'APK v0.49 scaricato pubblicamente, il gate
ammette la schermata di login; onboarding e file sentinella persistono. Nessun
crash AndroidRuntime. Le scritture di un servizio legacy vengono respinte dal
test server con contesto authenticated/version17; **non è stato eseguito un
servizio GPS legacy autenticato reale su telefono**.

Rimane da completare FCM: l'accesso confermato dall'utente è in Chrome/Edge
esterno, non disponibile agli strumenti della chat. Il browser integrato mostra
anche un errore di caricamento dello stato dei Termini di servizio sulla home
Firebase. Richiesta la credenziale server come file locale senza esporne il valore;
nessuna chiave privata creata, estratta o pubblicata durante queste verifiche.

## Cause e correzioni verificabili

| Percorso | Evidenza e intervento |
| --- | --- |
| `service/SharingController.kt` | Dopo quattro errori il vecchio ciclo aspettava un segnale della UI o una transizione di rete, potenzialmente senza termine. Il servizio riprova con backoff limitato a 30 secondi anche senza Activity. Test automatico dedicato. |
| `data/SharingRepository.kt` | Il recupero delle letture non proteggeva tutte le scritture da un token rifiutato. Le RPC attendono la sessione e rinnovano una volta dopo un 401; un fallimento temporaneo segue il backoff. Realtime non condiziona l'invio REST. |
| `data/LocationRepository.kt`, `data/FixAge.kt`, controller | Il fix usa `elapsedRealtimeNanos`: cache senza riferimento monotono, fix futuri e fix oltre 30 secondi non vengono ripubblicati. Coordinate uguali con nuova acquisizione restano un nuovo fix. Il timestamp di invio deriva dal tempo server e resta stabile nei retry dello stesso fix. |
| `publish_location_v049` | ACK booleano esplicito della riga effettivamente registrata; gli aggiornamenti fuori ordine non arretrano la posizione. Distinti fix, ACK e heartbeat del dispositivo. |
| `MainActivity.kt`, repository | Snapshot autorevole al ritorno in primo piano e alla riconnessione. Osservazione della UI sospesa in background; il servizio mantiene la propria sorgente GPS. Nessun polling aggiunto per un destinatario a schermo spento. |
| controller, manifest e `LocationForegroundService.kt` | FGS location, notifica persistente e STOP, avvio visibile con consenso, cancellazione immediata dei job allo stop, revoca persistita e inviata appena possibile. Default 5 secondi, preferenze salvate conservate. Nessun wake lock o audio usato per tenere vivo il GPS. |

Questi sono difetti del codice e correzioni coperte dai controlli disponibili.
Non dimostrano da soli la causa di ogni interruzione segnalata sui telefoni:
restrizioni OEM, Doze, radio, FLP e processi terminati richiedono collaudo reale.
Un arresto forzato impedisce il recupero automatico fino alla riapertura; un
riavvio del dispositivo non avvia arbitrariamente il servizio dal background.

## Ritrovi, mappa e audio

- Ritrovo/bengala: creazione server, scadenza massima dalla creazione originaria
  di un'ora, durate più brevi conservate, modifiche non prorogabili. RLS/RPC e UI
  invalidano alla scadenza; pulizia fisica ogni minuto. Luoghi salvati, gruppi e
  SOS conservano le proprie regole.
- Cancellazione per creatore o destinatario diretto/gruppo ancora autorizzato,
  conferma esplicita e risposta server prima dell'esito. Lock transazionale,
  un evento e una voce di coda; retry restituisce `gone`. Evento recuperabile dal
  creatore anche dopo la revoca della relazione, senza coordinate nell'audit.
  L'autocancellazione non genera avviso a sé stessi. Push ordinario `NORMAL`.
- Mappa: timer monotono di 20 secondi, utenti/cluster/ritrovi realmente proiettati
  e aree coperte dai controlli; reset con gesto, sospensione con dialoghi,
  selezione, blocco o background. I fix fuori schermo non riavviano il conto.
  La centratura non attiva follow e scarta 0,0 e coordinate non finite.
- Musica: composizione procedurale originale CC0, 32 battute a 112 bpm,
  68,571 secondi, FLAC mono 22050 Hz, circa 1,19 MB. Sorgente
  `tools/generate-lounge.py` (numpy/soundfile solo per la generazione).
  PCM SHA-256 `9cdaaccdd9d74ae78e5254b3f2124ad2d6044c68c4e72819167cc0f8618706b5`.
  Un player a volume interno 0,08, preferenza iniziale ON poi persistente,
  rilascio in background/OFF, focus/chiamate/cuffie, priorità bengala e SOS.
  La condivisione non dipende dal player; test con musica OFF incluso.

## Server realmente verificato

Progetto `vqvouzpsgbuaddcyitzg`, WhereWeAre, ramo main PRODUCTION.
Stato iniziale riletto: latest `0.48/17`, minimo `17`, api `3`, manutenzione OFF.
La tabella storica delle migrazioni esiste ma è vuota: i vecchi deploy erano
manuali. Schema e definizioni sono stati confrontati; non si presume applicata
la 022 solo perché esiste nel repository e non è stata rieseguita.

Applicate e verificate in transazioni separate:

| Migrazione | Esito |
| --- | --- |
| `024_v0_49_meetings.sql` | TTL, destinatari, rimozione/evento/outbox, cleanup; job `whereweare-meeting-expiry` attivo `* * * * *`. |
| `025_v0_49_protocol.sql` | Pre-request REST, controllo delle scritture legacy, sessioni Realtime compatibili, ACK, link bootstrap. Controlli preparati, inizialmente minimo sempre 17. |
| `026_v0_49_meeting_grants.sql` | Revoca dei privilegi diretti di scrittura/TRUNCATE sui ritrovi: le mutazioni passano dalle RPC autorizzate. |

`023_reset_meeting_points_and_location_requests.sql` era un file locale non
tracciato preesistente: non applicato, non modificato e non incluso nella release.
Non serve alla v0.49 e cancellerebbe dati estranei al deploy funzionale.

`supabase/tests/v049.sql` eseguito anche live con quattro identità temporanee e
ruolo authenticated, rollback integrale e **zero fixture residue**. Verificati
destinatario diretto, gruppo, estraneo, revoca, TTL non estendibile, limite esatto,
idempotenza, evento recuperabile, accesso diretto vietato, ACK e ordine dei fix,
client 17 rifiutato/client 18 ammesso e stop sempre consentito. Il minimo 18 in
questo test era esclusivamente interno alla transazione annullata.

Funzione `send-meeting-push` aggiornata e deploy confermato nel pannello.
Al controllo del 13 ottobre risultano assenti `FIREBASE_SERVICE_ACCOUNT` e
`PUSH_DISPATCH_SECRET`, e assente il job di dispatch: **consegna FCM non verificata**.
Il progetto Firebase configurato nell'app è `whereweare-c34e0`; l'account browser
disponibile riceve errore di autorizzazione per quel progetto. Il file pubblico
`google-services.json` non contiene una credenziale amministrativa utilizzabile.
La coda durevole e la Cronologia funzionano indipendentemente dal dispatcher.

## Compatibilità e limiti legacy

Il bootstrap è quello esistente, confronto numerico sul versionCode. Controllo
prima dell'onboarding, al resume/reconnect e ogni minuto durante UI/servizio.
Cache READY ammessa per massimo 15 minuti dalla verifica in questo processo;
riavvio offline richiede connessione. Un UPDATE già osservato resta bloccante
anche in caso di errore di rete o mancato salvataggio della cache.

Il server prepara PT426 `update_required` per API protette e pubblicazione anche
di servizi già avviati. Bootstrap, autenticazione e stop restano disponibili.
Sessioni Realtime autorizzate dopo una richiesta REST compatibile e con validità
di 15 minuti. Header e versionCode dichiarati **non sono attestazione crittografica**.

Analizzato l'APK storico reale v0.48, non il successivo main: la priorità del gate
prima dell'onboarding non era presente nel binario. Offline un vecchio APK può
mostrare UI/cache locale e non può essere disinstallato da remoto. Il divieto
server impedisce le operazioni protette, non modifica retroattivamente il binario.

## Prove da completare sui telefoni

Controlli disponibili **PASS**: `:app:build` (debug/release), **161 test JVM**,
lint **zero errori, 82 warning** (dipendenze/stile/risorse inutilizzate), 14 suite
SQL locali, 3 test Deno, test SQL live. Nessun test fallito al termine.
Firma v0.48/v0.49 verificata con apksigner:
`acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6`.
Installazione `adb install -r` sopra l'APK storico **PASS**, versionName 0.49/code18,
onboarding e file sentinella del vecchio sandbox conservati, nessun crash AndroidRuntime.
Non è una verifica di conservazione di un account autenticato reale.

**NON ESEGUITE:** due account su due telefoni per 60–90 minuti, entrambi bloccati,
fermi/in movimento, Wi-Fi/mobile, rinnovo token reale, riavvio/rimozione recenti,
Doze/OEM, musica OFF, consegna provider e visualizzazione push, focus/chiamate e
disconnessione cuffie reali, verifica percettiva del loop audio, gesti multitouch
e occlusioni sulla mappa reale. Nessun telefono fisico collegato. L'emulatore
disponibile è API 30 senza Google Play Services: non costituisce prova FLP/FCM.

Procedura: avviare consensualmente la condivisione reciproca a 5 secondi e
registrare per 90 minuti diagnostica locale senza coordinate/token; alternare
schermo visibile, blocco di un telefono, blocco di entrambi, cambio rete e 5 minuti
offline. Misurare età fix e ACK, silenzi e recupero in entrambi i versi. Ripetere
con musica OFF. Con ADB su dispositivo di test scollegato dall'alimentazione,
forzare Doze (`dumpsys deviceidle force-idle`) e poi `unforce`; distinguere questo
test da OEM e arresto forzato. Verificare nessun invio dopo STOP/logout.

Per ritrovi provare 59:59, 60:00 e dopo, app chiuse, riconnessione e cache; per
cancellazione due richieste concorrenti e revoca prima della richiesta. Il test
SQL abbrevia il TTL solo delle fixture rollback, mai dei dati live. Per audio e
mappa verificare toggle/aggiornamento, tutti i gesti, overlay, filtri, sospensioni
e assenza di follow involontario.

## Recupero

Backup locale delle sole definizioni/configurazioni interessate in
`.tools/v049-server-before.json`, `.tools/v049-meeting-grants-before.json` e
`.tools/v049-push-before.ts`; nessun export di posizioni o utenti. Non pubblicare
questi file come dump. In caso di problema dopo l'attivazione, ripristinare solo
latest `0.48/17`, minimo `17` e URL della prerelease v0.48 in
`private.app_bootstrap`, poi rileggere `public.app_bootstrap()`. Lasciare TTL e
autorizzazioni sicure in vigore. Preferire una migrazione correttiva progressiva;
non ricreare funzioni legacy né riaprire privilegi diretti senza analisi.

Comandi ripetibili: `./gradlew -I tools/isolated-v049-build.gradle :app:build
--max-workers=2 --console=plain`, `node supabase/tests/run-v049.mjs` (PGlite locale),
`deno test --allow-env --allow-net supabase/functions/send-meeting-push/`.
Il percorso build isolato evita un blocco Windows di rimozione dei vecchi output.
Nessuna cancellazione forzata dei file dell'utente.

Riferimenti tecnici: [FGS location](https://developer.android.com/develop/background-work/services/fgs/service-types),
[Doze](https://developer.android.com/training/monitoring-device-state/doze-standby),
[audio focus](https://developer.android.com/media/optimize/audio-focus),
[Cron](https://supabase.com/docs/guides/cron).
