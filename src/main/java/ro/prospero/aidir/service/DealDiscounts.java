package ro.prospero.aidir.service;

import ro.prospero.aidir.data.DealPayload.Offer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The percentage the directory filters and sorts a deal on. Prices round down, so a deal never lands in a
 * savings bracket it falls short of, and only a deal price of exactly 0 counts as 100% off.
 */
final class DealDiscounts {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DealDiscounts() {
    }

    /** Null for an offer with no percentage, and for a draft whose prices do not form a discount yet. */
    static Integer percentOf(Offer offer) {
        if (offer.offerType() == null) {
            return null;
        }
        return switch (offer.offerType()) {
            case PERCENTAGE -> offer.discountPercent();
            case FIXED, LIFETIME -> fromPrices(offer.originalPrice(), offer.dealPrice());
            case FREE_MONTHS, TRIAL, CREDITS, BONUS_CREDITS, CUSTOM -> null;
        };
    }

    private static Integer fromPrices(BigDecimal original, BigDecimal deal) {
        if (original == null || deal == null || original.signum() <= 0 || deal.compareTo(original) >= 0) {
            return null;
        }
        return original.subtract(deal).multiply(HUNDRED).divide(original, 0, RoundingMode.DOWN).intValue();
    }
}
