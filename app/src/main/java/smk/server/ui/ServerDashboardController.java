package smk.server.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import smk.shared.LamportClock;
import smk.shared.OrderRow;
import smk.server.ServerSocketService;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

public class ServerDashboardController {

    // top stats
    @FXML private Label lblClock;
    @FXML private Label lblQueueSize;
    @FXML private Label lblHead;

    // queue (cards)
    @FXML private ListView<OrderRow> lvQueue;

    // logs + search
    @FXML private TextArea txtLogs;
    @FXML private TextField tfSearch;
    @FXML private Button btnClearSearch;

    // sidebar + stats
    @FXML private Label lblNode;
    @FXML private Label lblWorkers;
    @FXML private Label lblThroughput;
    @FXML private Label lblUptime;

    private final LamportClock clock = new LamportClock();
    private final ObservableList<OrderRow> data = FXCollections.observableArrayList();
    private final ObservableList<OrderRow> mirror = FXCollections.observableArrayList();
    private final PriorityQueue<OrderRow> pq = new PriorityQueue<>();
    private ServerSocketService net;
    private final Deque<Instant> completions = new ArrayDeque<>();
    private Instant startTime;

    @FXML
    private void initialize() {
        // node/demo labels
        if (lblNode != null) lblNode.setText("chef-1@localhost:5000");
        if (lblWorkers != null) lblWorkers.setText("1 / 1");
        if (lblThroughput != null) lblThroughput.setText("~1/min");
        if (lblUptime != null) lblUptime.setText("00:00:00");

        lblClock.setText(String.valueOf(clock.now()));
        updateQueueStats();

        // ListView wiring
        lvQueue.setItems(data);
        lvQueue.setCellFactory(lv -> new LamportCell());

        // live filter
        if (tfSearch != null) {
            tfSearch.textProperty().addListener((o, a, b) -> applyFilter());
        }
        if (btnClearSearch != null) {
            btnClearSearch.setOnAction(e -> {
                tfSearch.clear();
                applyFilter();
            });
        }

        log("Server UI ready.");

        // Start real socket listener so client orders arrive here
        net = new ServerSocketService(5000, clock, new ServerSocketService.Listener() {
            @Override
            public int onOrder(String client, String dish, int tsClient) {
                // Advance Lamport once here; return value used for READY
                int lam = clock.onReceive(tsClient);
                Platform.runLater(() -> onOrderReceived(client, dish, tsClient, lam));
                return lam;
            }
            @Override
            public void onLog(String msg) {
                log(msg);
            }
        });
        net.start();

        // kick off live stats (uptime + throughput)
        startTime = Instant.now();
        startStatsTicker();
    }

    @FXML private void onClearQueue() {
        pq.clear();
        rebuildQueue();
        log("[QUEUE] cleared");
    }

    @FXML private void onClearLogs() {
        if (txtLogs != null) txtLogs.clear();
    }

    @FXML private void onClearSearch() {
        if (tfSearch != null) {
            tfSearch.clear();
            applyFilter();
        }
    }

    @FXML private void onQuit() {
        if (net != null) net.stop();
        Platform.exit();
    }

    // === Core helpers ===
    private void rebuildQueue() {
        var sorted = pq.stream().sorted().collect(Collectors.toList());
        mirror.setAll(sorted);
        applyFilter();
        updateHeadChip();
        updateQueueStats();
    }

    private void applyFilter() {
        String q = tfSearch == null ? "" : tfSearch.getText().trim().toLowerCase();
        if (q.isEmpty()) {
            data.setAll(mirror);
        } else {
            data.setAll(mirror.stream()
                .filter(r -> r.getClient().toLowerCase().contains(q)
                          || r.getDish().toLowerCase().contains(q))
                .collect(Collectors.toList()));
        }
    }

    private void updateHeadChip() {
        var head = pq.peek();
        lblHead.setText(head == null ? "—" : head.getDish() + " (" + head.getLamportOrder() + ")");
    }

    private void updateQueueStats() {
        lblQueueSize.setText(String.valueOf(pq.size()));
    }

