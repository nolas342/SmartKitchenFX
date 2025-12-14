package smk.shared;

import javafx.beans.property.*;

public class OrderRow implements Comparable<OrderRow> {
    private final StringProperty client = new SimpleStringProperty();
    private final StringProperty dish = new SimpleStringProperty();
    private final IntegerProperty tsClient = new SimpleIntegerProperty();
    private final IntegerProperty lamportOrder = new SimpleIntegerProperty();

    public OrderRow(String client, String dish, int tsClient, int lamportOrder) {
        this.client.set(client);
        this.dish.set(dish);
        this.tsClient.set(tsClient);
        this.lamportOrder.set(lamportOrder);
    }

    public String getClient() { return client.get(); }
    public void setClient(String v) { client.set(v); }
    public StringProperty clientProperty() { return client; }

    public String getDish() { return dish.get(); }
    public void setDish(String v) { dish.set(v); }
    public StringProperty dishProperty() { return dish; }

    public int getTsClient() { return tsClient.get(); }
    public void setTsClient(int v) { tsClient.set(v); }
    public IntegerProperty tsClientProperty() { return tsClient; }

    public int getLamportOrder() { return lamportOrder.get(); }
    public void setLamportOrder(int v) { lamportOrder.set(v); }
    public IntegerProperty lamportOrderProperty() { return lamportOrder; }

    // For PriorityQueue ordering: (ts, clientName) total order tie-break
    @Override
    public int compareTo(OrderRow o) {
        int c = Integer.compare(this.getLamportOrder(), o.getLamportOrder());
        if (c != 0) return c;
        return this.getClient().compareTo(o.getClient());
    }
}
