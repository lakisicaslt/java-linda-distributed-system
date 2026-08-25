# Konfiguraciona datoteka klijenta

Tekst projekta zahteva unos parametara putem tekstualne datoteke, ali ne propisuje
njen format. U ovom rešenju koristi se UTF-8 Java `properties` datoteka. Jedna
datoteka opisuje jedan skup poslova koji se zajedno šalje serveru.

## Primer

```properties
jobs=2

job.1.jar=files/first-job.jar
job.1.jvmArgument.count=1
job.1.jvmArgument.1=-Xmx512m
job.1.argument.count=1
job.1.argument.1=result.txt
job.1.input.count=1
job.1.input.1=files/input.txt
job.1.output.count=1
job.1.output.1=result.txt

job.2.jar=files/second-job.jar
job.2.argument.count=2
job.2.argument.1=summary.txt
job.2.argument.2=value with spaces
job.2.output.count=1
job.2.output.1=summary.txt
```

Obavezno polje `jobs` predstavlja broj poslova i mora biti između 1 i 256. Za
svaki posao obavezno je samo polje `job.<broj>.jar`. Ostale grupe imaju polje
`count`; ako ono nije navedeno, broj elemenata te grupe je nula.

| Polje | Značenje | Ograničenje |
|---|---|---:|
| `job.N.jar` | lokalna putanja do JAR arhive | datoteka mora postojati |
| `job.N.jvmArgument.count` | broj JVM argumenata | 0-256 |
| `job.N.jvmArgument.I` | jedan JVM argument | redni brojevi od 1 |
| `job.N.argument.count` | broj programskih argumenata | 0-256 |
| `job.N.argument.I` | jedan programski argument | redni brojevi od 1 |
| `job.N.input.count` | broj ulaznih datoteka | 0-6 |
| `job.N.input.I` | lokalna putanja do ulazne datoteke | datoteka mora postojati |
| `job.N.output.count` | broj izlaznih datoteka | 0-6 |
| `job.N.output.I` | ime očekivane izlazne datoteke | samo ime, bez putanje |

Relativne putanje računaju se u odnosu na direktorijum konfiguracione datoteke,
a ne u odnosu na direktorijum iz kog je klijent pokrenut. Pošto znak `\` ima
posebno značenje u `properties` formatu, za Windows putanje treba koristiti `/`
ili zapisati svaki `\` dva puta.

Parser odbija nedostajuća polja, nepoznate ključeve, pogrešne brojeve, indekse koji
nisu navedeni, nepostojeće lokalne datoteke, duplirana imena i više od šest ulaznih
ili izlaznih datoteka. Neispravna konfiguracija se zato ne šalje serveru.

## Pokretanje iz IntelliJ IDEA

Pokreće se klasa `rs.ac.bg.etf.kdp.client.ClientMain` iz modula `kdp-client`.
Programski argumenti su:

```text
<configuration-file> [server-host] [server-port]
```

Host i port nisu obavezni. Podrazumevane vrednosti su `127.0.0.1` i `5050`.
Posle uspešne predaje klijent ispisuje Batch ID i Job ID svakog posla. Te
identifikatore treba sačuvati za kasnije traženje statusa i rezultata.
