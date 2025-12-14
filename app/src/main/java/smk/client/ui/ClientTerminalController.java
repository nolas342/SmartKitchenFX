package smk.client.ui;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import smk.shared.LamportClock;
import smk.client.ClientSocketService;
import smk.shared.Message;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ClientTerminalController {

    // Header
    @FXML private TextField tfClient;
    @FXML private TextField tfSearch;
    @FXML private Label lblClock;

    // Filters
    @FXML private ToggleButton tgAll, tgVegan, tgPopular;

    // Menu
    @FXML private FlowPane menuFlow;
    @FXML private Label lblCount;

    // Cart summary
    @FXML private Label lblItems, lblSubtotal, lblTax, lblTotal;
    @FXML private ListView<CartRow> cartList;

    // Logs
    @FXML private TextArea txtLogs;

    // Data
    private final ObservableList<CartRow> cart = FXCollections.observableArrayList();
    private final List<MenuItem> allMenu = new ArrayList<>();
    private final LamportClock clock = new LamportClock();
    private ClientSocketService net;

    // --- lifecycle ----------------------------------------------------------

    @FXML
    private void initialize() {
        // Cart list as cards
        cartList.setItems(cart);
        cartList.setCellFactory(lv -> new CartCell());

        // Default client name if empty
        if (tfClient != null && (tfClient.getText() == null || tfClient.getText().isBlank())) {
            tfClient.setText("client-1");
        }

        // Totals auto-update
        cart.addListener((ListChangeListener<CartRow>) c -> updateTotals());

        // Build menu tiles (replace with your own data if needed)
        seedMenuIfEmpty();
        renderMenu();

        // Filters → apply
        tgAll.setOnAction(e -> applyFilters());
        tgVegan.setOnAction(e -> applyFilters());
        tgPopular.setOnAction(e -> applyFilters());

        // Debounced search
PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));
tfSearch.textProperty().addListener((o, a, b) -> {
    searchDebounce.stop();
    searchDebounce.setOnFinished(ev -> applyFilters());
    searchDebounce.playFromStart();
});
// Clear button
btnClearSearch.setOnAction(e -> {
    tfSearch.clear();
    applyFilters();
});


        log("[UI] Client UI ready.");
        updateTotals();
        cart.addListener((ListChangeListener<CartRow>) c -> updateCtas());
updateCtas(); // run once

        // Start socket client to talk to server
        String host = resolveHost();
        int port = resolvePort();
        log("[NET] Connecting to " + host + ":" + port);

        net = new ClientSocketService(host, port, clock, new ClientSocketService.Listener() {
            @Override
            public void onReady(Message m, int lamportAfter) {
                javafx.application.Platform.runLater(() -> {
                    refreshClock();
                    log("[READY] " + m.getDish() + " Ls=" + m.getLamport() + " Lc=" + lamportAfter);
                });
            }
            @Override
            public void onEvent(Message m, int lamportAfter) {
                javafx.application.Platform.runLater(() -> {
                    refreshClock();
                    if (m.getType() == Message.MessageType.START) {
                        updateStatus(m.getDish(), "En préparation");
                        log("[INFO] " + m.getDish() + " est en préparation.");
                    } else if (m.getType() == Message.MessageType.DONE) {
                        updateStatus(m.getDish(), "En livraison");
                        log("[INFO] " + m.getDish() + " est en cours de livraison.");
                    } else {
                        log("[NET] " + m.toJson());
                    }
                });
            }
            @Override
            public void onLog(String msg) {
                javafx.application.Platform.runLater(() -> log(msg));
            }
        });
        net.connect();
        refreshClock();
        
    }
    

    // --- UI actions ---------------------------------------------------------

    @FXML
    private void onSendOrder() {
        // hook your networking here; we just log
        String client = tfClient.getText().trim();
        if (client.isEmpty()) { log("[SEND] missing client"); return; }
        if (cart.isEmpty()) { log("[SEND] cart empty"); return; }

        if (net == null) { log("[SEND] not connected"); return; }

        // send one message per cart item (simple demo)
        for (CartRow r : cart) {
            net.sendOrder(client, r.getDish());
        }
        refreshClock();
        log("Sent " + cart.size() + " items for " + client);
    }

    @FXML
    private void onClearCart() {
        cart.clear();
        updateTotals();
    }

    @FXML
    private void onCheckout() {
        log("Checkout total = " + lblTotal.getText());
    }

    @FXML
    private void onFakeReady() { log("Fake READY (demo)"); }

    @FXML
    private void onLocalTick() {
        int l = clock.tick();
        refreshClock();
        log("Tick(local) -> " + l);
    }

    @FXML private Button btnSendOrder;
    @FXML private Button btnCheckout;
    @FXML private Button btnClearSearch;
    @FXML
