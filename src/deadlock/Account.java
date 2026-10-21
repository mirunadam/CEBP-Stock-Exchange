package deadlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A trader's wallet: cash and shares.
 *
 * Every method is synchronized, so only one thread at a time can use a given wallet.
 * (synchronized methods lock "this" Account object. Exchange.transfer locks the same
 * object with synchronized(account), so it is the same lock.)
 */
public class Account {
    final int id;
    private long cash;                                        // in cents
    private final Map<String, Integer> shares = new HashMap<>();

    Account(int id, long cash, List<String> symbols, int sharesEach) {
        this.id = id;
        this.cash = cash;
        for (String symbol : symbols) shares.put(symbol, sharesEach);
    }

    synchronized long getCash() {
        return cash;
    }

    synchronized int getShares(String symbol) {
        return shares.getOrDefault(symbol, 0);
    }

    // Adds money (use a negative amount to take money out)
    synchronized void addCash(long amount) {
        cash += amount;
    }

    // Adds shares (use a negative quantity to take shares out)
    synchronized void addShares(String symbol, int quantity) {
        shares.put(symbol, shares.getOrDefault(symbol, 0) + quantity);
    }
}
