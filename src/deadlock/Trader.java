package deadlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A robot buyer/seller. Each Trader runs in its own thread.
 *
 * While the market is open it repeats: look at the market, then
 * place a new order (70%), modify one of its orders (20%) or cancel one (10%).
 *
 * myOrders and random belong to this trader only (only its own thread uses them),
 * so they need no lock.
 */
public class Trader implements Runnable {

    private record MyOrder(long id, String symbol, boolean isBuy) {}

    private final int id;
    private final Exchange exchange;
    private final List<MyOrder> myOrders = new ArrayList<>();
    private final Random random = new Random();

    Trader(int id, Exchange exchange) {
        this.id = id;
        this.exchange = exchange;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("trader-" + id);    // so error messages show which trader
        while (exchange.isOpen()) {
            int action = random.nextInt(100);
            if (myOrders.isEmpty() || action < 70) placeOrder();
            else if (action < 90) modifyOrder();
            else cancelOrder();
        }
    }

    private void placeOrder() {
        List<String> symbols = exchange.getSymbols();
        String symbol = symbols.get(random.nextInt(symbols.size()));

        // Strategy, based on the common information (last trade price):
        // if the price fell below where it started, the share looks cheap, so buy more often.
        long lastPrice = exchange.getLastPrice(symbol);
        boolean looksCheap = lastPrice < exchange.getStartPrice(symbol);
        boolean isBuy = random.nextDouble() < (looksCheap ? 0.6 : 0.4);

        long price = priceNear(lastPrice, isBuy);
        int quantity = 1 + random.nextInt(50);

        long orderId = exchange.placeOrder(id, symbol, isBuy, price, quantity);
        myOrders.add(new MyOrder(orderId, symbol, isBuy));

        if (myOrders.size() > 10) {                      // keep at most 10 open orders
            MyOrder oldest = myOrders.remove(0);
            exchange.cancelOrder(id, oldest.symbol(), oldest.id());
        }
    }

    private void modifyOrder() {
        MyOrder order = myOrders.get(random.nextInt(myOrders.size()));
        long newPrice = priceNear(exchange.getLastPrice(order.symbol()), order.isBuy());
        int newQuantity = 1 + random.nextInt(50);
        boolean stillThere = exchange.modifyOrder(id, order.symbol(), order.id(), newPrice, newQuantity);
        if (!stillThere) myOrders.remove(order);        // another thread already traded it
    }

    private void cancelOrder() {
        MyOrder order = myOrders.remove(random.nextInt(myOrders.size()));
        exchange.cancelOrder(id, order.symbol(), order.id());
    }

    //A price within about 1.5% of the last price. Buyers offer a bit more, sellers ask a bit less, so orders meet
    private long priceNear(long lastPrice, boolean isBuy) {
        double change = random.nextDouble() * 0.03 - 0.015;
        change += isBuy ? 0.005 : -0.005;
        return Math.max(1, Math.round(lastPrice * (1 + change)));
    }
}
