package slow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    synchronized void addCash(long amount) {
        cash += amount;
    }

    synchronized void addShares(String symbol, int quantity) {
        shares.put(symbol, shares.getOrDefault(symbol, 0) + quantity);
    }
}
