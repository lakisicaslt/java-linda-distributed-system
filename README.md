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
osnovne podatke i kapacitet, a zatim periodično šalje heartbeat. Server periodično
proverava vreme poslednjeg heartbeat-a i prekida vezu sa radnom stanicom koja je
prestala da se javlja.

Osnovni scheduler prihvata spremne zadatke, bira online radnu stanicu sa slobodnim
kapacitetom i šalje joj `ASSIGN_TASK`. Worker pamti zadatak i odgovara porukom
`TASK_ACCEPTED` sa istim request ID-jem. Worker može zatim da prijavi početak,
uspešan završetak ili neuspeh zadatka. Server proverava da status prijavljuje upravo
worker kome je zadatak dodeljen i po završetku oslobađa njegov kapacitet. Ako se
radna stanica isključi tokom izvršavanja, njen zadatak prelazi u `FAILED` i ne
ponavlja se automatski. Odluka o ponavljanju prekinutog dela ili odustajanju od
celog posla pripada korisniku, kao što zahteva tekst projekta.

JAR i ulazne datoteke šalju se od servera ka workeru u delovima od najviše 64 KiB.
Worker ih upisuje direktno na disk u direktorijum zadatka i potvrđuje završen prenos.
Nepotpuna privremena datoteka briše se ako se prenos prekine, tako da veliki fajlovi
ne ostaju ni u memoriji ni kao neupotrebljivi privremeni resursi.

Modul `kdp-runner` može da pokrene primljeni JAR kao zaseban Java proces. Pre
pokretanja proverava JAR, Linda biblioteku i sve deklarisane ulazne datoteke, zatim
prosleđuje JVM i programske argumente. Putanja do Linda biblioteke dodaje se kao
poslednji programski argument. `stdout` i `stderr` čuvaju se odvojeno u direktorijumu
`.kdp`, a izlazni kod procesa određuje da li je izvršavanje bilo uspešno. Uspešan
proces mora da napravi sve izlazne datoteke koje je korisnik naveo.

Server posle potvrđenog prenosa datoteka šalje workeru `EXECUTE_TASK` sa
specifikacijom komande. Worker izvršavanje prebacuje na poseban skup niti, pa mrežna
nit i heartbeat ostaju aktivni dok korisnički JAR radi. Po završetku se status
automatski prijavljuje serveru. Izlazne datoteke su u ovoj fazi prikupljene na
workeru; njihov povratni prenos ka serveru je sledeći korak.

Ručna provera iz IntelliJ IDEA:

1. Pokrenuti `rs.ac.bg.etf.kdp.server.ServerMain` iz modula `kdp-server`.
2. Pokrenuti `rs.ac.bg.etf.kdp.worker.WorkerMain` iz modula `kdp-worker`.
3. Worker bez argumenata koristi `127.0.0.1:5050` i broj dostupnih procesora kao kapacitet.

`WorkerMain` prima opcione argumente:

```text
WorkerMain [server-host] [server-port] [capacity] [linda-library]
```

Četvrti argument je putanja do lokalnog `kdp-linda.jar`; podrazumevana vrednost je
`kdp-linda.jar` u trenutnom direktorijumu. Ovo je potpuno headless pokretanje.
Swing interfejs će kasnije koristiti isti `WorkerClient`, tako da mrežna logika neće
biti duplirana.
