package livelock;

// One finished trade. It's a record, so it can never change after creation
// and any thread can read it without a lock.
public record Trade(long id, String symbol, int buyerId, int sellerId, int quantity, long price) {

    // how a trade looks in the report
    @Override
    public String toString() {
        return "Trade #" + id + ": T" + buyerId + " bought " + quantity + " " + symbol
                + " from T" + sellerId + " at " + Main.money(price);
    }
}