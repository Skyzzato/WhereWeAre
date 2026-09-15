# QR persone e gruppi

Decisione del Blocco 05: CameraX 1.6.2 per fotocamera/lifecycle, ZXing core 3.5.4
per generare e leggere esclusivamente QR. Decodifica in memoria sul dispositivo;
nessun invio di immagini, nessun download iniziale di modelli, nessun server.

Valutazione prima di introdurre dipendenze:

| Opzione | Offline e privacy | Manutenzione e costo |
| --- | --- | --- |
| CameraX + ZXing core | Inclusi nella app; permesso CAMERA al bisogno | CameraX gestisce lifecycle e compatibilità dispositivi; core ZXing maturo in manutenzione, ultima release verificata 3.5.4. Incremento APK da misurare dopo build. |
| ZXing Android Embedded | Incluso, offline, CAMERA | Wrapper 4.3.0 con release molto meno recente; aggiunge la propria UI/camera rispetto alla UI Compose esistente. |
| ML Kit bundled + CameraX | Decodifica locale; modello incluso | Motore/modello aggiuntivo per una sola simbologia e serve comunque generazione QR. |
| Google Code Scanner | Decodifica locale, UI di Play Services | Modulo gestito da Play Services: disponibilità al primo uso offline non garantita senza installazione preventiva. |

Fonti primarie: [CameraX release](https://developer.android.com/jetpack/androidx/releases/camera),
[LifecycleCameraController](https://developer.android.com/reference/androidx/camera/view/LifecycleCameraController),
[ZXing release](https://github.com/zxing/zxing/releases),
[ZXing licenza Apache 2.0](https://github.com/zxing/zxing/blob/master/LICENSE),
[Embedded release](https://github.com/journeyapps/zxing-android-embedded/releases).
CameraX usa la licenza Apache 2.0 di AndroidX. Le versioni scelte supportano
minSdk26 e il toolchain Java17/21 esistente; la build deve verificarne la risoluzione.

Build verificata: APK debug precedente 68.522.663 byte; con QR 72.761.815 byte.
Incremento 4.239.152 byte (4,04 MiB, circa 6,2%). Misura sullo stesso tipo di
APK locale, non una stima della dimensione download dello store.

Payload: `whereweare://person/COD-ICE` o `whereweare://group/COD-ICE`, validato
con il parser tipizzato esistente. Mai UUID, credenziali o link arbitrari.
La scansione passa per InviteStore e la normale navigazione degli inviti:
un QR Gruppo letto da Persone apre il flusso Gruppo e viceversa. Nessuna
accettazione automatica del consenso GPS.

Lifecycle: fotocamera legata alla schermata e rilasciata uscendo. La finestra QR
usa fondo bianco, contrasto nero, X e Back; luminosità e keep-screen-on sono
ripristinati sia quando viene nascosta sia quando viene eliminata.
L'aggiunta di CAMERA richiede di proteggere anche il precedente intent foto
avatar con richiesta contestuale, per evitare regressioni Android.

Prima della distribuzione: test fisici con permesso negato/revocato, cambio app,
Back, QR ruotati, poca luce, scanner aperto da entrambe le pagine, invalidi,
luminosità automatica/manuale e foto avatar. Nessun telefono disponibile durante
l'audit: queste prove non possono essere sostituite dai test di codec e lifecycle.
