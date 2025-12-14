package smk.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import smk.shared.LamportClock;
import smk.shared.Message;

public class SmartKitchenClientApp extends Application {

    private final LamportClock clientClock = new LamportClock();

    private final TextField tfClient = new TextField("client-1");
    private final TextField tfDish = new TextField("Pizza");
    private final Label clockLabel = new Label("Client Lamport: 0");
    private final TextArea logArea = new TextArea();

    private ClientSocketService net;

    @Override
    public void start(Stage stage) {
        stage.setTitle("SmartKitchen Client");

        // Top
        HBox who = new HBox(10,
                new Label("Client:"), tfClient,
                new Label("Dish:"), tfDish
        );
        who.setPadding(new Insets(10));

        // Logs
        logArea.setEditable(false);
        logArea.setPrefRowCount(12);

        Button btnSend = new Button("Send Order");
        btnSend.setOnAction(e -> sendOrder());

        Button btnFakeReceiveAck = new Button("Fake READY (server)");
        btnFakeReceiveAck.setOnAction(e -> fakeAck());

        HBox actions = new HBox(10, btnSend, btnFakeReceiveAck);
        actions.setPadding(new Insets(8));

        HBox top = new HBox(12, new Label("Client UI"), clockLabel);
        top.setPadding(new Insets(10));

        VBox root = new VBox(8, top, who, new Label("Logs:"), logArea, actions);
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root, 560, 420);
        stage.setScene(scene);
        stage.show();

        String host = resolveHost();
        int port = resolvePort();
        log("[NET] Connecting to " + host + ":" + port);

        net = new ClientSocketService(host, port, clientClock, new ClientSocketService.Listener() {
            @Override
            public void onReady(Message m, int lamportAfter) {
                Platform.runLater(() -> {
                    refreshClock();
                    log("[READY] serverLamport=" + m.getLamport() + " clientLamport=" + lamportAfter);
                });
            }
            @Override
            public void onEvent(Message m, int lamportAfter) {
                Platform.runLater(() -> {
                    refreshClock();
                    if (m.getType() == Message.MessageType.START) {
                        log("[INFO] " + m.getDish() + " est en préparation.");
                    } else if (m.getType() == Message.MessageType.DONE) {
                        log("[INFO] " + m.getDish() + " est en cours de livraison.");
                    } else {
                        log("[" + m.getType() + "] " + m.getDish() + " Ls=" + m.getLamport() + " Lc=" + lamportAfter);
                    }
                });
            }
            @Override
            public void onLog(String msg) { log(msg); }
        });
        net.connect();
        stage.setOnCloseRequest(e -> { if (net != null) net.disconnect(); });

        log("Client ready.");
        refreshClock();
    }

    private void refreshClock() {
        clockLabel.setText("Client Lamport: " + clientClock.now());
    }

    private void log(String s) {
        Platform.runLater(() -> logArea.appendText(s + "\n"));
        System.out.println(s);
    }

    private void sendOrder() {
        String client = tfClient.getText().trim();
        String dish = tfDish.getText().trim();
        if (client.isEmpty() || dish.isEmpty()) {
            log("[SEND] Missing client/dish.");
            return;
        }

        // send over socket (ticks Lamport inside service)
        net.sendOrder(client, dish);
        refreshClock();
    }

    private void fakeAck() {
        // On receive from server with remote ts (demo choose ts+1)
        int remoteTs = clientClock.now() + 1;
        int after = clientClock.onReceive(remoteTs);
        refreshClock();

        log("[READY] From server (remoteTs=" + remoteTs + ") -> clientLamport=" + after);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private String resolveHost() {
        String h = System.getProperty("SMK_SERVER_HOST");
        if (h == null || h.isBlank()) h = System.getenv("SMK_SERVER_HOST");
        if (h == null || h.isBlank()) h = "localhost";
        return h.trim();
    }

    private int resolvePort() {
        String p = System.getProperty("SMK_SERVER_PORT");
        if (p == null || p.isBlank()) p = System.getenv("SMK_SERVER_PORT");
        int port = 5000;
        if (p != null && !p.isBlank()) {
            try { port = Integer.parseInt(p.trim()); }
            catch (NumberFormatException ignored) { log("[NET] Invalid SMK_SERVER_PORT, using 5000"); }
        }
        return port;
    }
}
