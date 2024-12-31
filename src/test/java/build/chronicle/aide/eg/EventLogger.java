package build.chronicle.aide.eg;

import net.openhft.chronicle.queue.ChronicleQueue;

public class EventLogger implements AutoCloseable {

    private final ChronicleQueue queue;
    private final EventInterface eventWriter;

    public EventLogger(String path) {
        queue = ChronicleQueue.singleBuilder(path).build();
        eventWriter = queue.methodWriter(EventInterface.class);
    }

    public void logEvent(String event) {
        eventWriter.onEvent(event);
    }

    public void close() {
        queue.close();
    }

    public static void main(String[] args) {
        try (EventLogger logger = new EventLogger("events-queue")) {
            logger.logEvent("UserLogin: user123");
            logger.logEvent("FileUpload: fileX.pdf");
        }
    }
}