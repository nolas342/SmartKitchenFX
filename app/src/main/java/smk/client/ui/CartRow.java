package smk.client.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Simple model for a cart item. */
public class CartRow {
    private final StringProperty dish = new SimpleStringProperty();
    private final StringProperty stem = new SimpleStringProperty();
    private final IntegerProperty unit = new SimpleIntegerProperty();
    private final IntegerProperty qty  = new SimpleIntegerProperty(1);
    private final StringProperty status = new SimpleStringProperty("");

    public CartRow(String dish, String stem, int unit, int qty) {
        this.dish.set(dish);
        this.stem.set(stem);
        this.unit.set(unit);
        this.qty.set(qty);
    }

    public String getDish() { return dish.get(); }
    public StringProperty dishProperty() { return dish; }

    public String getStem() { return stem.get(); }
    public StringProperty stemProperty() { return stem; }

    public int getUnit() { return unit.get(); }
    public IntegerProperty unitProperty() { return unit; }

    public int getQty() { return qty.get(); }
    public IntegerProperty qtyProperty() { return qty; }

    public String getStatus() { return status.get(); }
    public void setStatus(String v) { status.set(v); }
    public StringProperty statusProperty() { return status; }

    public int getSubtotal() { return getUnit() * getQty(); }
}
