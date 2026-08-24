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
