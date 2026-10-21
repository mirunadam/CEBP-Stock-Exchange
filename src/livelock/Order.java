package livelock;

// One buy or sell order waiting in an order book.
// price and quantity can change, so only touch them while holding the book's lock.
public class Order {
    final long id;          // unique number, a smaller id means an older order
    final int traderId;     // who placed it
    final boolean isBuy;    // true = buy order, false = sell order
    long price;             // price per share, in cents
    int quantity;           // shares still left to trade

    Order(long id, int traderId, boolean isBuy, long price, int quantity) {
        this.id = id;
        this.traderId = traderId;
        this.isBuy = isBuy;
        this.price = price;
        this.quantity = quantity;
    }
}