private void onClearSearch() {
    tfSearch.clear();
    applyFilters();
}


private void updateCtas() {
    boolean empty = cart.isEmpty();
    if (btnSendOrder != null) btnSendOrder.setDisable(empty);
    if (btnCheckout  != null) btnCheckout.setDisable(empty);

    // Update Pay $XX label
    if (btnCheckout != null && lblTotal != null) {
        btnCheckout.setText("Pay " + lblTotal.getText());
    }
}

    // --- Menu rendering / filtering ----------------------------------------

    private void seedMenuIfEmpty() {
        if (!allMenu.isEmpty()) return;
        allMenu.add(new MenuItem("Pizza", "pizza", 18, true, false));
        allMenu.add(new MenuItem("Burger", "burger", 12, false, true));
        allMenu.add(new MenuItem("Pasta", "pasta", 14, false, true));
        allMenu.add(new MenuItem("Salad", "salad", 10, true, false));
        allMenu.add(new MenuItem("Steak", "steak", 28, false, false));
        allMenu.add(new MenuItem("Sushi", "sushi", 24, false, true));
    }

    private void applyFilters() {
        renderMenu();
    }

    private void renderMenu() {
        menuFlow.getChildren().clear();

        String q = tfSearch.getText() == null ? "" : tfSearch.getText().trim().toLowerCase();
        boolean wantVegan   = tgVegan.isSelected();
        boolean wantPopular = tgPopular.isSelected();
        boolean wantAll     = tgAll.isSelected() || (!wantVegan && !wantPopular);

        List<MenuItem> filtered = new ArrayList<>();
        for (MenuItem m : allMenu) {
            boolean matchText = q.isEmpty() || m.name.toLowerCase().contains(q);
            boolean matchTag =
                    wantAll ||
                    (wantVegan && m.vegan) ||
                    (wantPopular && m.popular);

            if (matchText && matchTag) filtered.add(m);
        }

        lblCount.setText(filtered.size() + " items");

        for (MenuItem m : filtered) {
            menuFlow.getChildren().add(buildMenuCard(m));
        }
    }

    private Node buildMenuCard(MenuItem m) {
    // ----- Card root -----
    VBox card = new VBox(10);
    card.getStyleClass().add("menu-card");
    card.setPadding(new Insets(12));

    // ----- Image + overlays -----
    ImageView iv = new ImageView();
    iv.setFitWidth(240); iv.setFitHeight(140); iv.setPreserveRatio(true);
    InputStream is = tryLoadImage("/img/" + m.stem + ".png",
                                  "/img/" + m.stem + ".jpg",
                                  "/img/" + m.stem + ".jpeg");
    if (is != null) iv.setImage(new Image(is));

    StackPane imgWrap = new StackPane(iv);
    imgWrap.getStyleClass().add("menu-thumb");

    // price badge (overlay bottom-right)
    Label priceBadge = new Label("$" + m.price);
    priceBadge.getStyleClass().add("price-badge");
    StackPane.setAlignment(priceBadge, javafx.geometry.Pos.BOTTOM_RIGHT);
    StackPane.setMargin(priceBadge, new Insets(0, 8, 8, 0));

    // favorite heart (overlay top-right)
    ToggleButton favBtn = new ToggleButton();
    favBtn.getStyleClass().addAll("icon-btn", "fav-btn");
    // icon (png) or fallback "❤"
    Node heartOn  = iconOrText("/img/icons/heart_fill.png", 18, "❤");
    Node heartOff = iconOrText("/img/icons/heart.png",      18, "♡");
    favBtn.setGraphic(heartOff);
    favBtn.selectedProperty().addListener((o, a, b) ->
            favBtn.setGraphic(b ? heartOn : heartOff));
    favBtn.setTooltip(new Tooltip("Add to favorites"));
    StackPane.setAlignment(favBtn, javafx.geometry.Pos.TOP_RIGHT);
    StackPane.setMargin(favBtn, new Insets(8, 8, 0, 0));

    imgWrap.getChildren().addAll(priceBadge, favBtn);

    // ----- Title row -----
    HBox titleRow = new HBox(8);
    Label name = new Label(m.name);
    name.getStyleClass().add("menu-title");
    Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
    // optional rating stars (static for demo)
    Label rating = new Label("★ 4.8");
    rating.getStyleClass().add("rating-chip");
    titleRow.getChildren().addAll(name, grow, rating);

    // ----- Tags row -----
    FlowPane tags = new FlowPane(6, 6);
    if (m.vegan)   tags.getChildren().add(chip("Vegan"));
    if (m.popular) tags.getChildren().add(chip("Popular"));

    // ----- Controls row (glass stepper + Add) -----
    HBox controls = new HBox(10);
    controls.setFillHeight(true);

    Button minus = new Button();
    minus.setGraphic(iconOrText("/img/icons/minus.png", 12, "−"));
    minus.getStyleClass().add("qty-icon");

    Label qtyLabel = new Label("1");
    qtyLabel.getStyleClass().add("qty-pill");
    final int[] qty = {1};

    Button plus = new Button();
    plus.setGraphic(iconOrText("/img/icons/plus.png", 12, "+"));
    plus.getStyleClass().add("qty-icon");

    minus.setOnAction(e -> { qty[0] = Math.max(1, qty[0]-1); qtyLabel.setText(String.valueOf(qty[0])); });
    plus.setOnAction(e -> { qty[0] = Math.min(20, qty[0]+1); qtyLabel.setText(String.valueOf(qty[0])); });

    HBox stepper = new HBox(8, minus, qtyLabel, plus);
    stepper.getStyleClass().add("qty-glass");

    Region grow2 = new Region(); HBox.setHgrow(grow2, Priority.ALWAYS);

    Button add = new Button("Add");
    add.setGraphic(iconOrText("/img/icons/cart.png", 16, "🛒"));
    add.getStyleClass().add("add-pill");
    add.setOnAction(e -> {
        addToCart(m.name, m.stem, m.price, qty[0]);
        log("ADD -> " + m.name + " x" + qty[0]);
    });

    controls.getChildren().addAll(stepper, grow2, add);

    // ----- Compose card -----
    card.getChildren().addAll(imgWrap, titleRow, tags, controls);

    // subtle hover scale/elevation handled by CSS
    return card;
}


    private Label chip(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("tag-chip");
        return l;
    }
    private Node iconOrText(String path, double size, String fallbackText) {
    ImageView iv = null;
    try (InputStream is = getClass().getResourceAsStream(path)) {
        if (is != null) {
            iv = new ImageView(new Image(is));
            iv.setFitWidth(size); iv.setFitHeight(size); iv.setPreserveRatio(true);
        }
    } catch (Exception ignored) {}
    return iv != null ? iv : new Label(fallbackText);
}


    // --- Cart helpers -------------------------------------------------------

    private void addToCart(String dish, String stem, int unit, int qty) {
        // merge if same dish
        for (CartRow r : cart) {
            if (r.getDish().equals(dish)) {
                r.qtyProperty().set(Math.min(20, r.getQty() + qty));
                updateTotals();
                cartList.refresh();
                return;
            }
        }
        CartRow row = new CartRow(dish, stem, unit, qty);
        row.setStatus("En attente");
        cart.add(row);
        updateTotals();
    }

    private void updateTotals() {
        int items = cart.stream().mapToInt(CartRow::getQty).sum();
        int subtotal = cart.stream().mapToInt(CartRow::getSubtotal).sum();
        int tax = (int) Math.round(subtotal * 0.10);
        int total = subtotal + tax;

        lblItems.setText(items + " items");
        lblSubtotal.setText("$" + subtotal);
        lblTax.setText("$" + tax);
        lblTotal.setText("$" + total);

        updateCtas(); // <— keep the CTAs in sync
    }

    // --- Util ---------------------------------------------------------------

    private void log(String s) {
        if (txtLogs != null) {
            txtLogs.appendText(s + System.lineSeparator());
        }
    }

    private void updateStatus(String dish, String status) {
        for (CartRow r : cart) {
            if (r.getDish().equals(dish)) {
                r.setStatus(status);
                cartList.refresh();
                break;
            }
        }
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

    private void refreshClock() {
        if (lblClock != null) lblClock.setText(String.valueOf(clock.now()));
    }

    /** Try multiple resource paths, return the first existing stream. */
    private InputStream tryLoadImage(String... candidates) {
        for (String c : candidates) {
            InputStream is = getClass().getResourceAsStream(c);
            if (is != null) return is;
        }
        return null;
    }

    // --- Small menu item struct --------------------------------------------

    private static class MenuItem {
        final String name, stem; // stem = filename stem under /img
        final int price;
        final boolean vegan, popular;

        MenuItem(String name, String stem, int price, boolean vegan, boolean popular) {
            this.name = name;
            this.stem = stem;
            this.price = price;
            this.vegan = vegan;
            this.popular = popular;
        }
    }

    // --- Custom ListCell for cart rows -------------------------------------

    private class CartCell extends ListCell<CartRow> {
        private final HBox root = new HBox(12);
        private final ImageView thumb = new ImageView();
        private final VBox titles = new VBox(4);
        private final Label name = new Label();
        private final Label unit = new Label();
        private final Label status = new Label();
        private final Region grow = new Region();
        private final HBox stepper = new HBox(6);
        private final Button minus = new Button("−");
        private final Label qtyLabel = new Label();
        private final Button plus = new Button("+");
        private final Label subtotal = new Label();
        private final Button remove = new Button("Remove");

        CartCell() {
            HBox.setHgrow(grow, Priority.ALWAYS);
            root.getChildren().addAll(thumb, titles, grow, stepper, subtotal, remove);
            root.getStyleClass().add("cart-row");
            root.setFillHeight(true);

            thumb.setFitWidth(56); thumb.setFitHeight(42); thumb.setPreserveRatio(true);
            thumb.getStyleClass().add("cart-thumb");

            name.getStyleClass().add("cart-title");
            unit.getStyleClass().add("muted");
            status.getStyleClass().addAll("chip", "chip-soft");
            titles.getChildren().addAll(name, unit, status);

            minus.getStyleClass().add("qty-btn");
            plus.getStyleClass().add("qty-btn");
            qtyLabel.getStyleClass().add("qty-pill");
            stepper.getChildren().addAll(minus, qtyLabel, plus);

            subtotal.getStyleClass().add("price-chip");
            remove.getStyleClass().add("remove-pill");

            minus.setOnAction(e -> {
                CartRow r = getItem(); if (r == null) return;
                if (r.getQty() > 1) r.qtyProperty().set(r.getQty() - 1);
                refreshRow(r);
                updateTotals();
            });
            plus.setOnAction(e -> {
                CartRow r = getItem(); if (r == null) return;
                r.qtyProperty().set(Math.min(20, r.getQty() + 1));
                refreshRow(r);
                updateTotals();
            });
            remove.setOnAction(e -> {
                CartRow r = getItem(); if (r != null) {
                    cart.remove(r);
                    updateTotals();
                }
            });
        }

        @Override
        protected void updateItem(CartRow r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) { setGraphic(null); return; }

            InputStream is = tryLoadImage("/img/" + r.getStem() + ".png",
                                          "/img/" + r.getStem() + ".jpg",
                                          "/img/" + r.getStem() + ".jpeg");
            if (is != null) thumb.setImage(new Image(is));

            name.setText(r.getDish());
            refreshRow(r);
            setGraphic(root);
        }

        private void refreshRow(CartRow r) {
            qtyLabel.setText(String.valueOf(r.getQty()));
            unit.setText(String.format("$%d", r.getUnit()));
            subtotal.setText(String.format("$%d", r.getSubtotal()));
            status.setText(r.getStatus());
        }
    }
}
