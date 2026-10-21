package races;

/**
 * price and quantity can change (modify, partial trade), so they are only read or
 * changed while holding the lock of the OrderBook that contains this order.
 */
public class Order {
    final long id;
    final int traderId;
    final boolean isBuy;
    long price;
    int quantity;

    Order(long id, int traderId, boolean isBuy, long price, int quantity) {
        this.id = id;
        this.traderId = traderId;
        this.isBuy = isBuy;
        this.price = price;
        this.quantity = quantity;
    }
}