    private void startStatsTicker() {
        // update every second on FX thread
        var timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> updateStats())
        );
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();
    }

    private void updateStats() {
        // uptime
        if (startTime != null && lblUptime != null) {
            Duration d = Duration.between(startTime, Instant.now());
            long h = d.toHours();
            long m = d.toMinutesPart();
            long s = d.toSecondsPart();
            lblUptime.setText(String.format("%02d:%02d:%02d", h, m, s));
        }
        // throughput: completed per minute (rolling 60s)
        if (lblThroughput != null) {
            Instant cutoff = Instant.now().minusSeconds(60);
            while (!completions.isEmpty() && completions.peekFirst().isBefore(cutoff)) {
                completions.pollFirst();
            }
            lblThroughput.setText("~" + completions.size() + "/min");
        }
        // workers could be dynamic; keep placeholder "1 / 1"
        if (lblWorkers != null && lblWorkers.getText() == null) {
            lblWorkers.setText("1 / 1");
        }
    }

    private void log(String s) {
        Platform.runLater(() -> {
            if (txtLogs != null) txtLogs.appendText(s + System.lineSeparator());
        });
        System.out.println(s);
    }

    // Hookable API for your real socket server
    public void onOrderReceived(String client, String dish, int tsFromClient, int lam) {
        lblClock.setText(String.valueOf(lam));
        pq.offer(new OrderRow(client, dish, tsFromClient, lam));
        rebuildQueue();
        log("[RECV] " + client + " " + dish + " ts=" + tsFromClient + " -> L=" + lam);
    }
    public void onStartProcessingHead() {
        int lam = clock.tick();
        lblClock.setText(String.valueOf(lam));
        var head = pq.peek();
        log("[START] " + (head != null ? head.getClient() + " " + head.getDish() : "(empty)")
            + " S(L)=" + lam);
        if (net != null && head != null) {
            net.broadcast(new smk.shared.Message(smk.shared.Message.MessageType.START,
                    head.getClient(), head.getDish(), head.getTsClient(), lam, "en preparation"));
        }
    }
    public void onEndProcessingHead() {
        OrderRow done = pq.isEmpty() ? null : pq.poll();
        int lam = clock.tick();
        lblClock.setText(String.valueOf(lam));
        rebuildQueue();
        log("[END] S(L)=" + lam);
        if (net != null && done != null) {
            net.broadcast(new smk.shared.Message(smk.shared.Message.MessageType.DONE,
                    done.getClient(), done.getDish(), done.getTsClient(), lam, "en livraison"));
        }
        // track completions for throughput
        if (done != null) {
            completions.addLast(Instant.now());
        }
    }

    // === Custom ListCell for Lamport queue ===
    private class LamportCell extends ListCell<OrderRow> {
        private final HBox root = new HBox(12);

        private final StackPane avatarWrap = new StackPane();
        private final Label avatar = new Label(); // shows "C1" etc.
        private final VBox main = new VBox(4);
        private final HBox titleRow = new HBox(8);
        private final Label dish = new Label();
        private final Label client = new Label();
        private final HBox metaRow = new HBox(8);
        private final Label tsChip = new Label();
        private final Label lamChip = new Label();
        private final Pane grow = new Pane();
        private final HBox actions = new HBox(8);
        private final Button btnStart = new Button("Start");
        private final Button btnEnd = new Button("End");

        LamportCell() {
            // layout
            HBox.setHgrow(grow, Priority.ALWAYS);
            root.getChildren().addAll(avatarWrap, main, grow, actions);
            root.getStyleClass().add("lq-row");
            root.setFillHeight(true);
            avatarWrap.getStyleClass().add("avatar-wrap");
            avatar.getStyleClass().add("avatar");
            avatarWrap.getChildren().add(avatar);

            dish.getStyleClass().add("lq-dish");
            client.getStyleClass().add("lq-client");
            titleRow.getChildren().addAll(dish, client);

            tsChip.getStyleClass().addAll("chip", "chip-soft");
            lamChip.getStyleClass().addAll("chip", "chip-strong");
            metaRow.getChildren().addAll(tsChip, lamChip);

            main.getChildren().addAll(titleRow, metaRow);

            btnStart.getStyleClass().add("lq-start");
            btnEnd.getStyleClass().add("lq-end");
            actions.getChildren().addAll(btnStart, btnEnd);

            // actions
            btnStart.setOnAction(e -> {
                OrderRow r = getItem(); if (r == null) return;
                OrderRow head = pq.peek();
                if (head == null || head.compareTo(r) != 0) {
                    log("[WARN] Start pressed on non-head item; ignoring");
                    return;
                }
                onStartProcessingHead();
            });

            btnEnd.setOnAction(e -> {
                OrderRow r = getItem(); if (r == null) return;
                OrderRow head = pq.peek();
                if (head == null || head.compareTo(r) != 0) {
                    log("[WARN] End pressed on non-head item; ignoring");
                    return;
                }
                onEndProcessingHead();
            });
        }

        @Override
        protected void updateItem(OrderRow r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) { setGraphic(null); return; }

            // avatar = client initials (or load image if you have one)
            avatar.setText(r.getClient());
            avatarWrap.getStyleClass().removeAll("prio-1","prio-2","prio-3");
            // simple priority color by Lamport mod 3 (purely visual)
            switch (Math.floorMod(r.getLamportOrder(), 3)) {
                case 0 -> avatarWrap.getStyleClass().add("prio-1");
                case 1 -> avatarWrap.getStyleClass().add("prio-2");
                default -> avatarWrap.getStyleClass().add("prio-3");
            }

            dish.setText(r.getDish());
            client.setText("• " + r.getClient());
            tsChip.setText("tsClient=" + r.getTsClient());
            lamChip.setText("L=" + r.getLamportOrder());

            setGraphic(root);
        }
    }

    // util (optional image loading for avatar, unused here)
    @SuppressWarnings("unused")
    private Node imageOrInitials(String stem, String initials) {
        try (InputStream is = getClass().getResourceAsStream("/img/clients/" + stem + ".png")) {
            if (is != null) {
                ImageView iv = new ImageView(new Image(is));
                iv.setFitWidth(36); iv.setFitHeight(36); iv.setPreserveRatio(true);
                return iv;
            }
        } catch (Exception ignored) {}
        Label l = new Label(initials);
        l.getStyleClass().add("avatar");
        return l;
    }
}
