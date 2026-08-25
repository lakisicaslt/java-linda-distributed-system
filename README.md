# Java Linda Distributed System

Projekat iz predmeta Konkurentno i distribuirano programiranje.

Cilj je izrada distribuiranog sistema koji prima Java poslove, raspoređuje ih na dostupne radne stanice, prati njihovo izvršavanje i omogućava udaljenu sinhronizaciju po uzoru na C-Linda biblioteku.

## Tehnološke odluke

- Java 17 je ciljna verzija bytecode-a; razvojno okruženje može koristiti JDK 21.
- Maven multi-module projekat.
- Mrežna komunikacija isključivo pomoću Java NET API-ja.
- Swing za grafičke korisničke interfejse.
- Radna stanica podržava GUI i headless režim.
- Bez eksternih runtime framework-a za mrežnu komunikaciju.

## Moduli

- `kdp-common` - zajednički model, mrežne poruke, konfiguracija i validacija.
- `kdp-linda` - javni Linda API i udaljena implementacija.
- `kdp-server` - centralni server, scheduler, tuple-space i serverski GUI.
- `kdp-runner` - izolovano izvršavanje korisničkih i `eval` zadataka.
- `kdp-worker` - radna stanica sa GUI i headless načinom rada.
- `kdp-client` - predaja poslova, praćenje statusa i preuzimanje rezultata.
- `kdp-demo-jobs` - demonstracioni poslovi za integraciono testiranje.

## Izgradnja

```powershell
mvn clean test
```

## Trenutno funkcionalan tok

Centralni server prihvata Java NET konekcije i proverava verziju protokola i ulogu
udaljenog programa. Headless radna stanica može da se poveže, registruje svoje
osnovne podatke i kapacitet, a zatim periodično šalje heartbeat. Server je vodi kao
`ONLINE` dok je konekcija aktivna i kao `OFFLINE` nakon prekida veze.

Osnovni scheduler prihvata spremne zadatke, bira online radnu stanicu sa slobodnim
kapacitetom i šalje joj `ASSIGN_TASK`. Worker pamti zadatak i odgovara porukom
`TASK_ACCEPTED` sa istim request ID-jem. Ako konekcija nestane pre pokretanja,
zadatak se automatski vraća u red.

JAR i ulazne datoteke šalju se od servera ka workeru u delovima od najviše 64 KiB.
Worker ih upisuje direktno na disk u direktorijum zadatka i potvrđuje završen prenos.
Nepotpuna privremena datoteka briše se ako se prenos prekine, tako da veliki fajlovi
ne ostaju ni u memoriji ni kao neupotrebljivi privremeni resursi.

Ručna provera iz IntelliJ IDEA:

1. Pokrenuti `rs.ac.bg.etf.kdp.server.ServerMain` iz modula `kdp-server`.
2. Pokrenuti `rs.ac.bg.etf.kdp.worker.WorkerMain` iz modula `kdp-worker`.
3. Worker bez argumenata koristi `127.0.0.1:5050` i broj dostupnih procesora kao kapacitet.

`WorkerMain` prima opcione argumente:

```text
WorkerMain [server-host] [server-port] [capacity]
```

Ovo je potpuno headless pokretanje. Swing interfejs će kasnije koristiti isti
`WorkerClient`, tako da mrežna logika neće biti duplirana.
