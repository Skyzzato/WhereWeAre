# WhereWeAre v0.47 — pre-release

Versione Android **0.47**, **versionCode 16** (APK distribuito v0.46: 15). ApplicationId e firma debug storica invariati.

## Correzioni

- Riapertura: lettura dello stato di condivisione dopo ripristino autenticazione, rinnovo del token una volta in caso di sessione rifiutata e massimo due retry transitori entro 30 secondi. Rete, sessione, permessi, posizione e server hanno messaggi distinti. Un successo cancella il precedente errore.
- Ripresa serializzata; risposte tardive non possono annullare uno stop esplicito. Le preferenze OFF persistenti evitano qualsiasi avvio automatico. La riconnessione visibile riesegue la verifica; il servizio conserva la sessione durante la ricreazione del processo.
- Caricamenti obsoleti scartati anche in caso di errore; recupero sessione neutro, tentativi REST/realtime limitati e nuova possibilità dopo ripresa, aggiornamento manuale o riconnessione. Nessuna modifica alle policy server.
- Icone bandiera, Check-in e menu mappa verdi, con lo stesso token semantico del pulsante Avvia condivisione; contrasto verificato nei sei temi.
- Cronologia eventi / Event history: elenco unificato invariato, inclusi SOS e bidone per la rimozione personale. Nessuna modifica ai dati o agli identificativi persistenti.
- Nome visualizzato: esclusivamente WhereWeAre.
- Avatar mappa e anteprima: dimensioni più differenziate, preferenze salvate preservate, area minima di tocco 48 dp e stellina proporzionata.

| Scelta | Prima (dp) | Dopo (dp) | Selezionato prima → dopo (dp) |
|---|---:|---:|---:|
| Piccolo | 24 | 24 | 40,5 → 40,5 |
| Medio | 32 | 36 | 54 → 60,75 |
| Grande | 40 | 52 | 67,5 → 87,75 |
| Molto grande | 48 | 72 | 81 → 121,5 |

## Verifiche e limiti

Dettagli e distinzione fra prove automatiche, statiche e dispositivi: [VERIFICATION_v0.47.md](VERIFICATION_v0.47.md).
Non è stata riprodotta la segnalazione sul telefono: sono stati isolati e corretti difetti verificabili nel recupero e nella conservazione dello stato, con test deterministici. Nessun dispositivo ADB disponibile; nessuna prova end-to-end o SOS reale inviato. FCM resta da configurare come documentato nella v0.46.

Non è necessaria una nuova migrazione server: resta valido lo schema 001–021. Il bootstrap remoto può ancora pubblicizzare la v0.46/15: è compatibile con questo client e non è stato modificato per promuovere una pre-release a versione stabile.

APK: [WhereWeAre-v0.47.apk](https://github.com/Skyzzato/WhereWeAre/releases/download/v0.47/WhereWeAre-v0.47.apk). Checksum, firma, commit esatto e risultati nei file BUILD_PROVENANCE.json, BUILD_REPORT.json e SHA256SUMS.txt allegati.
