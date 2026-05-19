package qa.cinepolis.backend.model;

public record ResultPayload(
    String executionId,
    int    passed,
    int    failed,
    int    skipped,
    String allureUrl
) {}
