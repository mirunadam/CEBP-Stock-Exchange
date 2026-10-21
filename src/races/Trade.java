package races;

public record Trade(long id, String symbol, int buyerId, int sellerId, int quantity, long price) {

    @Override
    public String toString() {
        return "Trade #" + id + ": T" + buyerId + " bought " + quantity + " " + symbol
                + " from T" + sellerId + " at " + Main.money(price);
    }
}
