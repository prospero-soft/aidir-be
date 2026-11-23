package ro.prospero.aidir.service;

import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.DealPayload.Offer;
import ro.prospero.aidir.data.OfferType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DealDiscountsTest {

    private static Offer offer(OfferType type, String original, String deal, Integer percent) {
        return new Offer(type, "EUR", original == null ? null : new BigDecimal(original),
                         deal == null ? null : new BigDecimal(deal), percent, null, null, null, null, null, null,
                         null, null, null, null, null, null);
    }

    @Test
    void aPercentageOfferKeepsTheVendorsFigure() {
        assertThat(DealDiscounts.percentOf(offer(OfferType.PERCENTAGE, "100", "90", 25))).isEqualTo(25);
    }

    @Test
    void flatDiscountsRoundDown() {
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, "149", "49", null))).isEqualTo(67);
        assertThat(DealDiscounts.percentOf(offer(OfferType.LIFETIME, "100", "30.10", null))).isEqualTo(69);
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, "100", "30", null))).isEqualTo(70);
    }

    @Test
    void onlyADealPriceOfZeroIsAHundredPercent() {
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, "100", "0", null))).isEqualTo(100);
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, "100", "0.01", null))).isEqualTo(99);
    }

    @Test
    void aDiscountUnderOnePercentIsZero() {
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, "100", "99.50", null))).isZero();
    }

    @Test
    void aDraftWhosePricesFormNoDiscountHasNoPercent() {
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, null, "10", null))).isNull();
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, "10", null, null))).isNull();
        assertThat(DealDiscounts.percentOf(offer(OfferType.FIXED, "0", "0", null))).isNull();
        assertThat(DealDiscounts.percentOf(offer(OfferType.LIFETIME, "50", "60", null))).isNull();
        assertThat(DealDiscounts.percentOf(offer(null, "100", "50", null))).isNull();
    }

    @Test
    void otherOfferTypesHaveNoPercentEvenIfOneIsSent() {
        for (OfferType type : new OfferType[]{OfferType.FREE_MONTHS, OfferType.TRIAL, OfferType.CREDITS,
                                              OfferType.BONUS_CREDITS, OfferType.CUSTOM}) {
            assertThat(DealDiscounts.percentOf(offer(type, "100", "50", 50))).isNull();
        }
    }
}
