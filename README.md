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
prosleđuje JVM i programske argumente. Poslati JAR i Linda biblioteka nalaze se na
classpath-u novog procesa, a putanja do Linda biblioteke dodaje se i kao poslednji
programski argument. `stdout` i `stderr` čuvaju se odvojeno u direktorijumu
`.kdp`, a izlazni kod procesa određuje da li je izvršavanje bilo uspešno. Uspešan
proces mora da napravi sve izlazne datoteke koje je korisnik naveo.

Server posle potvrđenog prenosa datoteka šalje workeru `EXECUTE_TASK` sa
specifikacijom komande. Worker izvršavanje prebacuje na poseban skup niti, pa mrežna
nit i heartbeat ostaju aktivni dok korisnički JAR radi. Po završetku se status
automatski prijavljuje serveru. Worker pre konačnog statusa šalje deklarisane
izlazne datoteke, `stdout` i `stderr` serveru i čeka potvrdu prijema za svaku od
njih. Server rezultate čuva odvojeno po zadatku u `server-data/results`.

Klijentski modul sada ima mrežni `JobClient` koji serveru šalje jednu ili više
specifikacija posla, dobija stabilne identifikatore i zatim strimuje odgovarajuće
JAR i ulazne datoteke. I klijent i server proveravaju da imena lokalnih datoteka
odgovaraju deklaraciji posla. Server primljene poslove čuva u `server-data/jobs`,
odvojeno po Job ID-ju, i smatra ih kompletnim tek kada primi JAR i sve deklarisane
ulazne datoteke. Kompletan posao automatski ulazi u scheduler. Kada worker prihvati
njegov korenski zadatak, server mu šalje sačuvani JAR i ulaze, čeka potvrdu svakog
fajla i tek zatim šalje `EXECUTE_TASK`. Statusi korenskog zadatka istovremeno menjaju
status posla kroz `READY`, `SCHEDULED`, `RUNNING`, `DONE` ili `FAILED`.

Parametri celog skupa poslova mogu da se zadaju UTF-8 `properties` datotekom.
`JobConfigurationParser` proverava sva polja i lokalne datoteke, a `ClientMain`
učitava konfiguraciju i predaje skup serveru. Relativne putanje računaju se prema
direktorijumu konfiguracije. Tačan format i primer nalaze se u
[`docs/CLIENT_CONFIGURATION.md`](docs/CLIENT_CONFIGURATION.md).

## Grafičke aplikacije

Implementirana su tri minimalistička Swing interfejsa. `ClientGuiMain` omogućava
zadavanje jednog ili više poslova kroz vođeni formular ili tekstualnu konfiguraciju,
automatsko praćenje statusa, odluku nakon otkaza radne stanice i preuzimanje
rezultata. `ServerGuiMain` pokreće server i prikazuje radne stanice, poslove i log.
`WorkerGuiMain` povezuje radnu stanicu i prikazuje njeno trenutno zauzeće.

Mrežne operacije se ne izvršavaju na Swing EDT niti, tako da interfejs ostaje
odzivan dok čeka server ili prenosi datoteke. Konzolni `WorkerMain` ostaje potpuno
funkcionalan za obavezni headless način rada. Detaljno uputstvo nalazi se u
[`docs/GUI_USAGE.md`](docs/GUI_USAGE.md).

Posle predaje klijent ne mora da ostane povezan. `JobClient` može kasnije da otvori
novu CLIENT sesiju i pomoću sačuvanog Batch ID-ja zatraži trenutni status svih
poslova iz te grupe. Odgovor sadrži Job ID, status i razlog greške za `FAILED`
posao, pa ponovno povezivanje ne zavisi od prethodne TCP konekcije.

Kada svi poslovi iz grupe dostignu konačan status, klijent može preko nove sesije
da preuzme rezultate pozivom `downloadBatchResults`. Server prvo šalje manifest,
a zatim u blokovima strimuje deklarisane izlazne datoteke, `stdout` i `stderr`.
Klijent proverava svaki prenos prema manifestu i čuva ga u direktorijumu izabranom
pri pozivu, odvojeno po Job ID-ju u poddirektorijumima `outputs` i `streams`.

Ako se worker isključi tokom izvršavanja, posao dobija status `FAILED` i razlog
`Worker disconnected`. Klijent može da zadrži trajnu vezu pozivom `monitorBatch` i
preko dobijenog `JobFailureMonitor` objekta sačeka obaveštenje o konkretnom poslu.
Tada bira dalji tok pozivom `abortFailedJob` ili `retryFailedJob`. Prva odluka
postavlja posao na `ABORTED`, dok druga vraća prekinuti zadatak u `READY`; scheduler
ga ne ponavlja samostalno i novi pokušaj dobija sledeći redni broj. Ako posao ima
više aktivnih `eval` zadataka, ponavlja se samo neuspeli deo, dok ostali nastavljaju
rad. Odluka `abort` šalje aktivnim workerima zahtev za prekid, gasi njihove
korisničke Java procese i oslobađa rezervisani kapacitet. Ako nijedan klijent ne
prati Batch, ili se poslednji monitor isključi pre odluke, server automatski
prekida ceo posao.

Centralni server vodi evidenciju životnog ciklusa poslova u fajlu
`server-data/server.log`. Za svaku promenu statusa upisuju se vreme prijema, Job ID,
hostname dodeljene radne stanice, vreme završetka i trenutni status posla. Svaki novi
start servera nastavlja da dopisuje u postojeći log.

## Linda prostor torki

Posao dobija Linda objekat pozivom `LindaFactory.getLinda()` iz paketa
`rs.ac.bg.etf.kdp`. Implementirani su `out`, `in`, `inp`, `rd` i `rdp` iz interfejsa
zadatog tekstom projekta. Torka je niz stringova; `out` ne prihvata `null` polja,
dok `null` u šablonu predstavlja bilo koju vrednost. Poklapanje zahteva isti broj
polja. Uspešna operacija popunjava `null` polja prosleđenog šablona.

`in` i `rd` čekaju dok se ne pojavi odgovarajuća torka. `in` i `inp` je uklanjaju,
dok je `rd` i `rdp` samo čitaju. Sve operacije su bezbedne kada ih istovremeno
poziva više procesa i niti. Poslovi poslati zajedno jednim `SUBMIT_BATCH` zahtevom
dele isti prostor torki, a dve različite grupe imaju odvojene prostore.

Korisnički proces komunicira direktno sa centralnim serverom standardnim Java NET
socketima. Worker mu pri pokretanju interno prosleđuje adresu servera, Batch ID i
Task ID; korisnički kod ne mora da poznaje te podatke.

Metoda `eval(name, runnable)` serijalizuje prosleđeni objekat i od njega pravi novi
`EVAL` zadatak istog posla. Scheduler ga dodeljuje slobodnom workeru, koji dobija
originalni korisnički JAR i ulazne datoteke, a zatim pokreće `Runnable` u zasebnom
JVM-u. Novi zadatak koristi isti Linda prostor kao roditelj. Konkretna klasa objekta
prosleđenog metodi `eval` zato mora da implementira i `Serializable`. Posao prelazi
u `DONE` tek kada se završe korenski i svi njegovi `eval` zadaci. Standardni izlazni
tokovi `eval` zadataka takođe se čuvaju i vraćaju klijentu.

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
Swing interfejs koristi isti `WorkerClient`, tako da mrežna logika nije duplirana.
