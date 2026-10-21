# Stock Exchange – Concurrent Programming in Java

A simulated stock exchange where **16 trader threads** buy and sell shares of 4 companies at the same time.
It's built with the standard Java Concurrency API. The project contains one **correct** implementation and four
**intentionally faulty** versions, each showing a classic concurrency problem: data races, deadlock, livelock and poor performance.


---

## The market

- **Sellers** offer shares at a minimum price; **buyers** ask for shares at a maximum price.
- When buy price ≥ sell price, they trade **n = min(offer, request)** shares, at the price of the older order.
- Everyone can see the last price of each company and the full trade history.
- Orders can be modified or cancelled at any time, **but not while a trade on that company is in progress**.

Each trader starts with $100,000 and 1,000 shares of AAPL, AMZN, GOOG and MSFT. It trades for 5 seconds:
it places orders (70%), modifies them (20%) or cancels them (10%), and buys more often when a share trades below its starting price.

---

## How to run

Run one of the launchers in `src/`:

| Launcher | Version | Expected result |
|---|---|---|
| `RunCorrect.java` | correct | ~550,000 trades/s · `ALL CHECKS PASSED` |
| `RunRaces.java` | faulty: data races | money off by ~$10M, duplicate ids, lost trades |
| `RunDeadlock.java` | faulty: deadlock | freezes in < 1 s · `DEADLOCK found` |
| `RunLivelock.java` | faulty: livelock | freezes · no deadlock · millions of retries |
| `RunSlow.java` | faulty: performance | correct, but only ~890 trades/s |

Settings (traders, duration, starting money) are constants at the top of each `Main.java`.
Numbers depend on your machine.

---

## Architecture


Every package contains the same 7 classes:

| Class | Role |
|---|---|
| `Main` | Starts the traders in a thread pool, prints progress, detects a frozen market, stops the traders, verifies the results |
| `Trader` | One thread per trader: reads the market, then places, modifies or cancels orders |
| `Exchange` | The single entry point for traders; `transfer()` moves money and shares between two wallets |
| `OrderBook` | All orders of one company; `match()` executes trades |
| `Order` | A buy or sell order (price, remaining quantity) |
| `Account` | A trader's wallet (cash, shares) |
| `Trade` | An immutable record of a completed trade |

<img width="1360" height="1020" alt="class-diagram" src="https://github.com/user-attachments/assets/b8789ef4-4a43-40c9-ad7e-4b14ebed80f0" />


---

## Our approach for each version

Every faulty package is an exact copy of `correct` with a few lines changed and marked **`// BUG`**.
To see exactly what differs, compare any file with its `correct` counterpart.

### `correct` – one lock per company, both wallets locked in a fixed order

Each `OrderBook` protects its company's orders with its own lock (`synchronized` methods). Adding, modifying,
cancelling and matching orders of one company never overlap, but different companies trade in parallel.
When two orders match, `transfer()` locks **both** traders' wallets, checks that the buyer can pay and the seller has the shares,
and moves money and shares together as one atomic step. The wallet with the **smaller id is always locked first**,
so threads can never wait for each other in a circle.

| Shared data | Protection |
|---|---|
| Orders of one company | `synchronized` `OrderBook` methods: one lock per company |
| Wallets | `synchronized` `Account` methods + `transfer()` locks both wallets, smaller id first |
| Order and trade ids | `AtomicLong.incrementAndGet()` |
| Trade history | `ConcurrentLinkedQueue` |
| Market open flag | `volatile boolean` |
| Maps of books and accounts | Built before the threads start, never changed |
| Each trader's own orders and `Random` | Used by one thread only (thread confinement) |

**Lock order:** the company's `OrderBook` first, then the two `Account`s, smaller id first.

**Shutdown:** `main` sets the `volatile` flag, calls `shutdown()` and `awaitTermination()`, then `Future.get()` on every trader,
and only then reads the results.

**Verification:** after trading stops, `Main` checks the following:
- total money unchanged ($1,600,000),
- total shares of each company unchanged (16,000),
- no negative balances,
- every trade in the history,
- every trade id unique.

### `races` – faulty: data races and data consistency

**Approach:** remove the protection of the shared money and counters, keep everything else the same.
`Account` methods aren't `synchronized`, `transfer()` takes no locks, the ids are a plain `long` with `++`,
and the history is a plain `ArrayList`. The order books are still locked, so the market keeps running and the damage can be measured.

**Result:** lost updates create money out of nothing, two trades get the same id, and trades disappear from the history.
The numbers are different on every run.

### `deadlock` – faulty: deadlock

**Approach:** change only the lock order in `transfer()`. It now locks the **buyer first, then the seller**.

**Result:** "T1 buys from T2" and "T2 buys from T1" in two different companies each hold one wallet and wait for the other forever.
The market freezes in under a second; `Main` asks the JVM (`ThreadMXBean`) and prints the cycle of waiting threads.

### `livelock` – faulty: livelock

**Approach:** try to avoid the deadlock by being "polite". `transfer()` uses `tryLock()`, so it never waits, and steps back and retries
whenever another transfer wants the same wallet.

**Result:** two transfers keep stepping back for each other forever. The threads stay busy (RUNNABLE) and millions of retries happen,
but no trades. No deadlock is detected, because nobody is blocked.

### `slow` – faulty: poor performance

**Approach:** keep everything correct, but lock too much and do slow work inside the lock. Every `Exchange` method is
`synchronized` on the whole exchange, and each trade does a 1 ms "database write" while holding the locks.

**Result:** all checks still pass, but only one trader works at a time: about 890 trades/s instead of about 550,000.

---

## Summary

| Version | Problem shown | Changed lines (`// BUG`) | Fixed in `correct` by |
|---|---|---|---|
| `races` | data races | no locks on wallets, `long ++` ids, `ArrayList` history | `synchronized`, `AtomicLong`, `ConcurrentLinkedQueue` |
| `deadlock` | deadlock | buyer locked before seller | fixed lock order (smaller id first) |
| `livelock` | livelock | `tryLock()` + step back and retry | plain blocking locks in a fixed order |
| `slow` | performance | one global lock + slow work inside it | one lock per company, short critical sections |

---

## Project structure

```
src/
├── RunCorrect.java  RunRaces.java  RunDeadlock.java  RunLivelock.java  RunSlow.java
├── correct/   races/   deadlock/   livelock/   slow/
│   └── Main, Trader, Exchange, OrderBook, Order, Account, Trade
docs/
└── class-diagram.svg
```

## Team
- Damșa Andra-Miruna
- Efrem Giulia
- Tomici Biliana
- Berzava Denisa
- Buciuman Paul
