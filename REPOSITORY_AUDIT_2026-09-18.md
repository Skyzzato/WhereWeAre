# Audit e consolidamento — 18 settembre 2026

## Repository

Audit iniziale eseguito prima delle modifiche: git status, remote -v, branch -a,
tag --list, log --oneline --decorate --graph --all, fetch --all --tags --prune;
confronto con GitHub API branches/releases e ls-remote heads/tags.
Working tree iniziale pulita su codex/v0.45.

- main iniziale: 633966498ee4dc31ce199dd553e186bf009b68c2.
- Tag v0.48 e baseline: 0e97dc41f57b9e7511a9979604904ab5b3887361.
- Confronto: 16 commit avanti, zero indietro.
- main creata localmente da origin/main, merge --ff-only v0.48 e push riusciti.
- Dopo il primo push: origin/main == v0.48; i commit di consolidamento successivi
  restano sopra quella baseline, senza spostare il tag o riscrivere la storia.
- Branch remoti eliminati: codex/v0.33, codex/v0.4, codex/v0.41, codex/v0.42,
  codex/v0.43, codex/v0.44, codex/v0.45. Per ciascuno: merge-base --is-ancestor
  riuscito e rev-list v0.48..branch = 0; ricontrollati contro origin/main prima
  della cancellazione. Unico branch remoto rimasto: main.
- Branch locali storici conservati; nessun tag cancellato o spostato.
- Tutto il codice v0.48 resta raggiungibile. Nessuna modifica a funzioni Luoghi,
  mappe, SOS, condivisione, preferenze o servizi.

Lo SHA finale del consolidamento si ricava da git rev-parse main e dal report
consegnato: questo documento appartiene esso stesso alla cronologia consolidata.

## Releases e artefatti

Modificato solo prerelease false -> true per v0.4 e v0.41 tramite PATCH delle
release esistenti. Confronto prima/dopo: id, tag, body, created_at, published_at
ed elenco completo degli asset invariati. Nessuna release creata.

| Versione | Tag | Stato finale | Commit |
| --- | --- | --- | --- |
| v0.48 | v0.48 | prerelease | 0e97dc41f57b9e7511a9979604904ab5b3887361 |
| v0.47 | v0.47 | prerelease | 30aba60269af4a92eea9a59448c9035cbe6c7755 |
| v0.46 | v0.46 | prerelease | 28a24816bccf900c27a31e311f84982ccc79f38d |
| v0.45 | v0.45 | prerelease | 6d28b36deca72b62d7110a6cce679fd35f838f1e |
| v0.44 | v0.44 | prerelease | 63859336dfaaa8c7c01409d3d0a21865ae58a8f5 |
| v0.43 | v0.43 | prerelease | 639031f6011bc728f01d830c845b357741926f70 |
| v0.42 | v0.42 | prerelease | 8a614355dbdc0924d2bac27fc4fa783b1342ac81 |
| v0.41 | v0.41 | prerelease | 8d68ebdf0fd1b91aef92190ef265597c504df878 |
| v0.4 | v0.4 | prerelease | 633966498ee4dc31ce199dd553e186bf009b68c2 |
| v0.2 | v0.2 | prerelease | b7a3212f999339ece9d9d8b2c2325858df3b7ded |

I tag v0.1-stable e v0.33 sono conservati, senza GitHub Release associata.
Il nome storico del primo non modifica la politica attuale di prerelease.

