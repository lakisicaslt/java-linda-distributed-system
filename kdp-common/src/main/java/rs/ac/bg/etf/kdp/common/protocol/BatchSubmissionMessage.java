package rs.ac.bg.etf.kdp.common.protocol;

import rs.ac.bg.etf.kdp.common.domain.JobSpecification;

import java.util.List;
import java.util.Objects;

/** Descriptions of the jobs submitted together by one client request. */
public final class BatchSubmissionMessage {

    private final List<JobSpecification> specifications;

    public BatchSubmissionMessage(List<JobSpecification> specifications) {
        Objects.requireNonNull(specifications, "job specifications must not be null");
        if (specifications.isEmpty()) {
            throw new IllegalArgumentException("submission must contain at least one job");
        }
        if (specifications.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("job specifications must not contain null");
        }
        this.specifications = List.copyOf(specifications);
    }

    public List<JobSpecification> getSpecifications() {
        return specifications;
    }
}
