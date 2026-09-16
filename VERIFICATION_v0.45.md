# Verifica v0.45

## Causa e riproduzione

Il 16 settembre 2026 è stata verificata la struttura JSON restituita da `app_metadata()` per i profili del progetto configurato. L'output conteneva solo percorsi/tipi aggregati, senza dati personali: `$.events[].recipients` presentava sia `array` sia `null`. Analisi SQL in transazione con rollback; nessuna migrazione o modifica persistente ai dati.

`supabase/tests/export-android-contract.mjs` applica le migrazioni 001–020 a PGlite locale e usa esclusivamente utenti sintetici. Esporta due risposte `app_metadata`, una con SOS ordinario e una con SOS solo nelle vicinanze, senza destinatari ordinari. I JSON in `app/src/test/resources/contract` sono fixture sintetiche, non esportazioni dei dati utente.

Il test Android usa il serializer predefinito dello stesso client Supabase 3.8.0 impiegato dall'app. Prima della modifica fallisce con `JsonDecodingException: Expected start of the array '[', but had 'n' ... $.events[1].recipients`. Dopo la modifica i destinatari null diventano una lista vuota. Non viene attivata una coercizione globale dei dati.

Il serializer del campo mantiene non-null la lista nel dominio; le liste esistenti rimangono intatte e i formati errati sono respinti. Una lettura metadata riuscita elimina anche l'errore diagnostico della stessa operazione. Il banner resta disponibile per errori reali.

## Comandi

- `node supabase/tests/export-android-contract.mjs`: generazione riuscita delle fixture SQL.
- `.\gradlew.bat -I tools/isolated-v044-build.gradle :app:build :app:testDebugUnitTest --rerun --console=plain`: PASS, 118 test, zero fallimenti/errori/skipped, inclusi i 5 nuovi test del contratto. Lint: zero errori, 66 warning già presenti. Build debug e release completate; distribuito solo APK debug firmato. Il percorso di build isolato mantiene il nome storico v044.

Nessun dispositivo ADB disponibile. Non è dichiarato un collaudo sul telefono dell'utente. L'APK è firmato con la stessa chiave debug della v0.44; versione incrementata a 0.45/14. Nessuna migrazione Supabase necessaria.
