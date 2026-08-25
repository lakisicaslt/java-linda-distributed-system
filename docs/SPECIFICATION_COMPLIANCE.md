# Usklađenost sa specifikacijom projekta

Ovaj pregled prati zahteve iz `KDP_PROJEKAT_2026_avg-sep.pdf` i javne scenarije iz
`KDP_JavniTestovi.pdf`. Služi kao kontrolna lista pred odbranu; ne menja originalni
tekst zadatka.

## Funkcionalni zahtevi

| Zahtev | Realizacija | Provera | Status |
|---|---|---|---|
| Centralni server, radna stanica i korisnički program | Moduli `kdp-server`, `kdp-worker` i `kdp-client` | Pokretanje tri zasebna procesa iz `dist` paketa | Ispunjeno |
| Java posao zapakovan u JAR | Klijent šalje JAR, server ga čuva i prosleđuje workeru, a `kdp-runner` ga pokreće u zasebnom JVM-u | `submitsSchedulesExecutesAndStoresResult` i laboratorijski test 1 | Ispunjeno |
| JVM i programski argumenti | `JobSpecification` čuva obe liste; runner ih dodaje komandi i na kraj prosleđuje putanju do Linda biblioteke | `JavaTaskRunnerTest` | Ispunjeno |
| Najviše 6 ulaznih i 6 izlaznih datoteka | Ograničenja se proveravaju u zajedničkom modelu, parseru i serverskom dekoderu | `JobSpecificationTest`, `JobConfigurationParserTest`, javni test 6 | Ispunjeno |
| Parametri kroz GUI ili tekstualnu datoteku | Klijentski Swing formular i UTF-8 `properties` konfiguracija | `JobFormDataTest`, `JobConfigurationParserTest` i ručna GUI proba | Ispunjeno |
| Paralelan prijem više poslova | Server koristi poseban handler po konekciji, a scheduler poštuje prijavljeni kapacitet workera | `TaskSchedulerTest` i test skupa od dva posla | Ispunjeno |
| Status i naknadno preuzimanje rezultata | Stabilan Batch ID omogućava novu klijentsku konekciju, status i preuzimanje izlaza | `submitsSchedulesExecutesAndStoresResult` | Ispunjeno |
| `stdout`, `stderr` i izlazne datoteke | Worker strimuje sve rezultate serveru pre konačnog statusa | `WorkerClientIntegrationTest` i `JobClientIntegrationTest` | Ispunjeno |
| Prekid klijenta ne prekida posao | Server ne vezuje životni ciklus posla za konekciju kojom je predat | Ponovno povezivanje u `submitsSchedulesExecutesAndStoresResult` | Ispunjeno |
| Detekcija prestanka rada workera | Heartbeat i serverski liveness monitor; prekid veze odmah oslobađa rezervaciju | `WorkerLivenessMonitorTest`, `WorkerClientIntegrationTest` | Ispunjeno |
| Korisnik bira retry ili abort | Monitor konekcija šalje obaveštenje, a server prihvata eksplicitnu odluku | `appliesClientDecisionAfterWorkerDisconnects` | Ispunjeno |
| Nedostupan korisnik prekida ceo posao | Ako nema aktivnog monitora pri otkazu workera, posao prelazi u `ABORTED` | `abortsJobWhenNoClientIsMonitoringFailures` | Ispunjeno |
| Registracija platforme i kapaciteta workera | Registracija sadrži hostname, OS, Java verziju i broj paralelnih zadataka | `WorkerProtocolCodecTest`, `WorkerClientIntegrationTest` | Ispunjeno |
| Propisani statusi | `READY`, `SCHEDULED`, `RUNNING`, `DONE`, `FAILED`, `ABORTED` | Testovi domena i schedulera | Ispunjeno |
| Serverski log | Za svaku promenu upisuje vreme prijema, Job ID, worker, vreme završetka i status | `ServerJobLoggerTest` i `server-data/server.log` | Ispunjeno |
| Isključivo Java NET komunikacija | Produkcioni kod koristi `Socket` i `ServerSocket`; nema mrežnog framework-a | Pregled izvornog koda i Maven zavisnosti | Ispunjeno |
| Tri GUI aplikacije | Swing GUI za server, worker i klijent | `ServerGuiMain`, `WorkerGuiMain`, `ClientGuiMain` | Ispunjeno |
| Worker bez GUI-ja | Konzolni `WorkerMain` i `start-worker-headless.bat` | Pokretanje iz `dist` paketa | Ispunjeno |

