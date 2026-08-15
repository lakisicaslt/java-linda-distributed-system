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

Trenutno je postavljen samo kostur projekta. Funkcionalna implementacija će se dodavati korak po korak.
