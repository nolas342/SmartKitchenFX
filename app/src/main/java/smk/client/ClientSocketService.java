package smk.client;

import smk.shared.LamportClock;
import smk.shared.Message;
import smk.shared.Message.MessageType;

import java.io.*;
import java.net.Socket;

/**
 * Simple TCP client that sends orders and listens for READY/other messages.
 * Networking runs off the FX thread; callbacks should update UI via Platform.runLater.
 */
public class ClientSocketService {

    public interface Listener {
        void onReady(Message m, int lamportAfter);
        void onEvent(Message m, int lamportAfter); 
        void onLog(String msg);
    }

    private final String host;
    private final int port;
    private final LamportClock clock;
    private final Listener listener;

    private Socket socket;
    private PrintWriter out;
    private Thread readerThread;

    public ClientSocketService(String host, int port, LamportClock clock, Listener listener) {
        this.host = host;
        this.port = port;
        this.clock = clock;
        this.listener = listener;
    }

    public void connect() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            readerThread = new Thread(this::readLoop, "client-read-loop");
            readerThread.setDaemon(true);
            readerThread.start();
            log("[NET] Connected to " + host + ":" + port);
        } catch (IOException e) {
            log("[NET][ERR] " + e.getMessage());
        }
    }

    /** Tick Lamport and send an ORDER message. */
    public void sendOrder(String client, String dish) {
        if (out == null) {
            log("[NET][WARN] Not connected.");
            return;
        }
        int ts = clock.tick();
        Message m = new Message(MessageType.ORDER, client, dish, ts, 0, null);
        out.println(m.toJson());
        log("[SEND] " + client + " " + dish + " ts=" + ts);
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                log("[NET][RAW] " + line);
                Message m = Message.fromJson(line);
                if (m.getType() == MessageType.READY) {
                    int lam = clock.onReceive(m.getLamport());
                    listener.onReady(m, lam);
                } else if (m.getType() == MessageType.START || m.getType() == MessageType.DONE) {
                    int lam = clock.onReceive(m.getLamport());
                    listener.onEvent(m, lam);
                } else {
                    log("[NET] unknown " + line);
                }
            }
        } catch (IOException e) {
            log("[NET][ERR] " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        out = null;
        log("[NET] Disconnected.");
    }

    private void log(String msg) {
        if (listener != null) listener.onLog(msg);
    }
}
