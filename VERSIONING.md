# Politica di versionamento

## Branch

`main` è l'unico branch remoto permanente. Sviluppo lineare, nessun branch per
versione e nessun `codex/vX` permanente. Eventuali branch temporanei degli strumenti
vanno eliminati dopo integrazione verificata: prima controllare che tutti i commit
siano raggiungibili da main. Mai force push, reset distruttivi o riscrittura della
cronologia pubblicata. Le versioni storiche si conservano tramite tag.

## Versione e tag

Sorgente primaria: `app/build.gradle.kts`, campi `versionName` e `versionCode`.
Baseline della distribuzione corrente: **v0.49 / 18**. Il versionCode cresce sempre, non si riutilizza:
la prossima versione distribuita richiede **almeno 19**, a prescindere dal nome.
Splash, Informazioni e User-Agent derivano da BuildConfig.

Ogni versione pubblicata ha tag `v<versionName>`, esattamente sul commit usato per
costruire l'APK. Non spostare tag pubblicati. APK, checksum, provenance e note
devono identificare lo stesso commit/tag. Una correzione successiva al tag su main
non modifica l'APK già pubblicato: la sua distribuzione richiede una nuova versione
decisa esplicitamente, mai la sostituzione arbitraria degli artefatti storici.

## GitHub Release e documentazione

Durante lo sviluppo tutte le GitHub Releases hanno `prerelease = true`.
Una release stabile richiede una decisione futura esplicita. Il nome storico
`v0.1-stable` è un tag conservato, non una dichiarazione di stabilità corrente.
README e RELEASE_NOTES.md si aggiornano a ogni versione; RELEASE_NOTES.md è
l'unica fonte cronologica ufficiale, senza CHANGELOG duplicati. I documenti
versionati restano storici: non riscrivere provenienza, branch o risultati passati.

## Politica server e aggiornamento obbligatorio

Stato live, migrazioni 024–027, test e limiti in [VERIFICATION_v0.49.md](VERIFICATION_v0.49.md).
La 027 completa il dispatcher server dopo la pubblicazione: nessuna modifica
all'APK, al suo commit o al tag v0.49. Il report push integra gli allegati iniziali.
Al deploy funzionale il server aveva già latest 0.48/17 e minimo 17; la presenza
della 022 non ne dimostra l'applicazione. Il registro storico live è vuoto:
confrontare anche schema/definizioni. Le migrazioni applicate sono immutabili.
Il reset locale 023 non è parte della release e non è stato eseguito.

Solo dopo build, test, firma e APK pubblicato verificati, per imporre v0.49 come minimo:

```sql
update private.app_bootstrap
set
    latest_version_code = 18,
    latest_version_name = '0.49',
    minimum_supported_version_code = 18,
    update_url = 'https://github.com/Skyzzato/WhereWeAre/releases/tag/v0.49'
where singleton = true;

select public.app_bootstrap();
```

Con minimo 18, v0.49/code 18 è ammessa; i client precedenti vengono rifiutati
dalle API protette e dalle scritture di posizione. Bootstrap e stop restano
disponibili. Header/versionCode sono compatibilità, non prova crittografica dell'APK.
La nuova UI controlla prima dell'onboarding e limita la cache; vecchi APK offline
possono mostrare schermate/dati locali. Non si modificano binari già distribuiti.
L'esito live finale viene registrato su main dopo la release, senza spostare il tag.
