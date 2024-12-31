package build.chronicle.aide.eg;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.bytes.MethodReader;
public class EventProcessor {

    private final ChronicleQueue queue;
    private final MethodReader reader;

    public EventProcessor(String path) {
        queue = ChronicleQueue.singleBuilder(path).build();
        reader = queue.createTailer().methodReader((EventInterface) this::processEvent);
    }

    private void processEvent(String event) {
        System.out.println("Processing event: " + event);
    }

    public void startProcessing() {
        while (true) {
            if (!reader.readOne()) {
                try {
                    Thread.sleep(10); // Prevent busy spinning
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public static void main(String[] args) {
        EventProcessor processor = new EventProcessor("events-queue");
        processor.startProcessing();
    }
}