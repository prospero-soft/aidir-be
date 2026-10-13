package ro.prospero.aidir.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Result;
import org.jooq.SortField;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.DealDTO;
import ro.prospero.aidir.data.DealPayload.Info;
import ro.prospero.aidir.data.DealPayload.Offer;
import ro.prospero.aidir.data.DealPayload.PlacementContent;
import ro.prospero.aidir.data.DealPayload.Schedule;
import ro.prospero.aidir.data.DealStatus;
import ro.prospero.aidir.data.OfferType;
import ro.prospero.aidir.data.Placement;
import ro.prospero.aidir.jooq.generated.public_.tables.records.DealPlacementRecord;
import ro.prospero.aidir.jooq.generated.public_.tables.records.DealRecord;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL;
import static ro.prospero.aidir.jooq.generated.public_.Tables.DEAL_PLACEMENT;

/**
 * The deal tables, read into {@link DealDTO} and written from the form's {@link PlacementContent}. The
 * vendor, review and public services all go through here, so a deal reads the same wherever it is shown.
 *
 * <p>Every method takes the DSLContext to run on, so callers can use it inside their own transaction.
 */
@Component
public class DealRows {
    /**
     * Unknown properties are ignored when reading: {@code offer_terms} holds what an earlier version of the
     * form wrote, and a field dropped since must not make the deal unreadable.
     */
    private static final ObjectMapper TERMS_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final MediaUrls mediaUrls;

