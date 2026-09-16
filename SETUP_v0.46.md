# Configurazione v0.46

Le decisioni qui e in ADR_SOS prevalgono sulle specifiche storiche incompatibili: default vicini preselezionato ON con conferma, nessun rinnovo manuale, quota funzionale SOS OFF.

## Migrazione

`supabase/migrations/021_v0_46.sql` è incrementale e transazionale, non distruttiva. Richiede 001–020. Applicata il 16 settembre 2026 nel SQL Editor del progetto `vqvouzpsgbuaddcyitzg`, dopo test PGlite. Verificati sul server bootstrap 0.46/15, `sos_quota_enabled=false`, tabella delle rimozioni e zero fixture residue dopo il test remoto `supabase/tests/v046.sql`.

Non rieseguire 021: crea colonne, tabella e wrapper con nomi fissi. Per un altro server controllare prima schema e migrazioni effettive. Non è stata inventata una registrazione nella cronologia CLI per il deploy tramite SQL Editor.

`private.event_dismissals` conserva `(user_id,event_id)`. RLS abilitata e nessun accesso diretto ai client. `dismiss_event` deriva l’utente dalla sessione, verifica la visibilità dell’evento e notifica solo il suo account. `event_inbox` filtra, deduplica e ordina; `push_job_authorized` esclude i push ancora pendenti. `own_active_sos` conserva l’accesso del mittente alle azioni dell’SOS nascosto. Non interrompe le condivisioni e non modifica i destinatari.

## Riattivazione futura quota

Unica fonte autorevole: `private.nearby_config.sos_quota_enabled`, default `false`. Il client non ha una copia del flag. Per riattivare deliberatamente:

```sql
update private.nearby_config set sos_quota_enabled=true where singleton;
```

Questo riattiva cooldown mittente, quota giornaliera e quota oraria inviti al destinatario con i valori conservati nella stessa tabella. Gli eventi/inviti continuano a essere registrati con flag OFF. Il test v046 verifica entrambi gli stati in transazione e annulla la modifica. Restano sempre autenticazione, RLS, verifica destinatari, lock, ID idempotente, un solo SOS attivo e limite di fan-out.

## Preferenze e disponibilità

`nearby_sos_status.consent_initialized=false` significa nessuna scelta precedente: il passaggio informativo propone ON, ma nessuna iscrizione diventa operativa prima della conferma. Una riga `opted_in=false` è una revoca esplicita e non viene aggiornata automaticamente. Non si usa più `available_until` come rinnovo manuale nella ricerca o nell’accesso. La disponibilità tecnica richiede un fix realmente recente (finestra configurata `fix_seconds`, 900 s), mai un timestamp artificiale. La UI espone la sospensione senza chiedere rinnovi. Con app aperta si acquisisce ogni cinque minuti; in background si usano solo i fix della condivisione ordinaria già attiva. Il solo opt-in vicini non avvia tale condivisione.

La condivisione ordinaria salva `sharing_intent_<account>` separatamente dal servizio. `sharing_status.revision=0` permette di riconoscere un profilo mai inizializzato; revisioni precedenti non zero senza preferenza locale non sono trattate come nuovi profili. OFF esplicito, stop pendente e stop remoto restano rispettati.

FCM resta da configurare secondo SETUP_v0.43. Nessuna chiave privata è inclusa nell’APK o nel repository. APK debug: non sostituire la chiave già usata se si desidera aggiornare le installazioni esistenti.
