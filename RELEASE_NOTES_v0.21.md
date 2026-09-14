# WhereWeAre v0.21

Base: tag GitHub `v0.2`, commit `b7a3212`. Workspace `Documents/ChatGPT/WhereWeAre`, branch `codex/v0.21`. Versione `0.21`, codice `3`; nessuna pubblicazione GitHub eseguita.

## Modifiche

- Splash Compose con Fit esplicito, proporzioni invariate, margini verticali e contenuto scorrevole su schermi piccoli. Splash Android 12+ con drawable dotato di spazio di sicurezza nel viewport, invece della maschera implicita dell’icona launcher.
- Elimina foto tramite RPC esistente `set_avatar(null)`: profilo aggiornato immediatamente nello stato condiviso, cache RAM invalidata e iniziale ripristinata in tutte le viste personali. Gli account autorizzati ricevono l’invalidazione Realtime esistente. Si elimina solo il file appartenente al proprietario; se la pulizia Storage fallisce dopo la RPC, il profilo rimane corretto e il file privato residuo sarà eliminato dalla cancellazione account.
- Popup persona: informazioni precedenti conservate, coordinate con 5 decimali (punto decimale anche in italiano), link OpenStreetMap costruito dal medesimo fix del marker. Coordinate non valide: messaggio e nessun link. Intent ACTION_VIEW/CATEGORY_BROWSABLE, errore gestito se manca un browser.
- MapLibre mantenuto. Standard OpenStreetMap/OpenFreeMap conservato; OpenTopoMap e CyclOSM aggiunti, raster HTTPS senza key. Un unico enum contiene ID, etichetta, URL, crediti e max zoom. Scelta persistente nella chiave DataStore `map_style`, compatibile con gli ID v0.2. Cambio stile via `rememberMapState(baseStyle=...)`, camera conservata. Attribuzione sempre visibile più link nativi e pagina copyright. Cache nativa su disco di 64 MiB, User-Agent identificativo, niente download offline massivi.
- Login: “Password dimenticata?” mostra il messaggio di indisponibilità tramite lo stato UI esistente. Nessuna richiesta di reset o email.
- Impostazioni: card grigia Codice personale, copia con feedback, testo condiviso tramite Sharesheet; sezione Account con “Registrato dal”, Esci e azione distruttiva Elimina account. Privacy/Informazioni/Copyright mantengono i contenuti e seguono Account.
- Data registrazione: `created_at` dell’utente Supabase Auth, conversione con timezone del dispositivo e formato italiano `dd/MM/yyyy`; fallback esplicito se mancante. Nessuna nuova colonna.
- Eliminazione account: riutilizzata la funzione Edge `delete-account` già presente e collaudata, con conferma distruttiva. Verifica JWT, tombstone, stop, pulizia Storage e cascata Auth/profilo/relazioni/gruppi/posizioni; nessuna procedura parziale aggiunta nel client.

## Tracking e limiti Android

Un solo `LocationForegroundService` e un solo job del controller singleton. `START_STICKY` abilita il ripristino da parte di Android, `stopWithTask=false` mantiene il servizio quando l’Activity è rimossa dai recenti. Notifica persistente a bassa importanza “WhereWeAre / Condivisione posizione attiva”, con Stop.

L’avvio utente resta dalla UI visibile, dopo i permessi. Un riavvio di sistema con Intent nullo richiede una sessione già salvata, dello stesso utente, senza stop pendente; attende il ripristino Auth e il bootstrap. DataStore conserva ID utente, UUID sessione e revisione server originale, mai una coda di coordinate. La revisione viene salvata prima della RPC: retry e riavvii non possono annullare uno stop server intervenuto dopo l’avvio. Stop cancella l’intenzione di tracking e mantiene il worker esistente per lo stop remoto offline.

FLP conserva gli intervalli v0.2 (5 s, 30 s, 1 min predefinito, 5 min, 30 min, 1 h) e le modalità bilanciata/alta precisione. Sotto 5 minuti mantiene una sola richiesta con minimum interval uguale all’intervallo; per intervalli lunghi conserva le acquisizioni singole a timeout. Errori transitori del provider vengono ritentati dopo 30 s; revoca permessi ferma la condivisione. Upload ritenta con l’ultima posizione valida, senza accumulare storico. Persistenza/refresh token restano gestiti da Supabase Auth.

Permessi: nessuno aggiunto. Restano COARSE/FINE_LOCATION, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION e POST_NOTIFICATIONS; nessun ACCESS_BACKGROUND_LOCATION, boot receiver o allarme. Home, schermo spento e distruzione Activity non cancellano il job. Android/OEM possono ritardare fix o riavvio; la cadenza non è una garanzia temporale. Forza arresto e Stop di sistema non vengono aggirati. Il comportamento reale in Doze, rimozione dai recenti e process recreation richiede ancora collaudo su dispositivi.

## Database

