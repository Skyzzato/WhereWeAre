# Recupero della cronologia v0.2 → v0.33

Analisi del 14 settembre 2026. Destinazione: `https://github.com/Skyzzato/WhereWeAre.git`, branch remoto predefinito `main`.

## Evidenze e criterio

All'inizio `codex/v0.21`, `codex/v0.3`, `codex/v0.31`, `codex/v0.32` e `codex/v0.33` puntavano tutti a `b7a3212`, coincidente con `origin/main` e `v0.2`. Il reflog registrava soltanto passaggi fra branch, senza commit successivi. Le modifiche erano presenti nel working tree, con sorgenti e migrazioni non tracciati.

`git fsck --full --no-reflogs --unreachable` ha trovato 195 blob e 176 alberi non raggiungibili, nessun commit perduto. Undici alberi contenevano una radice di progetto completa. Il confronto delle varianti, di Gradle, dei sorgenti e dei report ha consentito la selezione seguente:

| Versione | Albero Git originale selezionato | Criterio |
| --- | --- | --- |
| v0.21 | `5610129e767012e1cc42042ff1b7d804ec2336ed` | 0.21/3; stessa base applicativa dell'altra variante 0.21, report più completo. |
| v0.3 | `267e674b7e45eb98f62e169546afefb01d2ec64c` | 0.3/5; correzione Hilt e report integrato rispetto all'altra variante build 5. |
| v0.31 | `4511a7589093472739b0ea987328f6475674f895` | 0.31/6; esclusa una successiva bozza che mantiene 0.31 ma introduce già file degli stili/audio 0.32. |
| v0.32 | `a3e04e843ed51b2c30f9079146a8786cf364a4b8` | 0.32/7; variante con correzioni audio in FlareAudio e MeetingMessagingService. |

I nuovi commit conservano esattamente questi alberi: nessuna modifica ai vecchi snapshot, nessuna retrodatazione. Il loro ordine rappresenta l'evoluzione recuperata e non pretende di ripristinare metadati di commit inesistenti. Non si afferma l'identità binaria con gli APK storici e non si dichiarano nuove build delle versioni intermedie.

Commit di recupero: `d977685` (v0.21), `7a9e7e6` (v0.3), `cc10e9a` (v0.31), `92e8b13` (v0.32). La v0.2 rimane il genitore originale della catena. Per le tappe intermedie si usano messaggi di commit espliciti; il nuovo tag di release è limitato alla v0.33 finale verificata.

L'albero `d783650a78674187fb78b76697eb063f8e1ff5f2` contiene anche una v0.33 recuperabile; per la pubblicazione finale prevale comunque il working tree consegnato, controllato contro una copia di sicurezza e hash SHA-256. La v0.22 non ha un albero autonomo riconoscibile: viene documentata senza commit o tag inventati.

## Conservazione

Prima dei commit è stata creata una copia esterna del progetto, inclusi `.git`, oggetti irraggiungibili e APK locale, con inventario SHA-256 dei file versionabili. Esclusi cache/build rigenerabili e `local.properties`, rimasto nella sua posizione originale. Nessun reset, clean, checkout distruttivo o force push. `ROADMAP.md` resta invariato.

Le uniche modifiche introdotte durante il recupero riguardano documentazione e regole di esclusione per artefatti/configurazioni sensibili. Sorgenti Android, risorse, dipendenze e migrazioni della v0.33 sono conservati byte per byte rispetto all'inizio dell'attività. La normalizzazione Git LF/CRLF segue il `.gitattributes` preesistente.

## Verifica

Gli esiti della verifica corrente sono riportati nella prima sezione di `VERIFICATION.md`. Le prove SQL usano un database PGlite temporaneo locale; i test live che creano account o cambiano dati remoti non sono stati eseguiti. I controlli di sicurezza comprendono gli snapshot recuperati oltre allo stato finale, per evitare di introdurre segreti attraverso commit storici.
