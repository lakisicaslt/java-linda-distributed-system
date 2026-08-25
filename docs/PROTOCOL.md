# KDP mrežni protokol, verzija 1

Sva mrežna komunikacija koristi standardne Java NET klase (`Socket` i `ServerSocket`) i binarne tokove `DataInputStream` i `DataOutputStream`.

## Frame

Svaka poruka ima zaglavlje fiksne veličine od 28 bajtova:

| Polje | Veličina | Opis |
|---|---:|---|
| Magic | 4 B | ASCII `KDP1`, odnosno `0x4B445031` |
| Verzija | 2 B | Trenutno `1` |
| Tip poruke | 2 B | Numerički kod iz `MessageType` |
| Request ID | 16 B | UUID za povezivanje zahteva i odgovora |
| Payload length | 4 B | Broj bajtova payload-a |
| Payload | promenljivo | Sadržaj konkretne poruke |

Brojevi se zapisuju Java mrežnim redosledom bajtova (big-endian), što je podrazumevano ponašanje `DataInputStream` i `DataOutputStream`.

## Uspostavljanje sesije

Prva poruka svake ispravne konekcije mora biti `HELLO`. Payload sadrži jedan bajt koji određuje ulogu:

- `1` - CLIENT
- `2` - WORKER
- `3` - LINDA

Server odgovara praznom porukom `HELLO_ACCEPTED` sa istim request ID-jem. Druga vrsta prve poruke, nepoznata uloga ili odgovor sa drugim request ID-jem prekidaju uspostavljanje sesije.

## Ograničenja

- Kontrolni payload: najviše 1 MiB.
- Jedan `FILE_CHUNK`: najviše 64 KiB.
- Negativna i prevelika dužina odbacuje se pre alokacije memorije.
- Veliki fajl se šalje kroz `FILE_BEGIN`, više `FILE_CHUNK` poruka i `FILE_END`.
- Podrazumevani connect i handshake timeout je 5 sekundi.

## Neispravan protokol

Ako prva četiri bajta nisu `KDP1`, konekcija se odbacuje kao pogrešan protokol. Ovo obuhvata Telnet tekst, HTTP odgovor i proizvoljne podatke. Greška pripada samo handleru te konekcije i ne sme zaustaviti serverski accept loop ili druge poslove.

## Uloge i poruke

Nakon handshake-a svaka poruka se proverava prema ulozi konekcije. Na primer, `HEARTBEAT` je dozvoljen na WORKER sesiji, a `SUBMIT_BATCH` na CLIENT sesiji. Pokušaj slanja poruke druge uloge završava se greškom `ROLE_VIOLATION`.

## Registracija radne stanice

WORKER sesija odmah nakon handshake-a šalje `REGISTER_WORKER`. Payload redom sadrži:

| Polje | Zapis |
|---|---|
| Worker ID | UUID, 16 B |
| Hostname | dužina `int`, zatim UTF-8 bajtovi |
| Operativni sistem | dužina `int`, zatim UTF-8 bajtovi |
| Java verzija | dužina `int`, zatim UTF-8 bajtovi |
| Kapacitet | pozitivan `int` |

Svaki tekst može imati najviše 4096 bajtova. Server potvrđuje registraciju praznim
`WORKER_REGISTERED` frame-om sa istim request ID-jem. Ponovljena registracija na
istoj konekciji i istovremene konekcije sa istim worker ID-jem nisu dozvoljene.

Posle registracije worker periodično šalje `HEARTBEAT` čiji payload sadrži njegov
UUID od 16 bajtova. ID mora odgovarati ID-ju registrovanom za tu konekciju. Server
ne šalje odgovor na svaki heartbeat, čime se izbegava nepotreban mrežni saobraćaj.

## Dodela zadatka

Kada worker ima slobodan kapacitet, server mu šalje `ASSIGN_TASK`:

| Polje | Veličina |
|---|---:|
| Task ID | 16 B |
| Job ID | 16 B |
| Tip zadatka (`ROOT = 1`, `EVAL = 2`) | 1 B |
| Redni broj pokušaja | 4 B |

Worker potvrđuje dodelu porukom `TASK_ACCEPTED`. Njen payload je Task ID od 16
bajtova, a request ID mora biti isti kao u odgovarajućoj `ASSIGN_TASK` poruci.
Potvrda ne znači da je korisnički program već pokrenut; za taj događaj postoji
posebna poruka `TASK_STARTED`.

Scheduler ne dodeljuje više zadataka od kapaciteta koji je worker prijavio. Ako se
worker diskonektuje dok je zadatak još u statusu `SCHEDULED`, rezervacija se uklanja
i zadatak se vraća u status `READY` kako bi mogao da bude dodeljen drugom workeru.

## Prenos datoteka

Jedan prenos koristi isti request ID u svim porukama i ima sledeći redosled:

```text
FILE_BEGIN -> FILE_CHUNK ... -> FILE_END -> FILE_ACKNOWLEDGED
```

`FILE_BEGIN` sadrži ID zadatka ili posla kome datoteka pripada, vrstu datoteke,
očekivanu veličinu i jednostavno ime datoteke. Putanje kao `../file` nisu dozvoljene.
Sadržaj svakog `FILE_CHUNK` frame-a ima najviše 64 KiB. TCP čuva redosled poruka,
pa se primljeni bajtovi redom upisuju direktno u privremenu datoteku.

`FILE_END` je ispravan samo kada se broj primljenih bajtova poklapa sa veličinom iz
`FILE_BEGIN`. Tada se privremena datoteka preimenuje i primalac šalje praznu poruku
`FILE_ACKNOWLEDGED`. Ako se veza prekine, nezavršena `.part` datoteka se briše.

Vrste datoteka su:

- `JOB_ARCHIVE` i `INPUT` za smer server -> worker;
- `OUTPUT`, `STANDARD_OUTPUT` i `STANDARD_ERROR` za smer worker -> server.
