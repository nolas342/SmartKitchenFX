package smk.server;

import smk.shared.LamportClock;
import smk.shared.Message;
import smk.shared.Message.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple TCP server that accepts clients, reads line-based JSON messages,
 * advances the Lamport clock, and notifies a listener.
 */
public class ServerSocketService {

    public interface Listener {
        /**
         * Handle an incoming ORDER. Returns the Lamport value after applying the event,
         * so we can send it back in the READY message.
         */
        int onOrder(String client, String dish, int tsClient);
        void onLog(String msg);
    }

    private final int port;
    private final LamportClock clock;
    private final Listener listener;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>();

    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public ServerSocketService(int port, LamportClock clock, Listener listener) {
        this.port = port;
        this.clock = clock;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        pool.submit(this::acceptLoop);
        log("[NET] Server listening on port " + port);
    }

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket(port)) {
            serverSocket = ss;
            while (running) {
                Socket socket = ss.accept();
                log("[NET] Client connected: " + socket.getRemoteSocketAddress());
                pool.submit(() -> handleClient(socket));
            }
        } catch (IOException e) {
            if (running) log("[NET][ERR] " + e.getMessage());
        } finally {
            running = false;
        }
    }

    private void handleClient(Socket socket) {
        PrintWriter out = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            clientWriters.add(out);
            String line;
            while ((line = in.readLine()) != null && running) {
                Message m = Message.fromJson(line);
                if (m.getType() == MessageType.ORDER) {
                    int lam = listener.onOrder(m.getClient(), m.getDish(), m.getTs());

                    // Reply READY to the sender with server Lamport.
                    Message ack = new Message(MessageType.READY, m.getClient(), m.getDish(), m.getTs(), lam,
                            "queued");
                    out.println(ack.toJson());
                } else {
                    log("[NET] Unknown message: " + line);
                }
            }
        } catch (IOException e) {
            log("[NET][ERR] client " + socket.getRemoteSocketAddress() + " " + e.getMessage());
        } finally {
            if (out != null) clientWriters.remove(out);
            try { socket.close(); } catch (IOException ignored) {}
            log("[NET] Client disconnected: " + socket.getRemoteSocketAddress());
        }
    }

    /** Broadcast a message to all connected clients (best-effort). */
    public void broadcast(Message m) {
        String json = m.toJson();
        for (PrintWriter w : clientWriters) {
            try { w.println(json); }
            catch (Exception ignored) {}
        }
        log("[NET][BCAST] " + json);
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
        clientWriters.clear();
        log("[NET] Server stopped.");
    }

    private void log(String msg) {
        if (listener != null) listener.onLog(msg);
    }
}
