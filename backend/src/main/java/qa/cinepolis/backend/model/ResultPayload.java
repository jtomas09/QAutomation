package qa.cinepolis.backend.model;

import java.util.List;

public record ResultPayload(
    String           executionId,
    int              passed,
    int              failed,
    int              skipped,
    String           allureUrl,
    List<TestCaseResult> testCases
) {}
