package smk.shared;
import java.util.concurrent.atomic.AtomicInteger;

public class LamportClock {
    private final AtomicInteger time = new AtomicInteger(0);

    public int now() {
        return time.get();
    }

    // local event
    public int tick() {
        return time.incrementAndGet();
    }

    // on receive(remoteTs)
    public int onReceive(int remoteTs) {
        // L = max(L, remoteTs) + 1
        int cur, next;
        do {
            cur = time.get();
            next = Math.max(cur, remoteTs) + 1;
        } while (!time.compareAndSet(cur, next));
        return next;
    }
}