Verificati per tutte le release: checksum allegati coerenti con digest GitHub;
SHA-256 degli APK locali (v0.2 scaricato per l'audit) identico al digest remoto;
aapt dump badging coerente con app/build.gradle.kts al tag. Codici:
v0.2=2, v0.4=9, v0.41=10, v0.42=11, v0.43=12, v0.44=13, v0.45=14,
v0.46=15, v0.47=16, v0.48=17.

Provenance scaricate da GitHub per v0.44–v0.48: commit uguale al tag e hash APK
uguale al digest pubblicato. Per v0.2 e v0.4–v0.43 non esiste un allegato
BUILD_PROVENANCE: versione e checksum sono verificati, il commit di compilazione
non è certificabile dai soli metadati APK. Nessun artefatto storico sostituito.

APK v0.48: WhereWeAre-v0.48.apk, SHA-256
cdec4f04d0dbcef3dad72eaeacd7f27c635a1ff06aa52255501e2e1081150ecd,
commit 0e97dc41f57b9e7511a9979604904ab5b3887361.

## Documentazione e versione

- README: baseline 0.48/17, main, prerelease, link note/verifiche/APK e User-Agent.
- RELEASE_NOTES.md: unica cronologia; v0.48 in testa, poi v0.47 fino a v0.4,
  seguite dalla ricostruzione precedente conservata.
- VERIFICATION.md: rinvio corrente prima delle sezioni storiche.
- ROADMAP.md: stato reale, sviluppo successivo da definire.
- VERSIONING.md: branch, versionCode crescente, tag/build/asset, prerelease,
  documenti storici e comando server esplicito.
- Questo audit: nuove prove separate dalle dichiarazioni storiche v0.48.
- tools/isolated-consolidation-build.gradle: cartella di verifica separata dagli
  artefatti storici; equivalente all'init script locale usato per la build.

Android resta versionName 0.48 / versionCode 17. Splash, pagina Informazioni e
User-Agent usano BuildConfig; nessuna stringa di versione precedente in quei punti.
SETUP_v0.46.md resta la configurazione applicata documentata; non sono creati
SETUP v0.47/v0.48 o CHANGELOG.md. Documenti storici versionati immutati.

## Bootstrap e server

BootstrapRepository conserva installed < minimum_supported_version_code -> UPDATE,
cache valida in fallback, timeout e retry; una risposta bloccante non viene persa
se fallisce il salvataggio cache. BootstrapViewModel mantiene refresh e arresto
condivisione per UPDATE/MAINTENANCE. Nessuna chiusura forzata dell'app.

MainActivity usa BootstrapBoundary prima di onboarding/login/NavHost: i contenuti
normali e i relativi effetti di navigazione non vengono composti fino a READY e
inizializzazione sessione completata. Gli intent restano pendenti. Refresh alla
ripresa conservato; UPDATE non viene azzerato da cambio account o nuovi link.
L'unica Activity esportata è MainActivity.

Nuovi test Compose/Robolectric: tutti i gate bloccano il contenuto, retry disponibile,
READY lo abilita; passaggio READY -> UPDATE rimuove contenuto già visibile;
variazioni simulate di destinazione/sessione e stop/start/resume della Activity
non aggirano UPDATE. Sono prove della composable reale con contenuto di test,
non E2E di intent/notifiche o autenticazione reale. Test numerico: minimo 17
ammette 17 e blocca 1–16, anche con manutenzione attiva.

022_v0_48_version_policy.sql prepara latest 0.48/17 e preserva il minimo esistente.
Test locale con 001–022, ripetizione 022 e confronto minimo prima/dopo;
public.app_bootstrap restituisce il minimo anche ai ruoli anon/authenticated.
Comando per imporre minimo 17 e select di verifica in VERSIONING.md.
**Nessun deploy o UPDATE sul server remoto eseguito.**

Confronto degli hash Git di tutti i 21 file migrazione 001–021 contro v0.48:
identici. Nessuna rinumerazione o duplicazione; aggiunta solo 022.

## Test del consolidamento

| Comando | Esito reale |
| --- | --- |
| .\gradlew.bat -I tools/isolated-v044-build.gradle :app:build --console=plain | FAIL iniziale in packageDebugResources sugli intermedi storici; nessun PASS attribuito a questo tentativo. |
| .\gradlew.bat -I .tools/consolidation-build.gradle :app:build --console=plain | PASS in cartella nuova: assembleDebug, assembleRelease (unsigned), 155 test debug JVM/Compose/Robolectric, zero errori/fallimenti/skipped; lint zero errori, 72 warning. |
| node supabase/tests/run-v03.mjs --v033 | PASS suite storiche e regressioni. |
| node supabase/tests/run-v03.mjs --v04 | PASS suite incrementali 001–017. |
| node supabase/tests/run-v046.mjs | PASS 12 suite, tutte le migrazioni correnti 001–022. |
| node supabase/tests/run-version-policy.mjs | PASS idempotenza 022, minimo preservato, RPC bootstrap anon/authenticated. |
| node tools/verify-v033-migrations.mjs | PASS hash baseline 001–007. |
| .\.tools\deno\package\deno.exe test --allow-env supabase/functions/send-meeting-push/index_test.ts supabase/functions/send-meeting-push/payload_test.ts supabase/functions/send-meeting-push/delivery_test.ts | PASS 3 test. |
| adb devices | Nessun dispositivo; prove fisiche non eseguite. |
| git diff --check | PASS. |

L'init script locale della build riuscita imposta soltanto la build directory a
.tools/build-consolidation/<project>; la stessa configurazione è conservata in
tools/isolated-consolidation-build.gradle per riprodurre il comando.
Report locali: .tools/consolidation-build-fresh.log e
.tools/build-consolidation/app/{test-results/testDebugUnitTest,reports}.
I 155 test comprendono V048Test, V048InteractionTest, V048RenderingTest e
BootstrapBoundaryTest (3 test), oltre ai test cache/fallback di V02Test.
Il task build esegue testDebugUnitTest; nessuna suite separata testReleaseUnitTest
è dichiarata eseguita. Debug e release compilati con metadati 0.48/17 verificati;
BuildConfig generato conferma VERSION_NAME e VERSION_CODE.

## Limiti e seguito

La correzione del gate è successiva al tag, quindi non è contenuta nell'APK v0.48
pubblicato. main conserva la baseline 0.48/17 più il consolidamento; il tag continua
ad identificare il binario originale. Per distribuire la correzione occorrerà una
nuova build/versione autorizzata, con versionCode almeno 18 e nome da decidere.
Non è stato inventato un numero nuovo né pubblicato un nuovo APK/release.
Imporre minimo 17 da solo non cambia la priorità dell'onboarding nel vecchio APK.

ADB devices senza dispositivi: nessuna prova fisica, emulatore, navigazione GPU,
notifica reale, cambio account reale o misura GPS dichiarata PASS. Prove SQL su
PGlite locale con ruoli sintetici, non E2E PostgREST/Auth live. Le limitazioni
storiche su FCM e collaudo fisico restano quelle dei documenti di versione.
