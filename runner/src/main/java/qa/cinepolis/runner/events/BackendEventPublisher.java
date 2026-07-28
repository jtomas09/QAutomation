package qa.cinepolis.runner.events;

import qa.cinepolis.runner.BackendClient;

/** Implementación real de ExecutionEventPublisher — envía cada evento al backend vía HTTP. */
public class BackendEventPublisher implements ExecutionEventPublisher {

    private final BackendClient client;

    public BackendEventPublisher(BackendClient client) {
        this.client = client;
    }

    @Override
    public void publish(ExecutionEvent event) {
        client.sendEvent(event);
    }
}
