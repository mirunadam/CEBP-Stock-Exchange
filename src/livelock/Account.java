package livelock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// A trader's wallet: some cash and some shares of each company.
public class Account {
    final int id;

    // Only transfer() uses these two, they are what makes the livelock possible.
    final ReentrantLock lock = new ReentrantLock();         // unlike synchronized, we can tryLock() it without waiting
    final AtomicInteger wantedBy = new AtomicInteger();     // how many transfers want this wallet right now
    private long cash;                                        // in cents, so 18482 = $184.82
    private final Map<String, Integer> shares = new HashMap<>();   // company -> how many shares

    Account(int id, long cash, List<String> symbols, int sharesEach) {
        this.id = id;
        this.cash = cash;
        for (String symbol : symbols) shares.put(symbol, sharesEach);   // same shares of every company at the start
    }

    // These are synchronized so one read or write at a time is safe.
    synchronized long getCash() {
        return cash;
    }

    synchronized int getShares(String symbol) {
        return shares.getOrDefault(symbol, 0);
    }

    /** Adds money, use a negative amount to take money out. */
    synchronized void addCash(long amount) {
        cash += amount;
    }

    /** Adds shares, use a negative quantity to take shares out. */
    synchronized void addShares(String symbol, int quantity) {
        shares.put(symbol, shares.getOrDefault(symbol, 0) + quantity);
    }
}