## Linda zahtevi

| Zahtev | Realizacija i dokaz | Status |
|---|---|---|
| Tačan paket i interfejs | `rs.ac.bg.etf.kdp.Linda extends Serializable` sa metodama `out`, `in`, `inp`, `rd`, `rdp`, `eval` | Ispunjeno |
| Zabrana `null` polja u `out` | Validacija u lokalnoj i udaljenoj implementaciji | Ispunjeno |
| Blokirajući `in` i `rd` | `Condition` u lokalnom prostoru; serverske operacije se izvršavaju van connection niti | Ispunjeno |
| Neblokirajući `inp` i `rdp` | Trenutni povrat `false` kada nema torke | Ispunjeno |
| Uklanjanje kod `in`/`inp`, čuvanje kod `rd`/`rdp` | `LocalLindaTest` proverava obe semantike | Ispunjeno |
| Deljeni prostor unutar skupa i izolacija skupova | Registry ima po jedan prostor za svaki Batch ID | `ServerLindaIntegrationTest` i demo sa dva posla | Ispunjeno |
| Distribuirani `eval` | Serijalizovani `Runnable` postaje novi zadatak istog posla i deli isti prostor torki | `executesLindaEvalOnAnotherAvailableWorker` | Ispunjeno |
| Lokalni `eval` | `LocalLinda.eval` pokreće imenovanu nit | `LocalLindaTest.evalStartsNamedThread` | Ispunjeno |

## Javni laboratorijski testovi

| Test | Dokaz pre laboratorije | Status |
|---|---|---|
| 1. Dva ispravna posla i rezultati | Dva posla iz `valid-two-jobs.properties` završila su sa `DONE` i tačnim izlazima | Prošlo lokalno |
| 2. Prestanak rada klijenta | Predaja i kasniji status/preuzimanje koriste različite konekcije u integracionom testu | Automatizovano prošlo |
| 3. Prestanak rada workera | Test gasi worker, prima obaveštenje, pokreće zamenu i proverava retry/abort | Automatizovano prošlo |
| 4. Besmislene Telnet komande | Raw TCP podaci ne prekidaju server; naredna dva posla završavaju sa `DONE` | Prošlo lokalno |
| 5. Klijent povezan na web server | HTTP odgovor se odbija kao pogrešan protokol kontrolisanim `IOException` | Automatizovano prošlo |
| 6. Neispravna konfiguracija | Sedam ulaza se odbija pre mrežnog slanja; serverski log ostaje nepromenjen | Prošlo lokalno |
| 7. Dva fajla veća od 60 MB | Dva fajla od 64 MB prošla su ceo tok, rezultat navodi tačne veličine i nema `.part` fajlova | Prošlo lokalno |

## Granice rešenja

- Stanje poslova se čuva dok centralni server radi. Specifikacija traži ponovno
  povezivanje klijenta, ali ne traži oporavak nakon gašenja samog servera.
- Komunikacija nije šifrovana niti autentifikovana, jer to nije zahtev zadatka.
- `Runnable` prosleđen metodi `eval` mora da bude serijalizabilan i njegova klasa
  mora da se nalazi u korisničkom JAR-u.
- Posao koji worker prekine ne ponavlja se automatski. To je namerno: odluku donosi
  dostupan korisnik, a bez dostupnog korisnika ceo posao se prekida.
