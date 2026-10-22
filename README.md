# Automatic Foto Report – app Android

Scatta foto ai quadri elettrici e le archivia in uno ZIP su una cartella condivisa di un PC della rete aziendale.

## Funzionamento
1. All'avvio si apre la fotocamera posteriore (contatore foto in alto).
2. Dopo ogni scatto compare il popup: **Scatta altra** (la foto resta in memoria e si torna alla fotocamera), **Archivia**, oppure **Scarta** l'ultima foto.
3. **Archivia** → inserisci PC (nome o IP), dominio (facoltativo), utente e password → **Connetti**.
4. Scegli la cartella condivisa dall'elenco (o scrivine il nome), naviga nelle sottocartelle, eventualmente creane una nuova.
5. Scrivi il nome dell'archivio (proposto `Quadro_AAAAMMGG_HHMM`) → **Salva qui**. Lo ZIP contiene `nome_001.jpg`, `nome_002.jpg`, ...
6. A salvataggio riuscito le foto vengono cancellate dal telefono e si riparte con una nuova sessione.

L'app ricorda PC, utente, ultima condivisione e ultima cartella. Le foto non ancora archiviate restano salvate anche se l'app viene chiusa.

## Compilare
1. Apri la cartella `FotoQuadri` con **Android Studio** (Ladybug o successivo).
2. Lascia che Studio sincronizzi Gradle (genera anche lo script `gradlew` se manca).
3. Collega il telefono con il debug USB attivo e premi **Run**, oppure *Build → Build APK(s)*.

Requisiti: Android 8.0 (API 26) o superiore.

## Configurazione lato PC (Windows)
- Condividi la cartella: *Proprietà → Condivisione → Condivisione avanzata*, dando all'utente permesso di **scrittura** (sia in Condivisione sia in Sicurezza/NTFS).
- Il telefono deve essere sulla stessa Wi-Fi/VLAN del PC; il firewall di Windows deve consentire *Condivisione file e stampanti* (porta TCP 445).
- Se il nome del PC non viene risolto dal telefono, usa l'indirizzo IP.
- Account Microsoft: usa l'indirizzo email come utente; account locale: lascia vuoto il dominio o metti il nome del PC.

## Note tecniche
- Fotocamera: CameraX. Rete: SMB2/SMB3 con la libreria `smbj`; elenco condivisioni con `dcerpc` (smbj-rpc). Se l'elenco non è disponibile si può sempre digitare il nome della condivisione.
- Se Gradle non trova `com.rapid7.client:dcerpc:0.12.13`, sostituisci la versione con l'ultima presente su Maven Central (o rimuovi la dipendenza e la funzione `listShares` in `SmbRepository.kt`: resterà l'inserimento manuale).
- La password viene salvata nelle preferenze private dell'app solo se è spuntato "Ricorda password".
