package qa.cinepolis.backend.model;

public record RunRequest(
    String suiteId,
    String env,
    String device,
    String country
) {}
