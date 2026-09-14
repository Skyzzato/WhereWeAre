# Aggiornamento v0.32

Su un progetto già alla v0.31 eseguire nel SQL Editor soltanto `supabase/migrations/006_v0_32.sql`. La migrazione aggiunge il solo campo opzionale `flare_style_id` ai ritrovi e la RPC compatibile `create_meeting_styled`; i ritrovi esistenti usano stile 1. Eseguire tutto il file in una volta. L'aggiornamento remoto non è stato applicato da questa sessione.

I link invito usano `https://whereweare.app/join/person/<codice>` e `/join/group/<codice>` quando `INVITE_BASE_URL` è configurato; per test locali è supportato `whereweare://person/<codice>` e `whereweare://group/<codice>`. Il dominio HTTPS e `assetlinks.json` non sono presenti nel repository: per App Links verificati occorre pubblicare il dominio e il file Android Digital Asset Links con il certificato di firma distribuito.
