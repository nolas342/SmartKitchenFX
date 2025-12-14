package smk.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import smk.shared.LamportClock;
import smk.shared.OrderRow;
import smk.shared.Message;

import java.util.PriorityQueue;
import java.util.Random;

public class SmartKitchenServerApp extends Application {

    private final LamportClock serverClock = new LamportClock();
    private final Label clockLabel = new Label("Server Lamport: 0");
    private final TextArea logArea = new TextArea();

    private final TableView<OrderRow> table = new TableView<>();
    private final ObservableList<OrderRow> tableData = FXCollections.observableArrayList();

    // Backend queue (keeps strict Lamport order)
    private final PriorityQueue<OrderRow> pq = new PriorityQueue<>();
    private final Random rnd = new Random();

    private ServerSocketService net;

    @Override
    public void start(Stage stage) {
        stage.setTitle("SmartKitchen Server (Lamport)");

        // Top bar
        HBox top = new HBox(12);
        top.setPadding(new Insets(12));
        top.getChildren().addAll(new Label("SmartKitchen Server UI"), clockLabel);

        // Table columns
        TableColumn<OrderRow, String> colClient = new TableColumn<>("Client");
        colClient.setCellValueFactory(new PropertyValueFactory<>("client"));
        colClient.setPrefWidth(120);

        TableColumn<OrderRow, String> colDish = new TableColumn<>("Dish");
        colDish.setCellValueFactory(new PropertyValueFactory<>("dish"));
        colDish.setPrefWidth(160);

        TableColumn<OrderRow, Integer> colTsClient = new TableColumn<>("tsClient");
        colTsClient.setCellValueFactory(new PropertyValueFactory<>("tsClient"));
        colTsClient.setPrefWidth(100);

        TableColumn<OrderRow, Integer> colLamport = new TableColumn<>("Lamport Order");
        colLamport.setCellValueFactory(new PropertyValueFactory<>("lamportOrder"));
        colLamport.setPrefWidth(130);

        table.getColumns().addAll(colClient, colDish, colTsClient, colLamport);
        table.setItems(tableData);
        table.setPrefHeight(320);

        // Log area
        logArea.setEditable(false);
        logArea.setPrefRowCount(12);

        // Demo buttons (replace later with real socket events)


        VBox root = new VBox(8, top, table, new Label("Logs:"), logArea, actions);
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root, 650, 560);
        stage.setScene(scene);
        stage.show();

        // Start network server (real sockets)
        net = new ServerSocketService(5000, serverClock, new ServerSocketService.Listener() {
            @Override
            public int onOrder(String client, String dish, int tsClient) {
                int lam = serverClock.onReceive(tsClient);
                Platform.runLater(() -> handleIncomingOrder(client, dish, tsClient, lam));
                return lam;
            }
            @Override
            public void onLog(String msg) { log(msg); }
        });
        net.start();
        stage.setOnCloseRequest(e -> { if (net != null) net.stop(); });

        log("Server ready. Listening (sockets + demo buttons).");
        refreshClock();
    }

    private void handleIncomingOrder(String client, String dish, int tsClient, int lamport) {
        refreshClock();
        OrderRow row = new OrderRow(client, dish, tsClient, lamport);
        pq.offer(row);
        rebuildTableFromPQ();
        log("[NET][RECV] " + client + " " + dish + " tsClient=" + tsClient + " -> Lamport=" + lamport);
    }

    private void refreshClock() {
        clockLabel.setText("Server Lamport: " + serverClock.now());
    }

    private void log(String s) {
        Platform.runLater(() -> {
            logArea.appendText(s + "\n");
        });
        System.out.println(s);
    }

    // ===== DEMO ONLY =====
    // Pretend a client message arrived: (client, dish, tsClient)
        int tsClient = rnd.nextInt(10) + 1;

        // On RECV: serverClock = onReceive(tsClient)
        int s = serverClock.onReceive(tsClient);
        refreshClock();

        // Use server clock current Lamport to order the queue
        OrderRow row = new OrderRow(client, dish, tsClient, s);
        pq.offer(row);

        // Update table to show sorted by pq
        rebuildTableFromPQ();

        log("[RECU] " + client + " " + dish + " (tsClient=" + tsClient + ") -> queued with Lamport=" + s);
    }

    private void simulateStart() {
        if (pq.isEmpty()) {
            log("[START] No orders.");
            return;
        }
        // local event: tick on START
        int s = serverClock.tick();
        refreshClock();

        OrderRow head = pq.peek();
        log("[START] " + head.getClient() + " " + head.getDish() + " (Lamport=" + head.getLamportOrder() + "), serverLamport=" + s);
        if (net != null && head != null) {
            net.broadcast(new Message(Message.MessageType.START, head.getClient(), head.getDish(),
                    head.getTsClient(), s, "processing"));
        }
    }

    private void simulateEnd() {
        if (pq.isEmpty()) {
            log("[END] No orders.");
            return;
        }
        // local event: tick on END
        int s = serverClock.tick();
        refreshClock();

        OrderRow done = pq.poll();
        rebuildTableFromPQ();
        log("[END] " + done.getClient() + " " + done.getDish() + " DONE. serverLamport=" + s);
        if (net != null && done != null) {
            net.broadcast(new Message(Message.MessageType.DONE, done.getClient(), done.getDish(),
                    done.getTsClient(), s, "done"));
        }
    }

    private void rebuildTableFromPQ() {
        tableData.setAll(pq.stream().sorted().toList());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
