package races;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Starts the simulation: creates the exchange, starts the trader threads, lets them trade,
 * stops them, prints a report and checks that no money or shares were lost or created.
 */
public class Main {

    static final String VERSION = "FAULTY version 1: DATA RACES";
    static final int TRADERS = 16;
    static final int SECONDS = 5;
    static final long START_CASH = 100_000_00;      // $100,000.00 per trader (in cents)
    static final int START_SHARES = 1_000;          // of every company, per trader

    public static void main(String[] args) throws Exception {
        Map<String, Long> startPrices = new LinkedHashMap<>();
        startPrices.put("AAPL", 190_00L);
        startPrices.put("AMZN", 180_00L);
        startPrices.put("GOOG", 160_00L);
        startPrices.put("MSFT", 420_00L);

        Exchange exchange = new Exchange(startPrices, TRADERS, START_CASH, START_SHARES);
        System.out.println(VERSION + ": " + TRADERS + " traders, " + SECONDS + " seconds\n");

        // 1. Start the traders: each Trader is a task run by a thread of the pool.
        ExecutorService pool = Executors.newFixedThreadPool(TRADERS);
        List<Future<?>> results = new ArrayList<>();
        for (int id = 1; id <= TRADERS; id++) {
            results.add(pool.submit(new Trader(id, exchange)));
        }

        // 2. Let them trade, print progress every second, stop if the market freezes.
        if (marketFroze(exchange)) System.exit(1);

        // 3. Close the market and wait until every trader has stopped.
        exchange.close();
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            System.out.println("Some traders did not stop!");
            System.exit(1);
        }
        for (Future<?> result : results) {
            try {
                result.get();                       // throws if the trader crashed
            } catch (ExecutionException e) {
                System.out.println("A trader crashed: " + e.getCause());
            }
        }

        // 4. Only the main thread is left: print the report and check the results.
        printReport(exchange);
        boolean ok = checkResults(exchange);
        System.exit(ok ? 0 : 1);
    }

    /** Prints the number of trades every second. Returns true if no trade happened for 2 seconds in a row. */
    static boolean marketFroze(Exchange exchange) throws InterruptedException {
        long before = 0;
        int quietSeconds = 0;
        for (int second = 1; second <= SECONDS; second++) {
            Thread.sleep(1000);
            long trades = exchange.getTradeCount();
            System.out.printf("  after %d s: %,d trades (+%,d)%n", second, trades, trades - before);
            quietSeconds = trades == before ? quietSeconds + 1 : 0;
            before = trades;
            if (quietSeconds == 2) {
                explainWhyFrozen();
                return true;
            }
        }
        return false;
    }

    /** Asks the JVM whether the threads are in a deadlock and prints what it finds. */
    static void explainWhyFrozen() {
        System.out.println("\nThe market is FROZEN: no trades for 2 seconds.");
        ThreadMXBean jvm = ManagementFactory.getThreadMXBean();
        long[] deadlocked = jvm.findDeadlockedThreads();
        if (deadlocked != null) {
            System.out.println("DEADLOCK found. These threads wait for each other forever:");
            for (ThreadInfo t : jvm.getThreadInfo(deadlocked)) {
                String lock = t.getLockName().replaceAll(".*\\.|@.*", "");    // e.g. "Account" or "OrderBook"
                System.out.println("  " + t.getThreadName() + " waits for an " + lock + " lock held by " + t.getLockOwnerName());
            }
        } else {
            Map<Thread.State, Integer> states = new TreeMap<>();
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().startsWith("trader")) states.merge(t.getState(), 1, Integer::sum);
            }
            System.out.println("No deadlock found. Trader thread states: " + states);
            System.out.println("RUNNABLE threads that make no progress = LIVELOCK.");
        }
    }

    static void printReport(Exchange exchange) {
        System.out.println("\nOrder books at closing time:");
        for (OrderBook book : exchange.getBooks()) System.out.println("  " + book.describe());

        List<Trade> history = exchange.getHistory();
        System.out.println("\nLast 3 trades:");
        for (int i = Math.max(0, history.size() - 3); i < history.size(); i++) {
            System.out.println("  " + history.get(i));
        }
        System.out.printf("%nTotal: %,d trades, %,d trades per second%n",
                exchange.getTradeCount(), exchange.getTradeCount() / SECONDS);
    }

    /** Checks the rules that must always hold. Returns true if all of them pass. */
    static boolean checkResults(Exchange exchange) {
        System.out.println("\nChecks:");
        boolean ok = true;

        long totalCash = 0;
        Map<String, Long> totalShares = new TreeMap<>();
        int negative = 0;
        for (Account account : exchange.getAccounts()) {
            totalCash += account.getCash();
            if (account.getCash() < 0) negative++;
            for (String symbol : exchange.getSymbols()) {
                int shares = account.getShares(symbol);
                totalShares.merge(symbol, (long) shares, Long::sum);
                if (shares < 0) negative++;
            }
        }

        long expectedCash = START_CASH * TRADERS;
        ok &= check(totalCash == expectedCash,
                "Total money unchanged: " + money(totalCash) + " (expected " + money(expectedCash) + ")");
        for (String symbol : totalShares.keySet()) {
            long expected = (long) START_SHARES * TRADERS;
            ok &= check(totalShares.get(symbol) == expected,
                    "Total " + symbol + " shares unchanged: " + totalShares.get(symbol) + " (expected " + expected + ")");
        }
        ok &= check(negative == 0, "Nobody has negative money or shares (" + negative + " found)");

        List<Trade> history = exchange.getHistory();
        ok &= check(history.size() == exchange.getTradeCount(),
                "Every trade is in the history: " + history.size() + " of " + exchange.getTradeCount());

        Set<Long> ids = new HashSet<>();
        int duplicates = 0, empty = 0;
        for (Trade trade : history) {
            if (trade == null) empty++;                         // a broken ArrayList can contain null
            else if (!ids.add(trade.id())) duplicates++;
        }
        ok &= check(duplicates == 0 && empty == 0,
                "Every trade has its own id (" + duplicates + " duplicates, " + empty + " empty entries)");


        System.out.println(ok ? "\nRESULT: ALL CHECKS PASSED" : "\nRESULT: SOME CHECKS FAILED");
        return ok;
    }

    static boolean check(boolean passed, String description) {
        System.out.println((passed ? "  [PASS] " : "  [FAIL] ") + description);
        return passed;
    }

    /** 18482 cents -> "$184.82" */
    static String money(long cents) {
        return String.format("$%,d.%02d", cents / 100, Math.abs(cents % 100));
    }
}
