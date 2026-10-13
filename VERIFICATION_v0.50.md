# Verifica v0.50 — Android 0.50 / 19

Data: 13 ottobre 2026. Prerelease richiesta dall'utente dopo la segnalazione
dell'avviso Realtime persistente. Il tag `v0.50` identifica esattamente il commit
dell'APK; checksum, firma e provenienza sono negli allegati della prerelease.

## Correzione e prova di regressione

### Resoconto aggiunto dopo la pubblicazione

Prerelease [v0.50](https://github.com/Skyzzato/WhereWeAre/releases/tag/v0.50)
pubblicata (`prerelease=true`, `draft=false`), con APK dal commit immutabile
`4187921a2f8128b3ef7c55cdf64b58a37d42b551`, tree
`df58d97b95b4d76ea2b16ad9175dbddfca1ccbb9`. Ricostruzione da directory nuova:
42 task eseguiti, assembleDebug PASS.

APK pubblico: **74.632.632 byte**, SHA-256
`f6103a636b58548a827797b7430aafb0d76b0b0aa71324e66e8f391c2d6d1478`.
Download senza autenticazione HTTP 200 e firma storica verificati. Installato
il file scaricato sopra l'APK pubblico v0.49: versionName/code 0.50/19 e dati
locali di prova (sentinella e preferenza onboarding) conservati su emulatore
API 30 isolato. Nessun account reale impiegato; non è un test della sessione
autenticata o del GPS su telefono. Emulatore di prova poi arrestato.

Dopo queste verifiche, il server è stato aggiornato a latest **0.50/19**,
minimo compatibile **18**, URL della nuova prerelease. Confermato anche tramite
RPC bootstrap pubblico HTTP 200. Nessuna migrazione aggiuntiva.
[PUBLICATION_REPORT.json](https://github.com/Skyzzato/WhereWeAre/releases/download/v0.50/PUBLICATION_REPORT.json)
integra la provenienza e lo snapshot documentale allegati alla release.

### Verifica precedente al tag

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
