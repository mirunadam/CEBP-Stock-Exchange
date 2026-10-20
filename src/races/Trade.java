package races;

/**
 * One completed trade. A record cannot be changed after it is created,
 * so many threads can read it at the same time without any lock.
 */
public record Trade(long id, String symbol, int buyerId, int sellerId, int quantity, long price) {

    @Override
    public String toString() {
        return "Trade #" + id + ": T" + buyerId + " bought " + quantity + " " + symbol
                + " from T" + sellerId + " at " + Main.money(price);
    }
}
