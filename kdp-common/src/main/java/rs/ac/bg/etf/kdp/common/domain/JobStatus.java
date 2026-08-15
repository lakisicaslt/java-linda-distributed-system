package rs.ac.bg.etf.kdp.common.domain;

/**
 * Status of a job as defined by the project specification.
 */
public enum JobStatus {
    READY, // server primio posao
    SCHEDULED, // posao se dodeljuje
    RUNNING, // worker ga izvrsava
    DONE, // uspesno zzavrsen
    FAILED, // nije izvrsavanje uspelo
    ABORTED; // korisnik odustao

    public boolean isTerminal() {
        return this == DONE || this == ABORTED;
    } // da li moze da se menja stanje
}
