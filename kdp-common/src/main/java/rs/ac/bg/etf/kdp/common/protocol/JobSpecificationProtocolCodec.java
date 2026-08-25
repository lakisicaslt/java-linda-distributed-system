package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Shared binary representation of a job specification. */
final class JobSpecificationProtocolCodec {

    private static final int MAX_TEXT_BYTES = 4_096;
    private static final int MAX_VALUE_COUNT = 256;

    void write(DataOutputStream output, JobSpecification specification) throws IOException {
        writeString(output, specification.getJarFileName());
        writeStrings(output, specification.getJvmArguments());
        writeStrings(output, specification.getProgramArguments());
        writeStrings(output, specification.getInputFileNames());
        writeStrings(output, specification.getOutputFileNames());
    }

    JobSpecification read(DataInputStream input) throws IOException {
        return new JobSpecification(
                readString(input),
                readStrings(input),
                readStrings(input),
                readStrings(input),
                readStrings(input));
    }

    private static void writeStrings(DataOutputStream output, List<String> values)
            throws IOException {
        if (values.size() > MAX_VALUE_COUNT) {
            throw new IllegalArgumentException("Too many values in job specification");
        }
        output.writeInt(values.size());
        for (String value : values) {
            writeString(output, value);
        }
    }

    private static List<String> readStrings(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_VALUE_COUNT) {
            throw new IllegalArgumentException("Invalid job specification list size: " + count);
        }
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(readString(input));
        }
        return List.copyOf(values);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Job specification value is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(
                    "Invalid job specification value length: " + length);
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Job specification value is truncated");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
