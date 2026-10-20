package races;

import java.util.ArrayList;
import java.util.List;

/**
 * The order book of ONE company: all buy orders and sell orders waiting for that company.
 *
 * Every method is synchronized: while one thread adds, modifies, cancels or matches orders
 * of this company, no other thread can touch this book. This gives us the rule
 * "an order can only be modified when no trade is in progress": a trade runs inside
 * match(), which is always called from a synchronized method, so modify() has to wait.
 *
 * Each company has its own book and therefore its own lock: AAPL and MSFT can trade
 * at the same time. *
 * NOTE: in this faulty version the order book is still CORRECT (synchronized), so the market keeps
 * running and we can measure the damage done by the bugs in Account and Exchange.
 * Experiment: remove the word synchronized from add, modify and cancel. The ArrayLists get
 * corrupted (they end up containing null) and the market stops within a second.
 */
public class OrderBook {
    final String symbol;
    private final Exchange exchange;
    private final List<Order> buyOrders = new ArrayList<>();
    private final List<Order> sellOrders = new ArrayList<>();
    private long lastPrice;                     // price of the last trade, in cents

    OrderBook(String symbol, long startPrice, Exchange exchange) {
        this.symbol = symbol;
        this.lastPrice = startPrice;
        this.exchange = exchange;
    }

    synchronized void add(Order order) {
        if (order.isBuy) buyOrders.add(order);
        else sellOrders.add(order);
        match();
    }

    /** Changes the price and quantity of an order. Returns false if the order is gone (already traded or cancelled). */
    synchronized boolean modify(long orderId, int traderId, long newPrice, int newQuantity) {
        Order order = find(orderId, traderId);
        if (order == null) return false;
        order.price = newPrice;
        order.quantity = newQuantity;
        match();                                // the new price may now meet another order
        return true;
    }

    /** Removes an order. Returns false if the order is gone (already traded or cancelled). */
    synchronized boolean cancel(long orderId, int traderId) {
        Order order = find(orderId, traderId);
        if (order == null) return false;
        buyOrders.remove(order);
        sellOrders.remove(order);
        return true;
    }

    synchronized long getLastPrice() {
        return lastPrice;
    }

    synchronized String describe() {
        Order bestBuy = best(buyOrders, true);
        Order bestSell = best(sellOrders, false);
        return symbol + ": last " + Main.money(lastPrice)
                + " | " + buyOrders.size() + " buy orders, best " + (bestBuy == null ? "-" : Main.money(bestBuy.price))
                + " | " + sellOrders.size() + " sell orders, best " + (bestSell == null ? "-" : Main.money(bestSell.price));
    }

    /**
     * Trades as long as the best buy price is at least the best sell price.
     * Called only from synchronized methods, so we always hold this book's lock here.
     */
    private void match() {
        while (true) {
            Order buy = best(buyOrders, true);      // highest price someone will pay
            Order sell = best(sellOrders, false);   // lowest price someone will accept
            if (buy == null || sell == null || buy.price < sell.price) return;

            int quantity = Math.min(buy.quantity, sell.quantity);        // n = min(offer, request)
            long price = buy.id < sell.id ? buy.price : sell.price;      // price of the older order

            Exchange.Result result = exchange.transfer(symbol, buy.traderId, sell.traderId, quantity, price);
            if (result == Exchange.Result.BUYER_CANNOT_PAY) {
                buyOrders.remove(buy);              // remove the order that cannot be paid
                continue;
            }
            if (result == Exchange.Result.SELLER_HAS_NO_SHARES) {
                sellOrders.remove(sell);            // remove the order that cannot be delivered
                continue;
            }

            buy.quantity -= quantity;
            sell.quantity -= quantity;
            lastPrice = price;
            if (buy.quantity <= 0) buyOrders.remove(buy);     // nothing left to trade
            if (sell.quantity <= 0) sellOrders.remove(sell);
        }
    }

    /** Best order: highest price for buys, lowest for sells; on equal prices the older one. */
    private static Order best(List<Order> orders, boolean highest) {
        Order best = null;
        for (Order o : orders) {
            if (best == null
                    || (highest ? o.price > best.price : o.price < best.price)
                    || (o.price == best.price && o.id < best.id)) {
                best = o;
            }
        }
        return best;
    }

    private Order find(long orderId, int traderId) {
        for (Order o : buyOrders) if (o.id == orderId && o.traderId == traderId) return o;
        for (Order o : sellOrders) if (o.id == orderId && o.traderId == traderId) return o;
        return null;
    }
}
