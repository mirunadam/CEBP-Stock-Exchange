package races;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// a trader's wallet: cash and shares.
// problem: the methods are NOT synchronized. Two threads can change the same wallet at the same time. "cash += amount" is three steps (read, add, write), so one change can be lost.
//solution: make every method synchronized.

public class Account {
    final int id;
    private long cash;
    private final Map<String, Integer> shares = new HashMap<>();

    Account(int id, long cash, List<String> symbols, int sharesEach) {
        this.id = id;
        this.cash = cash;
        for (String symbol : symbols) shares.put(symbol, sharesEach);
    }

    long getCash() {                                // BUG: not synchronized
        return cash;
    }

    int getShares(String symbol) {
        return shares.getOrDefault(symbol, 0);
    }

    void addCash(long amount) {                     // BUG: not synchronized, updates can be lost
        cash += amount;
    }

    void addShares(String symbol, int quantity) {   // BUG: not synchronized, HashMap changed by many threads
        shares.put(symbol, shares.getOrDefault(symbol, 0) + quantity);
    }
}
