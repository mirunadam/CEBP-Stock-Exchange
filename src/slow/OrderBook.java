package slow;

import java.util.ArrayList;
import java.util.List;

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

    synchronized boolean modify(long orderId, int traderId, long newPrice, int newQuantity) {
        Order order = find(orderId, traderId);
        if (order == null) return false;
        order.price = newPrice;
        order.quantity = newQuantity;
        match();                                // the new price may now meet another order
        return true;
    }

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
