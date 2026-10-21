package livelock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

// The front desk of the market, traders only talk to this class.
// BUG version: transfer() tries to avoid deadlock by being "polite" and ends up in a livelock.
public class Exchange {

    enum Result { OK, BUYER_CANNOT_PAY, SELLER_HAS_NO_SHARES }

    // The two maps are filled in the constructor and never change, so no lock needed to read them.
    private final Map<String, OrderBook> books = new HashMap<>();
    private final Map<Integer, Account> accounts = new HashMap<>();
    private final Map<String, Long> startPrices;
    private final AtomicLong orderIds = new AtomicLong();       // atomic so two threads never get the same id
    private final AtomicLong tradeIds = new AtomicLong();
    private final Queue<Trade> history = new ConcurrentLinkedQueue<>();   // many threads can add at once
    private volatile boolean open = true;                       // volatile so every trader sees when main closes the market
    static final AtomicLong retries = new AtomicLong();     // how many times a transfer stepped back

    Exchange(Map<String, Long> startPrices, int traders, long cashEach, int sharesEach) {
        this.startPrices = startPrices;
        // one order book per company
        for (String symbol : startPrices.keySet()) {
            books.put(symbol, new OrderBook(symbol, startPrices.get(symbol), this));
        }
        // one wallet per trader
        for (int id = 1; id <= traders; id++) {
            accounts.put(id, new Account(id, cashEach, getSymbols(), sharesEach));
        }
    }

    // used by traders

    /** Places a buy or sell order and returns its id. */
    long placeOrder(int traderId, String symbol, boolean isBuy, long price, int quantity) {
        // get a unique id, build the order, hand it to that company's book (which tries to match it)
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
        return new ArrayList<>(startPrices.keySet());   // a copy, so nobody can change ours
    }

    boolean isOpen() {
        return open;
    }

    // used by the order books

    /**
     * Moves the money from the buyer to the seller and the shares from the seller to the buyer.
     * Called by OrderBook.match(), so the company's book is already locked.
     */
    Result transfer(String symbol, int buyerId, int sellerId, int quantity, long price) {
        Account buyer = accounts.get(buyerId);
        Account seller = accounts.get(sellerId);

        // BUG (livelock): a "polite" way to avoid deadlock.
        // 1. The transfer says "I want these two wallets" (wantedBy + 1 on each).
        // 2. It TRIES to take both locks (tryLock never waits, so there is no deadlock).
        // 3. If ANOTHER transfer also wants one of these wallets, it politely lets go and tries again.
        // The other transfer does exactly the same thing, so both keep stepping aside forever,
        // like two people in a corridor. Nobody is blocked, the threads keep running, no trade happens.
        // FIX (correct version): lock the two wallets in a fixed order (smaller id first).

        // a trader can match with himself, then buyer and seller are the same wallet
        boolean sameWallet = buyer == seller;
        // announce that we want these wallets
        buyer.wantedBy.incrementAndGet();
        if (!sameWallet) seller.wantedBy.incrementAndGet();
        try {
            while (true) {                                  // keep trying until the trade is done
                if (buyer.lock.tryLock()) {                 // try the buyer's wallet, don't wait
                    try {
                        if (seller.lock.tryLock()) {        // then the seller's wallet, don't wait
                            try {
                                // our own +1 is already counted, so more than 1 means someone else wants it too
                                boolean someoneElseWants = !sameWallet
                                        && (buyer.wantedBy.get() > 1 || seller.wantedBy.get() > 1);
                                if (!someoneElseWants) {
                                    // nobody else wants them, we hold both locks, do the real work
                                    return moveMoneyAndShares(symbol, buyer, seller, quantity, price);
                                }
                                // BUG: "after you!" - let go and try again
                            } finally {
                                seller.lock.unlock();       // always release, even if something throws
                            }
                        }
                    } finally {
                        buyer.lock.unlock();
                    }
                }
                // we stepped back, count it for the report and let other threads run
                retries.incrementAndGet();
                Thread.yield();
            }
        } finally {
            // whatever happens, take our announcement back or the counters stay too high forever
            buyer.wantedBy.decrementAndGet();
            if (!sameWallet) seller.wantedBy.decrementAndGet();
        }
    }

    /** The same work as in the correct version, done once both wallets are locked. */
    private Result moveMoneyAndShares(String symbol, Account buyer, Account seller, int quantity, long price) {
        long cost = price * quantity;
        // check first, then move (the locks make sure nothing changes in between)
        if (buyer.getCash() < cost) return Result.BUYER_CANNOT_PAY;
        if (seller.getShares(symbol) < quantity) return Result.SELLER_HAS_NO_SHARES;

        buyer.addCash(-cost);
        seller.addCash(cost);
        seller.addShares(symbol, -quantity);
        buyer.addShares(symbol, quantity);

        history.add(new Trade(tradeIds.incrementAndGet(), symbol, buyer.id, seller.id, quantity, price));
        return Result.OK;
    }

    // used by main

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