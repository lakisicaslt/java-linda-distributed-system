# Priprema za laboratorijsko testiranje

Ovaj dokument prati sedam javnih testova iz dokumenta `KDP_JavniTestovi.pdf`.
Laboratorijski paket se pravi iz korena projekta komandom:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-distribution.ps1
```

Komanda prvo izvršava `mvn clean package`, a zatim pravi:

- direktorijum `dist` spreman za lokalno pokretanje;
- arhivu `target/java-linda-lab.zip` za prenos na druge računare.

Na svakom računaru treba raspakovati ceo ZIP, bez izdvajanja pojedinačnih JAR-ova.
Za rad je potreban JDK 17 ili noviji i dostupna komanda `java`.

## Pokretanje sistema

Na serverskom računaru:

```text
start-server.bat
```

Na svakoj radnoj stanici:

```text
start-worker.bat
```

U worker GUI-ju izabrati `kdp-linda.jar` iz korena raspakovanog paketa. Worker bez
GUI-ja pokreće se komandom:

```text
start-worker-headless.bat [server-host] [server-port] [capacity]
```

Na klijentskom računaru:

```text
start-client.bat
```

Za rad preko mreže umesto `127.0.0.1` unosi se IP adresa serverskog računara. Port
`5050` mora biti dozvoljen u firewall-u.

## Test 1 - Ispravno funkcionisanje

1. Pokrenuti server, najmanje jednu radnu stanicu i klijenta.
2. U klijentu izabrati karticu *Tekstualna konfiguracija*.
3. Učitati `configurations/valid-two-jobs.properties`.
4. Poslati skup i sačuvati prikazani Batch ID.
5. Sačekati da oba posla dobiju status `DONE`.
6. Preuzeti rezultate.

Očekivani fajlovi `result-one.txt` i `result-two.txt` sadrže tekst
`JAVA LINDA DISTRIBUTED SYSTEM`.

## Test 2 - Prestanak rada klijenta

1. Poslati `configurations/long-running.properties` i kopirati Batch ID.
2. Dok je posao `RUNNING`, u Task Manager-u ugasiti Java proces klijenta.
3. Ponovo pokrenuti `start-client.bat`.
4. Uneti sačuvani Batch ID i osvežiti status.
5. Po završetku preuzeti rezultate.

Gašenje klijenta ne sme da prekine izvršavanje posla.

## Test 3 - Prestanak rada radne stanice

1. Poslati `configurations/long-running.properties`.
2. Dok je posao `RUNNING`, u Task Manager-u ugasiti Java proces workera.
3. Klijent treba da prikaže razlog prekida i izbor naredne akcije.
4. Ponovo pokrenuti worker i sačekati da se registruje na serveru.
5. U klijentu izabrati ponavljanje prekinutog dela.
6. Sačekati `DONE` i preuzeti rezultate.

Za drugu varijantu testa umesto ponavljanja izabrati odustajanje i proveriti status
`ABORTED`.

## Test 4 - Provera serverskog protokola

1. Poslati regularan ili dugotrajan posao.
2. Sa drugog terminala pokrenuti `telnet server-ip 5050`.
3. Poslati nekoliko besmislenih linija i prekinuti Telnet vezu.
4. Proveriti da server i postojeći posao nastavljaju rad.
5. Preuzeti rezultat regularnim klijentom.

Telnet klijent mora unapred biti omogućen na laboratorijskom računaru.

## Test 5 - Provera protokola klijenta

1. U klijentu privremeno uneti adresu i port postojećeg web servera.
2. Pokušati operaciju statusa ili slanja.
3. Klijent treba da prikaže kontrolisanu grešku protokola, bez gašenja GUI-ja.
4. Vratiti adresu KDP servera i proveriti regularan posao.

## Test 6 - Kontrola konfiguracione datoteke

1. Učitati `configurations/invalid.properties`.
2. Pokušati slanje.
3. Klijent mora da odbije datoteku jer je navedeno sedam ulaznih datoteka, dok je
   dozvoljeno najviše šest.

Zahtev ne treba da stigne do servera.

## Test 7 - Dve datoteke veće od 60 MB

U korenu raspakovanog paketa prvo izvršiti:

```powershell
powershell -ExecutionPolicy Bypass -File .\create-large-files.ps1
```

Zatim poslati `configurations/large-files.properties`. Posle statusa `DONE`
preuzeti `sizes.txt`. Očekivane veličine su:

```text
big-a.bin=67108864
big-b.bin=67108864
```

Tokom testa treba proveriti da server i worker ostaju odzivni i da se privremeni
`.part` fajlovi ne zadržavaju posle uspešnog prenosa.

## Dodatne Linda provere

Konfiguracija `configurations/linda-two-jobs.properties` proverava da dva posla iz
istog skupa dele prostor torki. Očekivani rezultati su `distributed tuple` kod
potrošača i `sent` kod proizvođača.

Konfiguraciju `configurations/eval-job.properties` treba pokrenuti sa najmanje dve
radne stanice. Očekivani sadržaj izlaza je:

```text
computed remotely|demo calculation
```

Tokom izvršavanja na kartici *Radne stanice* serverskog GUI-ja može se pratiti
zauzeće obe radne stanice. Automatizovani integracioni test dodatno proverava da
korenski i `eval` zadatak nisu dodeljeni istoj radnoj stanici.

## Šta poneti u laboratoriju

- `java-linda-lab.zip` na najmanje dva medijuma;
- kompletan izvorni kod i Git repozitorijum;
- JDK 17 ili noviji i Maven, ili unapred potvrdu da su instalirani;
- IP adrese računara i otvoren TCP port `5050`;
- omogućen Telnet klijent;
- sačuvane komande za izgradnju i headless pokretanje;
- završni pisani rad i elektronsku verziju rešenja.
