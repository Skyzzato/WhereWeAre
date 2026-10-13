# Verifica v0.50 — Android 0.50 / 19

Data: 13 ottobre 2026. Prerelease richiesta dall'utente dopo la segnalazione
dell'avviso Realtime persistente. Il tag `v0.50` identifica esattamente il commit
dell'APK; checksum, firma e provenienza sono negli allegati della prerelease.

## Correzione e prova di regressione

`SharingRepository.observe` attendeva indefinitamente dopo il primo subscribe.
Con `phx_close` l'SDK rimuoveva il canale e passava a `UNSUBSCRIBED`, senza che
il repository riprendesse il ciclo di cleanup/backoff/riconnessione.
Ora viene osservata la chiusura del canale e riutilizzato quel ciclo, incluso
il recupero dello snapshot autorevole. La pubblicazione GPS resta indipendente.

`RealtimeRecoveryTest` usa repository e SDK 3.8.0 reali, autenticazione simulata
e socket controllato. Prima del fix la seconda sottoscrizione non arrivava
entro otto secondi; dopo il fix viene ristabilita, con un solo canale e tutti
i dieci binding INSERT/UPDATE delle cinque tabelle.

La build 0.50/19 ha superato **162 test JVM** (zero fallimenti, errori o test
saltati), **assembleDebug** e **lintDebug** (zero errori, 82 warning esistenti).
Comando: `./gradlew.bat -I tools/isolated-v049-build.gradle
"-PwwaBuildDirectory=.tools/build-v050-verified" :app:testDebugUnitTest
:app:lintDebug :app:assembleDebug --max-workers=2 --console=plain`.

Dopo il commit, l'APK da distribuire viene ricostruito in una directory nuova
(`.tools/build-v050-artifact`), quindi verificato per identità e firma storica
`acf785391278fa98832980a51e594c88ffb06ff36b590c73c18c917f975080c6`.
La verifica del download pubblico e dell'aggiornamento 0.49 → 0.50 su emulatore
API 30 verrà allegata separatamente come `PUBLICATION_REPORT.json`; non è
anticipata come completata nel presente snapshot del commit della build.

## Compatibilità e limiti

- applicationId `com.whereweare.app`, versionCode 19, versionName 0.50.
- APK di collaudo variante debug, con la firma storica: aggiornamento senza
  disinstallazione. L'APK v0.49 e il relativo tag sono immutati.
- Server con migrazioni 024–028, nessuna nuova migrazione richiesta. Il file
  locale 023 di reset non è parte della release e non viene eseguito.
- Minimo server 18; latest 0.50/19 da annunciare solo dopo il download pubblico
  e la verifica di identità/firma/checksum dell'APK.
- Il test di regressione non dimostra che la chiusura del canale sia la causa
  esatta dell'avviso sul telefono dell'utente: manca il log di quel dispositivo.
- Nessun nuovo collaudo su telefoni reali, GPS/Doze/OEM prolungato o ricezione
  visibile delle notifiche FCM. Le precedenti verifiche server restano descritte
  in [VERIFICATION_v0.49.md](VERIFICATION_v0.49.md).