Nessuna migration SQL; nessuna modifica a RLS, schema o backend distribuito. Il bootstrap remoto resta alla v0.2 (minimo 2), compatibile con il client code 3. Nessuna service-role key nel client. Configurazione locale esistente riutilizzata in `local.properties`, escluso da Git.

## Verifiche

- Build finale del 12 settembre 2026: `gradlew.bat :app:build --console=plain` — **BUILD SUCCESSFUL**, debug e release compilate, 110 task. Lint: 0 errori, 44 avvisi su risorse inutilizzate preesistenti. Risolto un errore locale AccessDenied di Lint rimuovendo l’attributo di sola lettura dalle sole directory generate in `app/build`.
- APK installabile di collaudo: `app/build/outputs/apk/debug/WhereWeAre-v0.21-debug.apk`, firma debug APK v2 verificata con `apksigner`; versione 0.21/code 3, min SDK 26. SHA-256: `B700E531899B50310E7F3ED4D775468B2B6143CE3DC8DDBF4FA4C7D6C21614B8`. La build release è `app/build/outputs/apk/release/app-release-unsigned.apk`, non firmata per la distribuzione.
- 17 test JVM debug superati, 0 errori/fallimenti.
- Suite SQL locale PGlite completa superata, incluse RLS e Storage guard.
- Suite HTTPS/WebSocket remota superata con tre account temporanei e cleanup completato. Due client scambiano fix sintetici, revoche e Stop; verificati anche gruppi, avatar e cancellazione account. Non sono emulatori Android.
- Tutti e tre i provider rispondono HTTP 200 ai controlli mirati; JSON raster verificato nei test JVM.
- Nessun dispositivo/AVD disponibile. Non dichiarati come superati i test che richiedono Android.

| Test richiesti | Esito e copertura |
| --- | --- |
| 1 splash su più dimensioni | Layout e risorse controllati nel codice; verifica visiva su dispositivo pendente |
| 2 login | Auth reale con due client superata; UI pendente |
| 3 recupera password | Handler e messaggio verificati nel codice; tap UI pendente |
| 4–7 avatar upload/sostituzione/rimozione/default | Operazioni Storage/profili reali superate; fallback Compose implementato, verifica visiva pendente |
| 8–10 popup/coordinate/browser | Coordinate e URL testati in JVM; popup e apertura browser su Android pendenti |
| 11–15 tre mappe/persistenza/attribuzione | JSON, ID e raggiungibilità verificati; DataStore e attribuzione implementati; selezione/riavvio/rendering pendenti |
| 16–18 card/copia/condividi | Implementati; verifica appunti e Sharesheet su Android pendente |
| 19 data registrazione | Campo Auth reale e conversione data/timezone testati |
| 20–22 logout/elimina account/navigazione | Backend cancellazione/regressioni superati; navigazione e logout UI pendenti |
| 23–27 GPS foreground/background/schermo spento/recenti/duplicati | Analisi controller/manifest e guardia singolo job; collaudo Android pendente |
| 28–29 upload e secondo utente | Client HTTPS/WebSocket reali superati con fix sintetici; GPS fisico e rendering mappa pendenti |

Regressioni backend di registrazione, relazioni, autorizzazioni direzionali, gruppi, scadenza posizione, RLS, Realtime e Storage superate. L’app v0.2 conserva solo l’ultima posizione: nessuno storico rimosso o introdotto. Orientamento portrait e navigazione esistenti mantenuti nel codice.

## File modificati/aggiunti

- Versione/configurazione: `app/build.gradle.kts`, `local.properties.example`.
- Splash/runtime/manifest: `MainActivity.kt`, `WhereWeAreApplication.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values-v31/styles.xml`.
- Modelli: `domain/MapStyle.kt` (nuovo).
- Repository: `data/AuthRepository.kt`, `data/AvatarRepository.kt`, `data/PreferencesRepository.kt`, `data/SharingRepository.kt`.
- Servizio: `service/LocationForegroundService.kt`, `service/SharingController.kt`.
- UI: `ui/AuthScreen.kt`, `ui/Avatar.kt`, `ui/MapScreen.kt`, `ui/SettingsScreen.kt`, `ui/ViewModels.kt`, `app/src/main/res/values/strings.xml`.
- Test: `app/src/test/java/com/whereweare/app/V021Test.kt`, `supabase/tests/live-v02.mjs`.
- Documentazione: `README.md`, `VERIFICATION.md`, questo report. I percorsi Kotlin abbreviati sono relativi a `app/src/main/java/com/whereweare/app/`.

## Riferimenti verificati

- [Android Service: START_STICKY e restart](https://developer.android.com/reference/android/app/Service#START_STICKY)
- [MapLibre: cambio stile e conservazione della camera](https://maplibre.org/maplibre-compose/api/lib/maplibre-compose/org.maplibre.compose.map/remember-map-state.html)
- [Policy tile OSM](https://operations.osmfoundation.org/policies/tiles/)
- [OpenTopoMap e attribuzione](https://www.opentopomap.org/about)
- [CyclOSM](https://www.cyclosm.org/)
