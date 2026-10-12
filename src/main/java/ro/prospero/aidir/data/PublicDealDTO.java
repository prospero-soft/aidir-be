package ro.prospero.aidir.data;

import java.time.LocalDate;

/**
 * One card in the public deals directory: an approved deal, with the content of the listing placement
 * that is running today. The card's offer strings are composed on the front end from {@code offer}, the
 * same way the creation form previews them. {@code verified} means the vendor is on a paid plan. Mirrors
 * PublicDealDTO in aidir-fe (src/types/deal.ts).
 */
public record PublicDealDTO(long id,
                            Placement placement,
                            String productName,
                            String brand,
                            String description,
                            String category,
                            String logoUrl,
                            DealPayload.Offer offer,
                            LocalDate startDate,
                            LocalDate endDate,
                            boolean verified) {
}
