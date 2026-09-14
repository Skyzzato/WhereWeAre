# WhereWeAre v0.32

La release introduce 30 stili di bengala tramite un renderer parametrico unico, con preferenza locale persistente e audio sintetizzato per lancio/esplosione. Gli eventi remoti conservano `flare_style_id` (fallback 1 per dati precedenti); in background il sistema usa la notifica sonora del canale Android, mentre in foreground l'audio interno evita duplicazioni.

Il crash avatar è stato reso non bloccante: riferimenti remoti non validi vengono ignorati e le bozze locali sono validate e sanificate all'avvio, con fallback alle iniziali. Il flusso fotocamera/galleria conserva copie private controllate dall'app.

La migrazione `006_v0_32.sql` è incrementale e compatibile con la v0.31. I deep link tipizzati persona/gruppo sono predisposti; il dominio HTTPS/App Links verificato richiede ancora infrastruttura esterna. La posizione di Modifica gruppo è nella testata del dettaglio. Restano da completare i test su dispositivo reale per camera, audio, background e due account.
