package races;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traders only talk to this class.
 *
 * problem : the wallets, the ids and the history are NOT protected.
 * The order books are still locked, so the market keeps running.
 *   orderIds, tradeIds - plain long with ++: two threads can get the SAME number.
 *   history            - plain ArrayList: adds from many threads get lost or throw exceptions.
 *   transfer           - no locks: money and shares are changed by many threads at once.
 *   open               - still volatile (that part is correct).
 */
public class Exchange {

    enum Result { OK, BUYER_CANNOT_PAY, SELLER_HAS_NO_SHARES }

    private final Map<String, OrderBook> books = new HashMap<>();
    private final Map<Integer, Account> accounts = new HashMap<>();
    private final Map<String, Long> startPrices;
    private long orderIds = 0;
    private long tradeIds = 0;
    private final List<Trade> history = new ArrayList<>();
    private volatile boolean open = true;

    Exchange(Map<String, Long> startPrices, int traders, long cashEach, int sharesEach) {
        this.startPrices = startPrices;
        for (String symbol : startPrices.keySet()) {
            books.put(symbol, new OrderBook(symbol, startPrices.get(symbol), this));
        }
        for (int id = 1; id <= traders; id++) {
            accounts.put(id, new Account(id, cashEach, getSymbols(), sharesEach));
        }
    }



    long placeOrder(int traderId, String symbol, boolean isBuy, long price, int quantity) {
        long id = ++orderIds;                       // BUG: ++ is read, add, write: two threads can get the same id
        Order order = new Order(id, traderId, isBuy, price, quantity);
        books.get(symbol).add(order);
        return order.id;
    }

    boolean modifyOrder(int traderId, String symbol, long orderId, long newPrice, int newQuantity) {
        return books.get(symbol).modify(orderId, traderId, newPrice, newQuantity);
    }

    boolean cancelOrder(int traderId, String symbol, long orderId) {
        return books.get(symbol).cancel(orderId, traderId);
    }

    long getLastPrice(String symbol) {
        return books.get(symbol).getLastPrice();
    }

    long getStartPrice(String symbol) {
        return startPrices.get(symbol);
    }

    List<String> getSymbols() {
        return new ArrayList<>(startPrices.keySet());
    }

    boolean isOpen() {
        return open;
    }






    Result transfer(String symbol, int buyerId, int sellerId, int quantity, long price) {
        Account buyer = accounts.get(buyerId);
        Account seller = accounts.get(sellerId);

        // problem: no locks. Between the check ("can the buyer pay?") and the change, another
        // thread can spend the same money (check-then-act), and changes can overwrite each other.
        // solution: synchronized on both accounts, smaller id first.
        long cost = price * quantity;
        if (buyer.getCash() < cost) return Result.BUYER_CANNOT_PAY;
        if (seller.getShares(symbol) < quantity) return Result.SELLER_HAS_NO_SHARES;

        buyer.addCash(-cost);
        seller.addCash(cost);
        seller.addShares(symbol, -quantity);
        buyer.addShares(symbol, quantity);

        history.add(new Trade(++tradeIds, symbol, buyerId, sellerId, quantity, price));   // BUG: ++ and ArrayList
        return Result.OK;
    }




    void close() {
        open = false;
    }

    long getTradeCount() {
        return tradeIds;
    }

    List<Trade> getHistory() {
        return new ArrayList<>(history);
    }

    Collection<Account> getAccounts() {
        return accounts.values();
    }

    Collection<OrderBook> getBooks() {
        return books.values();
    }
}
