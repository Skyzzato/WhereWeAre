# ADR — Routing v0.4

Stato: adapter Valhalla implementato, servizio di produzione NON configurato.
Valutazione del 15 settembre 2026. Nessun endpoint demo viene utilizzato.

| Aspetto | Valhalla | GraphHopper | OSRM |
|---|---|---|---|
| Piedi/auto/bici | Costing dinamico pedestrian/auto/bicycle | Profili e custom model | Profili Lua foot/car/bicycle preparati sul server |
| Sentieri/montagna | Parametro max_hiking_difficulty collegato a sac_scale OSM | Modelli personalizzabili per preferire/escludere categorie di vie | Personalizzazione del profilo e ricostruzione dati; maggiore lavoro specifico sui sentieri |
| Licenza motore | MIT | Apache 2.0 | BSD 2-clause |
| Hosting | Self-host, manutenzione/import dati e capacità a carico operatore | Self-host oppure servizio commerciale | Self-host, dataset per profilo |
| Costi/limiti | Dipendono dall’hosting, nessuna SLA implicita | API commerciale a crediti con quote per piano | Dipendono dall’hosting, nessuna SLA implicita |
| Chiavi | Dipendono dal gateway scelto | Chiave per servizio commerciale; self-host configurabile | Dipendono dal gateway scelto |
| Android | HTTP/JSON, nessun motore nell’APK | HTTP/JSON, nessun motore nell’APK | HTTP/JSON, nessun motore nell’APK |

Fonti primarie: [Valhalla API](https://valhalla.github.io/valhalla/api/route/api-reference/),
[licenza Valhalla](https://github.com/valhalla/valhalla/blob/master/COPYING),
[GraphHopper API](https://docs.graphhopper.com/openapi/section/explore-our-apis/api-explorer),
[piani GraphHopper](https://www.graphhopper.com/pricing/),
[licenza GraphHopper](https://github.com/graphhopper/graphhopper/blob/master/LICENSE.txt),
[profili OSRM](https://project-osrm.org/docs/v26.4.0/profiles),
[licenza OSRM](https://github.com/Project-OSRM/osrm-backend/blob/master/LICENSE.TXT).
I costi non sono stimabili senza area geografica, traffico, infrastruttura e SLA;
le quote commerciali vanno verificate al momento del contratto.

## Decisione

Valhalla è la prima implementazione di RoutingRepository: la combinazione di
profili dinamici e controllo della difficoltà dei sentieri è adatta al progetto.
È una valutazione tecnica, non un benchmark di qualità sul territorio.
La modalità piedi mantiene difficoltà massima 1; non abilita automaticamente
sentieri alpini tecnici. Copertura OSM, accessi, dislivello e condizioni reali
richiedono verifiche sul campo: l’ETA non dimostra percorribilità o sicurezza.

Configurare in local.properties, prima di compilare una distribuzione:

```
ROUTING_PROVIDER=valhalla
ROUTING_ENDPOINT=https://HOST-APPROVATO/route
```

L’endpoint deve implementare il contratto Valhalla route. Nessun valore reale
è stato impostato. Provider sconosciuto o endpoint assente disabilitano il
routing. Non inserire API key nell’URL o nell’APK: eventuali credenziali del
provider restano in un gateway amministrato. Un servizio che richiede una
sessione gateway dedicata necessita della relativa integrazione prima di
abilitare questa configurazione. HTTPS obbligatorio; redirect disabilitati,
timeout limitati, risposte limitate a 512 KiB e validazione delle unità.

## Privacy e comportamento

Tutti i provider ricevono origine/destinazione: scegliere hosting, retention,
regione e access logging coerenti con l’informativa prima dell’attivazione.
L’adapter invia coordinate e modalità solo su Calcola percorso, senza ID,
nomi, token account o cronologia. Non conserva cache persistenti. Posizioni
approssimate, vecchie oltre 2 minuti o con accuratezza oltre 100 m non vengono
inviate. Restano visibili le distanze autorizzate in linea d’aria.

Il riepilogo Bengala permette piedi/auto/bici e mostra metri di percorso e
minuti stimati restituiti dal provider. Nessuna conversione arbitraria della
distanza in ETA; errore o servizio assente non producono un risultato finto.
Nessuna polyline o navigazione turn-by-turn introdotta in questo blocco.

Prima di abilitare: collaudare il servizio scelto, quote, carico, aggiornamento
OSM e percorsi reali di montagna. I test locali usano trasporto simulato e
validano parsing, profili, assenza richieste non autorizzate e fallback; non
sono una prova di SLA o di accuratezza del motore in produzione.
