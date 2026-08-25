# Pokretanje i korišćenje grafičkih aplikacija

Sistem ima tri Swing aplikacije sa istim podrazumevanim portom `5050`. Pre prvog
pokretanja iz korena projekta treba izvršiti:

```powershell
mvn clean package
```

Time se, između ostalog, pravi Linda biblioteka
`kdp-linda/target/kdp-linda-1.0-SNAPSHOT.jar` koja je potrebna radnoj stanici.

## 1. Centralni server

U IntelliJ IDEA pokrenuti klasu:

```text
rs.ac.bg.etf.kdp.server.ServerGuiMain
```

Podrazumevana adresa `0.0.0.0` znači da server prihvata veze preko svih mrežnih
interfejsa računara. Port može ostati `5050`. Klikom na **Pokreni server** počinje
prijem veza.

Kartica **Radne stanice** prikazuje povezane računare, njihove platforme, kapacitet
i broj aktivnih zadataka. Kartica **Poslovi** prikazuje identifikator, JAR i trenutni
status svakog posla. Donji deo prozora prikazuje poslednjih 200 redova serverskog
loga.

## 2. Radna stanica

U IntelliJ IDEA pokrenuti klasu:

```text
rs.ac.bg.etf.kdp.worker.WorkerGuiMain
```

Ako je server na istom računaru, adresa ostaje `127.0.0.1`. Za server na drugom
računaru treba uneti njegovu LAN ili WAN adresu. Broj paralelnih poslova određuje
koliko zadataka ova radna stanica može istovremeno da izvršava.

Klikom na **Izaberi...** izabrati napravljenu biblioteku:

```text
kdp-linda/target/kdp-linda-1.0-SNAPSHOT.jar
```

Zatim kliknuti **Poveži radnu stanicu**. U delu *Trenutno stanje* vide se veza,
identifikator stanice, platforma i trenutno zauzeće.

### Pokretanje radne stanice bez GUI-ja

Headless način ostaje dostupan preko klase `WorkerMain`:

```text
WorkerMain [server-host] [server-port] [capacity] [linda-library]
```

Primer argumenata u IntelliJ Run Configuration:

```text
127.0.0.1 5050 4 kdp-linda/target/kdp-linda-1.0-SNAPSHOT.jar
```

## 3. Korisnički program

U IntelliJ IDEA pokrenuti klasu:

```text
rs.ac.bg.etf.kdp.client.ClientGuiMain
```

### Slanje kroz formular

1. Uneti adresu i port servera.
2. U delu *Slanje novih poslova* ostaviti karticu **Unos kroz formular**.
3. Kliknuti **Dodaj posao...** i izabrati izvršni JAR.
4. Po potrebi uneti JVM i programske argumente, po jedan argument u svakom redu.
5. Po potrebi dodati najviše šest ulaznih datoteka.
6. Navesti imena najviše šest izlaznih datoteka koje program treba da napravi.
7. Sačuvati posao. Na isti način može se dodati još poslova u isti skup.
8. Kliknuti **Pošalji poslove**.

### Slanje tekstualnom konfiguracijom

Na kartici **Tekstualna konfiguracija** izabrati `.properties` datoteku i kliknuti
**Pošalji poslove**. Format je opisan u
[`CLIENT_CONFIGURATION.md`](CLIENT_CONFIGURATION.md).

### Praćenje i rezultati

Posle uspešnog slanja program automatski upisuje Batch ID i osvežava statuse na
svake tri sekunde. Ako se radna stanica prekine tokom izvršavanja, prikazuje se
razlog i korisnik bira da li želi da ponovi prekinuti deo, odustane ili odluku
donese kasnije.

Batch ID treba sačuvati. Klijent može da se ugasi, a nakon ponovnog pokretanja taj
ID može da se unese u deo *Praćenje poslova* i proveri klikom na **Osveži status**.

Kada su poslovi završeni, bira se direktorijum i klikne **Preuzmi rezultate**.
Rezultati svakog posla čuvaju se u posebnom direktorijumu označenom njegovim Job
ID-jem.
