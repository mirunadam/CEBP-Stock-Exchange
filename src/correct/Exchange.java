package correct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The stock exchange. Traders only talk to this class.
 *
 * How the shared data is protected:
 *   books, accounts - filled in the constructor and never changed afterwards,
 *                     so many threads can read these maps at the same time.
 *                     Each OrderBook and each Account protects itself (synchronized).
 *   orderIds,
 *   tradeIds        - AtomicLong: incrementAndGet() gives every thread a different number.
 *   history         - ConcurrentLinkedQueue: a list many threads can add to at once.
 *   open            - volatile: when main sets it to false, every trader sees it.
 *
 * Lock order (this is why there is no deadlock):
 *   1. the company's OrderBook   2. the two Accounts, the SMALLER account id first.
 */
public class Exchange {

    enum Result { OK, BUYER_CANNOT_PAY, SELLER_HAS_NO_SHARES }

    private final Map<String, OrderBook> books = new HashMap<>();
    private final Map<Integer, Account> accounts = new HashMap<>();
    private final Map<String, Long> startPrices;
    private final AtomicLong orderIds = new AtomicLong();
    private final AtomicLong tradeIds = new AtomicLong();
    private final Queue<Trade> history = new ConcurrentLinkedQueue<>();
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

    // ------------------------------------------------------------ used by traders

    // Places a buy or sell order and returns its id
    long placeOrder(int traderId, String symbol, boolean isBuy, long price, int quantity) {
        Order order = new Order(orderIds.incrementAndGet(), traderId, isBuy, price, quantity);
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

    // ------------------------------------------------------------ used by the order books

    /**
     * Moves the money from the buyer to the seller and the shares from the seller to the buyer.
     * Called by OrderBook.match(), so the company's book is already locked.
     */
    Result transfer(String symbol, int buyerId, int sellerId, int quantity, long price) {
        Account buyer = accounts.get(buyerId);
        Account seller = accounts.get(sellerId);

        // Lock BOTH wallets, so the money and the shares move together as one step.
        // Always lock the wallet with the SMALLER id first. Every thread uses the same order,
        // so two threads can never wait for each other in a circle: no deadlock.
        Account first = buyer.id < seller.id ? buyer : seller;
        Account second = first == buyer ? seller : buyer;

        synchronized (first) {
            synchronized (second) {
                long cost = price * quantity;
                if (buyer.getCash() < cost) return Result.BUYER_CANNOT_PAY;
                if (seller.getShares(symbol) < quantity) return Result.SELLER_HAS_NO_SHARES;

                buyer.addCash(-cost);
                seller.addCash(cost);
                seller.addShares(symbol, -quantity);
                buyer.addShares(symbol, quantity);

                history.add(new Trade(tradeIds.incrementAndGet(), symbol, buyerId, sellerId, quantity, price));
                return Result.OK;
            }
        }
    }

    // ------------------------------------------------------------ used by main

    void close() {
        open = false;
    }

    long getTradeCount() {
        return tradeIds.get();
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
