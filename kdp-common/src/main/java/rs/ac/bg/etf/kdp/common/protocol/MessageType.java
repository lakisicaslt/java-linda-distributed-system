package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum MessageType {
    HELLO(1, allRoles()),
    HELLO_ACCEPTED(2, allRoles()),
    ERROR(3, allRoles()),

    SUBMIT_BATCH(100, roles(ConnectionRole.CLIENT)),
    BATCH_ACCEPTED(101, roles(ConnectionRole.CLIENT)),
    GET_BATCH_STATUS(102, roles(ConnectionRole.CLIENT)),
    BATCH_STATUS(103, roles(ConnectionRole.CLIENT)),
    GET_BATCH_RESULT(104, roles(ConnectionRole.CLIENT)),
    BATCH_RESULT(105, roles(ConnectionRole.CLIENT)),
    ABORT_JOB(106, roles(ConnectionRole.CLIENT)),
    RETRY_TASK(107, roles(ConnectionRole.CLIENT)),

    REGISTER_WORKER(200, roles(ConnectionRole.WORKER)),
    WORKER_REGISTERED(201, roles(ConnectionRole.WORKER)),
    HEARTBEAT(202, roles(ConnectionRole.WORKER)),
    ASSIGN_TASK(203, roles(ConnectionRole.WORKER)),
    TASK_ACCEPTED(204, roles(ConnectionRole.WORKER)),
    TASK_STARTED(205, roles(ConnectionRole.WORKER)),
    TASK_FINISHED(206, roles(ConnectionRole.WORKER)),
    TASK_FAILED(207, roles(ConnectionRole.WORKER)),

    FILE_BEGIN(300, roles(ConnectionRole.CLIENT, ConnectionRole.WORKER)),
    FILE_CHUNK(301, roles(ConnectionRole.CLIENT, ConnectionRole.WORKER)),
    FILE_END(302, roles(ConnectionRole.CLIENT, ConnectionRole.WORKER)),
    FILE_ACKNOWLEDGED(303, roles(ConnectionRole.CLIENT, ConnectionRole.WORKER)),

    LINDA_OUT(400, roles(ConnectionRole.LINDA)),
    LINDA_IN(401, roles(ConnectionRole.LINDA)),
    LINDA_INP(402, roles(ConnectionRole.LINDA)),
    LINDA_RD(403, roles(ConnectionRole.LINDA)),
    LINDA_RDP(404, roles(ConnectionRole.LINDA)),
    LINDA_EVAL(405, roles(ConnectionRole.LINDA)),
    LINDA_RESULT(406, roles(ConnectionRole.LINDA));

    private final int code;
    private final Set<ConnectionRole> allowedRoles;

    MessageType(int code, Set<ConnectionRole> allowedRoles) {
        this.code = code;
        this.allowedRoles = allowedRoles;
    }

    public int getCode() {
        return code;
    }

    public boolean isAllowedFor(ConnectionRole role) {
        return allowedRoles.contains(role);
    }

    public static MessageType fromCode(int code) throws ProtocolException {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new ProtocolException(
                ProtocolError.UNKNOWN_MESSAGE_TYPE, "Unknown message type code: " + code);
    }

    private static Set<ConnectionRole> allRoles() {
        return Collections.unmodifiableSet(EnumSet.allOf(ConnectionRole.class));
    }

    private static Set<ConnectionRole> roles(
            ConnectionRole first,
            ConnectionRole... additionalRoles) {
        EnumSet<ConnectionRole> roles = EnumSet.of(first, additionalRoles);
        return Collections.unmodifiableSet(roles);
    }
}
