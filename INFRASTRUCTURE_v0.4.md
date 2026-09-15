# Scheduler e routing: stato verificabile v0.4

## Scheduler
È un servizio server che esegue periodicamente un lavoro anche a telefono spento.
Supabase Cron conserva job e risultati di esecuzione: https://supabase.com/docs/guides/cron
Nel repository esistono il dispatcher send-meeting-push e le istruzioni per
invocarlo ogni minuto, ma non prove di un job remoto attivo e monitorato.
La configurazione locale contiene credenziali pubbliche del client, non accesso
amministrativo a Cron: non è possibile attestare che lo scheduler remoto manchi
o funzioni. Per verificarlo servono job attivo, esecuzioni recenti riuscite,
esito HTTP/dispatcher e consegna reale su un secondo telefono.
La sola riuscita di un job SQL che avvia HTTP non prova la consegna FCM.

Verifica remota in sola lettura del 16 settembre 2026: app_bootstrap risponde
HTTP 200 e dichiara versione 0.33/8; le feature restituite sono meeting_points,
invite_links e client_analytics. Non dichiara device_status. Questo conferma che
il backend raggiungibile non espone il bootstrap incrementale della v0.4;
non rivela l’elenco dei job Cron né prova la loro assenza. Prima di usare le
nuove funzioni server occorre verificare/applicare le migrazioni 008–017 e
distribuire il dispatcher aggiornato secondo SETUP_v0.4.md.

Gli avvisi di mancato arrivo richiedono inoltre logica server per scadenze,
proroghe, annullamenti, deduplicazione e monitoraggio. Tale funzione NON è
implementata operativamente: accendere un cron per le push non la completa.

## Routing
Le mappe MapLibre/OpenFreeMap visualizzano la cartografia. Il routing calcola
un percorso su strade o sentieri e stima distanza e tempo di viaggio.
L’adapter Valhalla è implementato; nella configurazione e nell’APK pubblicato
ROUTING_PROVIDER e ROUTING_ENDPOINT sono vuoti. Non esiste quindi un servizio
di routing collegato alla build. Le distanze in linea d’aria restano disponibili.
Documentazione: https://valhalla.github.io/valhalla/api/route/api-reference/

Per attivarlo occorre un endpoint HTTPS Valhalla gestito, copertura geografica
corretta, disponibilità/limiti verificati e collaudo; poi ricompilare con
ROUTING_PROVIDER=valhalla e ROUTING_ENDPOINT=https://HOST/route. Non è stato
scelto un server demo per trasmettergli le posizioni degli utenti.
Il rilevatore “Sta arrivando” resta disabilitato anche dopo la configurazione:
richiede ancora integrazione operativa e prove sul territorio.
