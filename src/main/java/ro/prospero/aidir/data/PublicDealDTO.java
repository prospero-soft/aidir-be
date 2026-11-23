package ro.prospero.aidir.data;

import java.time.LocalDate;
import java.util.List;

/**
 * One card in the public deals directory: an approved deal's product, with the schedule and offer of the
 * listing placement that is running today. The card's offer strings are composed on the front end from {@code offer}, the
 * same way the creation form previews them. {@code verified} means the vendor is on a paid plan. The last three
 * fields are read off the vendor's published tool, for the directory's filters. Mirrors PublicDealDTO in aidir-fe
 * (src/types/deal.ts).
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
                            boolean verified,
                            List<String> toolPricingModels,
                            List<String> targetUsers,
                            List<String> integrations) {
}