    public DealRows(MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    /** A deal with the account it belongs to, which the DTO leaves out. */
    public record StoredDeal(long accountId, DealDTO deal) {
    }

    /** The fields of an offer that only some offer types use - the {@code offer_terms} column. */
    private record OfferTerms(String currency,
                              BigDecimal originalPrice,
                              BigDecimal dealPrice,
                              Integer discountMonths,
                              Integer trialDays,
                              String billingPeriod,
                              String creditType,
                              BigDecimal creditAmount,
                              BigDecimal estimatedValue,
                              String customText) {
    }

    public List<StoredDeal> read(DSLContext ctx, Condition condition, SortField<?>... order) {
        List<DealRecord> deals = ctx.selectFrom(DEAL).where(condition).orderBy(order).fetch();
        if (deals.isEmpty()) {
            return List.of();
        }
        // One query for the placements of every deal read, not one per deal.
        Map<Long, Result<DealPlacementRecord>> placements =
                ctx.selectFrom(DEAL_PLACEMENT)
                   .where(DEAL_PLACEMENT.DEAL_ID.in(deals.stream().map(DealRecord::getId).toList()))
                   .fetchGroups(DEAL_PLACEMENT.DEAL_ID);

        return deals.stream()
                    .map(deal -> new StoredDeal(deal.getAccountId(),
                                                toDto(deal, placements.containsKey(deal.getId())
                                                            ? placements.get(deal.getId())
                                                            : List.of())))
                    .toList();
    }

    public Optional<StoredDeal> readOne(DSLContext ctx, Condition condition) {
        return read(ctx, condition).stream().findFirst();
    }

    /** The stored logo of each placement that has one, keyed by {@link Placement#wire()}. */
    public Map<String, String> logoPaths(DSLContext ctx, long dealId) {
        return ctx.select(DEAL_PLACEMENT.PLACEMENT, DEAL_PLACEMENT.LOGO_PATH)
                  .from(DEAL_PLACEMENT)
                  .where(DEAL_PLACEMENT.DEAL_ID.eq(dealId))
                  .and(DEAL_PLACEMENT.LOGO_PATH.isNotNull())
                  .fetchMap(DEAL_PLACEMENT.PLACEMENT, DEAL_PLACEMENT.LOGO_PATH);
    }

    /** {@code content} is null for a placement without content of its own, which is then just its key. */
    public void insertPlacement(DSLContext tx, long dealId, Placement placement,
                                PlacementContent content, String logoPath) {
        DealPlacementRecord row = tx.newRecord(DEAL_PLACEMENT);
        row.setDealId(dealId);
        row.setPlacement(placement.wire());

        if (content != null) {
            Schedule schedule = content.schedule();
            row.setStartDate(schedule.startDate());
            row.setDurationDays(schedule.durationDays());

            Info info = content.info();
            row.setProductName(info.productName());
            row.setCategory(info.category());
            row.setBrand(info.brand());
            row.setShortDescription(info.description());
            row.setLogoPath(logoPath);
            row.setLine1(info.line1());
            row.setLine2(info.line2());

            Offer offer = content.offer();
            row.setOfferType(offer.offerType() == null ? null : offer.offerType().wire());
            row.setOfferTerms(writeTerms(new OfferTerms(offer.currency(), offer.originalPrice(), offer.dealPrice(),
                                                        offer.discountMonths(), offer.trialDays(),
                                                        offer.billingPeriod(), offer.creditType(),
                                                        offer.creditAmount(), offer.estimatedValue(),
                                                        offer.customText())));
            row.setDealLabel(offer.dealLabel());
            row.setButtonText(offer.buttonText());
            row.setDestinationUrl(offer.destinationUrl());
            row.setSpotlightText(offer.spotlightText());
            row.setBannerBadge(offer.bannerBadge());
        }

        row.insert();
    }

    private DealDTO toDto(DealRecord deal, List<DealPlacementRecord> rows) {
        // Rows come back in no particular order; the form's order is the enum's.
        List<DealPlacementRecord> ordered = rows.stream()
                                                .sorted(Comparator.comparing(row -> Placement.fromWire(row.getPlacement())))
                                                .toList();

        Map<String, PlacementContent> perPlacement = new LinkedHashMap<>();
        for (DealPlacementRecord row : ordered) {
            if (Placement.fromWire(row.getPlacement()).hasContent()) {
                perPlacement.put(row.getPlacement(), toContent(row));
            }
        }

        return new DealDTO(deal.getId(),
                           DealStatus.fromWire(deal.getStatus()),
                           ordered.stream().map(row -> Placement.fromWire(row.getPlacement())).toList(),
                           deal.getSponsored(),
                           deal.getSponsoredLabel(),
                           deal.getCampaign(),
                           deal.getInternalCampaignId(),
                           deal.getLongDescription(),
                           perPlacement,
                           deal.getRejectionReason(),
                           deal.getCreatedAt(),
                           deal.getUpdatedAt(),
                           deal.getSubmittedAt(),
                           deal.getReviewedAt());
    }

    private PlacementContent toContent(DealPlacementRecord row) {
        OfferTerms terms = readTerms(row.getOfferTerms());
        return new PlacementContent(
                new Schedule(row.getDurationDays(), row.getStartDate()),
                new Info(row.getProductName(), row.getCategory(), row.getBrand(), row.getShortDescription(),
                         row.getLogoPath() == null ? null : mediaUrls.publicUrl(row.getLogoPath()),
                         row.getLine1(), row.getLine2()),
                new Offer(row.getOfferType() == null ? null : OfferType.fromWire(row.getOfferType()),
                          terms.currency(), terms.originalPrice(), terms.dealPrice(), terms.discountMonths(),
                          terms.trialDays(), terms.billingPeriod(), terms.creditType(), terms.creditAmount(),
                          terms.estimatedValue(), terms.customText(), row.getDealLabel(), row.getButtonText(),
                          row.getDestinationUrl(), row.getSpotlightText(), row.getBannerBadge()));
    }

    private static JSONB writeTerms(OfferTerms terms) {
        try {
            return JSONB.valueOf(TERMS_MAPPER.writeValueAsString(terms));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the offer terms", e);
        }
    }

    private static OfferTerms readTerms(JSONB jsonb) {
        if (jsonb == null) {
            return new OfferTerms(null, null, null, null, null, null, null, null, null, null);
        }
        try {
            return TERMS_MAPPER.readValue(jsonb.data(), OfferTerms.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed offer terms in deal_placement: " + jsonb.data(), e);
        }
    }
}
