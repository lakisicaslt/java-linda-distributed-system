package rs.ac.bg.etf.kdp.common.protocol;

import java.util.Objects;

/** Result of a Linda tuple operation. */
public final class LindaResultMessage {

    private final boolean matched;
    private final String[] tuple;

    public LindaResultMessage(boolean matched, String[] tuple) {
        if (matched) {
            this.tuple = Objects.requireNonNull(
                    tuple, "matched Linda result must contain a tuple").clone();
        } else {
            if (tuple != null) {
                throw new IllegalArgumentException(
                        "unmatched Linda result must not contain a tuple");
            }
            this.tuple = null;
        }
        this.matched = matched;
    }

    public boolean isMatched() {
        return matched;
    }

    public String[] getTuple() {
        return tuple == null ? null : tuple.clone();
    }
}
