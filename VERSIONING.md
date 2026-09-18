# Politica di versionamento

## Branch

`main` è l'unico branch remoto permanente. Sviluppo lineare, nessun branch per
versione e nessun `codex/vX` permanente. Eventuali branch temporanei degli strumenti
vanno eliminati dopo integrazione verificata: prima controllare che tutti i commit
siano raggiungibili da main. Mai force push, reset distruttivi o riscrittura della
cronologia pubblicata. Le versioni storiche si conservano tramite tag.

## Versione e tag

Sorgente primaria: `app/build.gradle.kts`, campi `versionName` e `versionCode`.
Baseline pubblicata: **v0.48 / 17**. Il versionCode cresce sempre, non si riutilizza:
la prossima versione distribuita richiede **almeno 18**, a prescindere dal nome.
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

La configurazione server applicata documentata resta SETUP_v0.46.md. La nuova
`supabase/migrations/022_v0_48_version_policy.sql` prepara latest 0.48/17 senza
alzare il minimo; non è stata applicata al server durante il consolidamento.
Le migrazioni 001–021 sono immutabili. Verificare lo stato prima di qualsiasi deploy.

Solo dopo esplicita autorizzazione al deploy live, per imporre v0.48 come minimo:

```sql
update private.app_bootstrap
set
    latest_version_code = 17,
    latest_version_name = '0.48',
    minimum_supported_version_code = 17
where singleton = true;

select public.app_bootstrap();
```

Con minimo 17: v0.48/code 17 ammessa; v0.47/code 16, v0.46/code 15 e precedenti
bloccate da UPDATE. Il confronto usa il codice numerico, non il nome versione.
Il gate precede onboarding, login e navigazione; retry e cache esistenti restano.
La correzione di priorità del 18 settembre è successiva al tag v0.48 e non è
contenuta nell'APK storico: impostare il minimo 17 non aggiorna quel binario.
