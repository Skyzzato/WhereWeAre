# WhereWeAre v0.2 — APK di collaudo

Scarica e installa `WhereWeAre-v0.2-debug.apk` dagli allegati della release. Android 8.0 o successivo (min SDK 26); APK universale con firma debug. Configurato per il progetto Supabase WhereWeAre, senza credenziali amministrative.

Novità: gruppi, avatar privati, timeout di visibilità, impostazioni GPS e controllo versione all'avvio. Incluse le correzioni di upload Storage e delle riletture avatar dopo una revoca.

Verifiche: 12 test Android superati, lint senza errori (45 warning), suite SQL superata e collaudo reale HTTPS/Realtime superato con account temporanei poi eliminati. Testata anche la cancellazione autenticata con avatar e gruppi.

Questa è una prerelease: nessun telefono/emulatore era disponibile. Installazione, GPS, fotocamera, mappe e comportamento in background devono ancora essere collaudati su dispositivi reali. Non è un APK release firmato per Google Play.

Il checksum SHA-256 è nell'allegato `SHA256SUMS.txt`. Dettagli e procedura ripetibile in `VERIFICATION.md` e `README.md`.
