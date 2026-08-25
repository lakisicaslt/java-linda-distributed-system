package rs.ac.bg.etf.kdp.common.protocol;

import java.io.IOException;

@FunctionalInterface
public interface ProtocolFrameSink {

    void send(ProtocolFrame frame) throws IOException;
}
