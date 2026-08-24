package rs.ac.bg.etf.kdp.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void registeredWorkerStartsOnlineWithItsPlatformInformation() {
        UUID workerId = UUID.randomUUID();
        Worker worker = new Worker(
                workerId, "worker-1", "Windows 11", "21.0.3", 2, REGISTERED_AT);

        assertEquals(workerId, worker.getId());
        assertEquals("worker-1", worker.getHostname());
        assertEquals("Windows 11", worker.getOperatingSystem());
        assertEquals("21.0.3", worker.getJavaVersion());
        assertEquals(2, worker.getCapacity());
        assertEquals(WorkerStatus.ONLINE, worker.getStatus());
        assertEquals(REGISTERED_AT, worker.getLastHeartbeatAt());
        assertEquals(2, worker.getAvailableSlots());
    }

    @Test
    void enforcesParallelTaskCapacity() {
        Worker worker = newWorker(2);
        UUID firstTask = UUID.randomUUID();
        UUID secondTask = UUID.randomUUID();

        worker.reserveTask(firstTask);
        worker.reserveTask(secondTask);

        assertFalse(worker.hasCapacity());
        assertEquals(0, worker.getAvailableSlots());
        assertThrows(IllegalStateException.class,
                () -> worker.reserveTask(UUID.randomUUID()));

        worker.releaseTask(firstTask);
        assertTrue(worker.hasCapacity());
        assertEquals(1, worker.getAvailableSlots());
    }

    @Test
    void rejectsDuplicateAndUnknownTaskReservations() {
        Worker worker = newWorker(2);
        UUID taskId = UUID.randomUUID();
        worker.reserveTask(taskId);

        assertThrows(IllegalArgumentException.class, () -> worker.reserveTask(taskId));
        assertThrows(IllegalArgumentException.class,
                () -> worker.releaseTask(UUID.randomUUID()));
    }

    @Test
    void suspectedAndOfflineWorkersCannotReceiveTasks() {
        Worker suspectedWorker = newWorker(1);
        suspectedWorker.markSuspected(REGISTERED_AT.plusSeconds(5));
        assertEquals(WorkerStatus.SUSPECTED, suspectedWorker.getStatus());
        assertFalse(suspectedWorker.hasCapacity());

        suspectedWorker.markOffline(REGISTERED_AT.plusSeconds(10));
        assertEquals(WorkerStatus.OFFLINE, suspectedWorker.getStatus());
        assertEquals(0, suspectedWorker.getAvailableSlots());
        assertThrows(IllegalStateException.class,
                () -> suspectedWorker.reserveTask(UUID.randomUUID()));
    }

    @Test
    void heartbeatBringsSuspectedWorkerBackOnline() {
        Worker worker = newWorker(1);
        worker.markSuspected(REGISTERED_AT.plusSeconds(5));

        worker.recordHeartbeat(REGISTERED_AT.plusSeconds(6));

        assertEquals(WorkerStatus.ONLINE, worker.getStatus());
        assertEquals(REGISTERED_AT.plusSeconds(6), worker.getLastHeartbeatAt());
        assertTrue(worker.hasCapacity());
    }

    @Test
    void preservesActiveTasksWhenWorkerGoesOffline() {
        Worker worker = newWorker(2);
        UUID taskId = UUID.randomUUID();
        worker.reserveTask(taskId);

        worker.markOffline(REGISTERED_AT.plusSeconds(5));

        assertTrue(worker.getActiveTaskIds().contains(taskId));
        assertThrows(UnsupportedOperationException.class,
                () -> worker.getActiveTaskIds().add(UUID.randomUUID()));
    }

    @Test
    void rejectsInvalidCapacityAndBackwardsTime() {
        assertThrows(IllegalArgumentException.class, () -> new Worker(
                UUID.randomUUID(), "worker", "Linux", "17", 0, REGISTERED_AT));

        Worker worker = newWorker(1);
        worker.recordHeartbeat(REGISTERED_AT.plusSeconds(10));
        assertThrows(IllegalArgumentException.class,
                () -> worker.markSuspected(REGISTERED_AT.plusSeconds(5)));
        assertEquals(WorkerStatus.ONLINE, worker.getStatus());
    }

    private static Worker newWorker(int capacity) {
        return new Worker(
                UUID.randomUUID(), "worker", "Linux", "17.0.12", capacity, REGISTERED_AT);
    }
}
