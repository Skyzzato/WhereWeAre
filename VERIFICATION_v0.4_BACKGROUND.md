# WhereWeAre — Troviamoci. — verifica background

## Decisione Android

Preservare il foreground service location avviato da UI visibile con permesso
coarse/fine; nessun permesso background aggiunto. Ripristinare solo una sessione
consensuale persistita, con la revisione originale, mai creando consenso dalla
sola presenza di uno stato remoto ON. Uno stop remoto deve prevalere sul recovery.

La distinzione fra foreground access e avvio dal background è documentata da
[Android, tipi di servizio](https://developer.android.com/develop/background-work/services/fgs/service-types)
e [restrizioni di avvio](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
Il [contratto START_STICKY](https://developer.android.com/reference/android/app/Service#START_STICKY)
esclude il restart sticky dalle normali restrizioni di avvio introdotte con Android 12;
non garantisce tempi di ricreazione né superamento delle restrizioni sulle risorse.
Il recovery esplicito avviene soltanto mentre l'Activity è visibile, da qualsiasi tab.
Force-stop e politiche OEM non sono aggirabili con START_STICKY.

Schermo spento non modifica la frequenza FLP impostata dal codice. I ritardi del
sistema non sono una garanzia di intervallo: [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)
può limitare rete e attività. Non introdotti wake lock né esenzioni batteria forzate.
Le verifiche su SDK37 reale rimangono necessarie; Robolectric SDK34 non le sostituisce.

## Origine degli stati

`Snapshot.offline` è un nome storico assegnato quando una lettura REST fallisce:
non dimostra assenza di Internet. Le schermate usano NetworkMonitor.online per
Internet validato, syncFailed per errore REST e realtimeUnavailable per realtime.
Le età dei fix locali/remoti sono indipendenti dagli errori di rete e possono
comparire insieme. Il controller distingue fix in attesa di pubblicazione,
timestamp del fix inviato e momento locale di ricezione dell'ACK.
Quest'ultimo non è un timestamp del server.

## Test manuale riproducibile

Non eseguito: nessun dispositivo né AVD disponibile al momento dell'audit.
Usare due account consenzienti su staging con migrazioni 001–007. Impostare
prima 5 secondi, poi 60 secondi e infine 10 minuti. Per ogni prova annotare
solo tempi, stato UI/servizio, esito delle RPC; non coordinate o token nei log.

1. Avviare dalla Mappa. Prima dell'ACK deve apparire avvio/riconnessione;
   ON deve arrivare dopo conferma. Premere STOP dalla notifica e dalla Mappa.
2. Passare a Persone, Gruppi, Impostazioni, Home Android e a un'altra app.
   Attendere tre intervalli in ogni stato: nessuno stop implicito.
3. Spegnere lo schermo per almeno tre intervalli. Riaccendere e confrontare
   i tempi di invio e ricezione sul secondo dispositivo.
4. Disattivare rete, generare più fix e riattivare. Verificare invio del fix
   più recente, stato in attesa durante l'interruzione e stesso consenso/sessione.
5. Wi-Fi → mobile → Wi-Fi: nessuno stop; eventuale riconnessione distinta da fix vecchio.
6. Rendere indisponibile solo il server, poi solo realtime (proxy di staging):
   messaggi distinti. Una posizione remota vecchia deve essere segnalata anche
   con rete funzionante, senza dichiararla errore di connessione.
7. Ricreare Activity; ricreare processo/servizio con strumenti di debug dove
   Android lo consente. Verificare sessione/revisione conservate e nessuno stop
   automatico della Mappa durante il ripristino. Provare riapertura in ogni tab.
8. Fermare condivisione lato server mentre GPS è disattivato o intervallo 1 ora:
   il servizio deve accorgersene senza aspettare un fix (controllo ogni 30 s circa,
   oltre a tempi rete/sistema), andare OFF e non riattivare lo stato remoto.
9. Avviare un'altra sessione dello stesso account su un secondo telefono: il
   vecchio servizio deve terminare senza inviare uno stop globale alla nuova.
10. Revocare permesso e disattivare/riattivare GPS separatamente. La revoca termina
    la condivisione; GPS indisponibile non deve essere presentato come errore REST.
11. Con telefono collegato, abilitare Doze di test:
    `adb shell dumpsys battery unplug`, `adb shell dumpsys deviceidle force-idle`.
    Verificare il comportamento per almeno tre intervalli. Ripristinare sempre
    `adb shell dumpsys deviceidle unforce` e `adb shell dumpsys battery reset`.
12. Provare force-stop separatamente: nessuna promessa di ripartenza automatica.
    Alla riapertura, una sessione persistita può riprendere solo se il server
    accetta la stessa sessione/revisione e non esiste uno stop pendente.

## Dipendenze di test

Mockito core 5.23.0 ([release](https://github.com/mockito/mockito/releases/tag/v5.23.0),
[licenza MIT](https://github.com/mockito/mockito/blob/main/LICENSE)) consente test
del controller reale con repository simulati. Solo JVM/test, nessun incremento
APK o requisito Android. Non è l'artefatto mockito-android.
kotlinx-coroutines-test 1.11.0 segue la versione coroutine già usata dal progetto;
il tempo virtuale consente di verificare recovery e retry senza attese reali.
