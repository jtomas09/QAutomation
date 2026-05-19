package qa.cinepolis.backend.model;

public record RunRequest(
    String suite,
    String env,
    String device,
    String country
) {}
