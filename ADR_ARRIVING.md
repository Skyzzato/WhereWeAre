# Rilevamento “Sta arrivando” — sperimentale disattivato

ARRIVING_EXPERIMENTAL_ENABLED=false. La UI Luoghi e regole indica lo stato;
nessuna regola, raccolta GPS o notifica automatica è collegata al rilevatore.
Il solo inserimento di ROUTING_ENDPOINT non lo abilita.

È predisposto un rilevatore puro e testato per la futura integrazione:
ETA di un motore reale, posizione precisa recente, accuratezza <=100 m,
minimo tre campioni coerenti su almeno 60 secondi, intervallo massimo 180 s,
ETA e distanza di percorso in diminuzione, spostamento oltre l’incertezza GPS,
avvicinamento geometrico alla destinazione. La distanza in linea d’aria è
soltanto una verifica di coerenza, mai il sostituto dell’ETA.

Il superamento della soglia (default 15 minuti) produce un solo candidato;
riarmo con isteresi ETA +5 minuti e cooldown 30 minuti. Dati assenti/stale o
non coerenti azzerano la sequenza. Non sono ancora notifiche agli utenti.

Prima di attivare: endpoint approvato e monitorato, associazione certa tra
risposta routing e fix, consenso/visibilità della regola, persistenza del
cooldown e dedup server, test su percorsi veri anche montani. Si prosegue con
i blocchi indipendenti senza trasformare un’euristica di distanza in ETA.
