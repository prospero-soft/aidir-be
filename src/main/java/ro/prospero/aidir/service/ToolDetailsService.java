package ro.prospero.aidir.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.JsonbArrays;
import ro.prospero.aidir.data.ToolCardDTO;
import ro.prospero.aidir.data.ToolDetailsDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL;

/**
 * One published listing, read whole.
 *
 * <p>The features and the pricing plans come back exactly as the vendor entered them in the onboarding
 * wizard: they are stored as jsonb and were never normalised into tables, because nothing queries across
 * them - they are read for one listing at a time and rendered.
 */
@Service
public class ToolDetailsService {

    /** How many alternatives the strip at the foot of the page shows. */
    private static final int SIMILAR_LIMIT = 3;

    private static final TypeReference<ArrayList<ToolDetailsDTO.Feature>> FEATURES = new TypeReference<>() {
    };
    private static final TypeReference<ArrayList<ToolDetailsDTO.Plan>> PLANS = new TypeReference<>() {
    };

    private final DSLContext dslContext;
    private final ToolCards toolCards;
    private final ToolService toolService;

    public ToolDetailsService(DSLContext dslContext, ToolCards toolCards, ToolService toolService) {
        this.dslContext = dslContext;
        this.toolCards = toolCards;
        this.toolService = toolService;
    }

    public Optional<ToolDetailsDTO> find(long id) {
        return dslContext.selectFrom(TOOL)
                         .where(TOOL.ID.eq(id))
                         .and(TOOL.PUBLISHED.isTrue())
                         .fetchOptional()
                         .map(this::toDetails);
    }

    private ToolDetailsDTO toDetails(ToolRecord tool) {
        ToolCardDTO card = toolCards.toCards(List.of(tool)).getFirst();

        return new ToolDetailsDTO(card,
                                  tool.getLongDescription(),
                                  JsonbArrays.read(tool.getFeatures(), FEATURES),
                                  JsonbArrays.read(tool.getPlans(), PLANS),
                                  tool.getFreeTierPlanId(),
                                  tool.getHighlightPlanId(),
                                  JsonbArrays.readStringArray(tool.getIntegrations()),
                                  tool.getDemoVideoUrl(),
                                  toolService.findImageUrls(tool.getId(), ToolService.SCREENSHOT),
                                  tool.getSubmittedAt(),
                                  similar(tool.getId(), card.categories()));
    }

    /**
     * The other listings in this one's categories, newest first. Shared categories are the only similarity
     * signal the schema carries: there is no rating, popularity or audience to compare on.
     *
     * <p>A tool filed under no category at all gets an empty strip rather than a filler row of whoever
     * happens to be newest.
     */
    private List<ToolCardDTO> similar(long id, List<String> categories) {
        if (categories.isEmpty()) {
            return List.of();
        }

        return toolCards.toCards(dslContext.selectFrom(TOOL)
                                           .where(TOOL.PUBLISHED.isTrue())
                                           .and(TOOL.ID.ne(id))
                                           .and(ToolConditions.inCategories(categories))
                                           .orderBy(TOOL.APPROVED_AT.desc(), TOOL.ID.desc())
                                           .limit(SIMILAR_LIMIT)
                                           .fetch());
    }
}
