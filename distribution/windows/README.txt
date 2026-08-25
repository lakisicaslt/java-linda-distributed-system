JAVA LINDA - LABORATORIJSKI PAKET
================================

Preduslov: JDK 17 ili noviji mora biti dostupan preko komande java.

Pokretanje GUI aplikacija:
1. start-server.bat
2. start-worker.bat
3. start-client.bat

Worker bez GUI-ja:
start-worker-headless.bat [server-host] [server-port] [capacity]

Podrazumevane vrednosti su 127.0.0.1, 5050 i broj logickih procesora.

Konfiguracije za javne testove nalaze se u direktorijumu configurations:
- valid-two-jobs.properties: dva regularna posla
- long-running.properties: gasenje klijenta ili workera
- invalid.properties: neispravna konfiguracija
- large-files.properties: dve datoteke od po 64 MB
- linda-two-jobs.properties: deljeni prostor torki
- eval-job.properties: udaljeni eval zadatak

Za test velikih datoteka prvo pokrenuti:
powershell -ExecutionPolicy Bypass -File .\create-large-files.ps1

Linda biblioteka koju treba izabrati u worker GUI-ju nalazi se pored skripti:
kdp-linda.jar

Batch ID treba sacuvati posle svakog slanja. On omogucava proveru statusa i
preuzimanje rezultata nakon ponovnog pokretanja klijenta.
