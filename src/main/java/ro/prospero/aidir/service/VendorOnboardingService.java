package ro.prospero.aidir.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ro.prospero.aidir.data.VendorOnboardingResult;
import ro.prospero.aidir.data.VendorSubmissionPayload;
import ro.prospero.aidir.data.VendorSubmissionPayload.PricingPlan;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.public_.Tables.COMPANY_INFORMATION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN;
import static ro.prospero.aidir.jooq.generated.public_.Tables.PLAN_PRICE;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL_SUBMISSION;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL_SUBMISSION_IMAGE_METADATA;

/**
 * The vendor wizard's one write: an account, the company behind it, and a tool submission parked for
 * review, from a single POST.
 *
 * <p>The three are one transaction because a vendor who cannot sign in and a submission with no owner are
 * both worse than a failed signup. Transactions come from jOOQ rather than {@code @Transactional}:
 * JooqConfig builds its DSLContext over the raw DataSource, so Spring's transaction manager would not
 * enrol these statements (the same reason {@link SubscriptionService} works this way).
 */
@Service
public class VendorOnboardingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VendorOnboardingService.class);

    private static final String VENDOR = "vendor";
    private static final String MONTHLY = "monthly";
    private static final String CURRENCY = "EUR";

    private static final String LOGO = "logo";
    private static final String SCREENSHOT = "screenshot";
    private static final String SUBMISSION_DIR = "tool-submissions/";

    private final DSLContext dslContext;
    private final FileStorageService fileStorageService;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public VendorOnboardingService(DSLContext dslContext,
                                   FileStorageService fileStorageService,
                                   SubscriptionService subscriptionService,
                                   PasswordEncoder passwordEncoder,
                                   ObjectMapper objectMapper) {
        this.dslContext = dslContext;
        this.fileStorageService = fileStorageService;
        this.subscriptionService = subscriptionService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    public VendorOnboardingResult onboard(VendorSubmissionPayload payload,
                                          MultipartFile logo,
                                          List<MultipartFile> screenshots) {
        // Files are written inside the transaction because their directory is named after the submission
        // id, which only exists once the row is in. Whatever was written before a failure is tracked here
        // so it can be removed: the database unwinds itself, the filesystem does not.
        List<String> storedPaths = new ArrayList<>();
        VendorOnboardingResult result;
        try {
            result = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                long accountId = insertAccount(tx, payload);
                insertCompanyInformation(tx, accountId, payload.company());
                long submissionId = insertSubmission(tx, accountId, payload);
                storeImages(tx, submissionId, logo, screenshots, storedPaths);
                return new VendorOnboardingResult(accountId, submissionId);
            });
        } catch (RuntimeException failure) {
            storedPaths.forEach(fileStorageService::delete);
            throw failure;
        }

        openSubscription(result.accountId(), payload);
        return result;
    }

    private long insertAccount(DSLContext tx, VendorSubmissionPayload payload) {
        Long accountId = tx.insertInto(ACCOUNT)
                           .set(ACCOUNT.EMAIL, payload.company().workEmail())
                           .set(ACCOUNT.PASSWORD, passwordEncoder.encode(payload.account().password()))
                           .set(ACCOUNT.ACCOUNT_TYPE, VENDOR)
                           .set(ACCOUNT.TERMS_ACCEPTED_AT, OffsetDateTime.now())
                           .returningResult(ACCOUNT.ID)
                           .fetchOne(ACCOUNT.ID);
        if (accountId == null) {
            throw new IllegalStateException("Creating the vendor account returned no id");
        }
        return accountId;
    }

    private void insertCompanyInformation(DSLContext tx, long accountId,
                                          VendorSubmissionPayload.Company company) {
        tx.insertInto(COMPANY_INFORMATION)
          .set(COMPANY_INFORMATION.ACCOUNT_ID, accountId)
          .set(COMPANY_INFORMATION.OFFICIAL_NAME, company.officialName())
          .set(COMPANY_INFORMATION.WEBSITE_URL, company.websiteUrl())
          .set(COMPANY_INFORMATION.DESCRIPTION, company.description())
          .execute();
    }

    /**
     * {@code approved} is left NULL on purpose - that is what parks the row in the moderation queue, which
     * {@link ToolSubmissionService#getQueue()} selects on.
     */
    private long insertSubmission(DSLContext tx, long accountId, VendorSubmissionPayload payload) {
        VendorSubmissionPayload.Product product = payload.product();
        Long submissionId = tx.insertInto(TOOL_SUBMISSION)
                .set(TOOL_SUBMISSION.ACCOUNT_ID, accountId)
                .set(TOOL_SUBMISSION.NAME, product.name())
                // The product has no URL of its own in the form; the company's site is what a reviewer follows.
                .set(TOOL_SUBMISSION.URL, payload.company().websiteUrl())
                .set(TOOL_SUBMISSION.PRICING, pricingOf(product))
                .set(TOOL_SUBMISSION.SHORT_DESCRIPTION, product.shortDescription())
                .set(TOOL_SUBMISSION.LONG_DESCRIPTION, product.longDescription())
                .set(TOOL_SUBMISSION.TAGS, toJsonb(product.tags()))
                .set(TOOL_SUBMISSION.CATEGORIES, toJsonb(product.categories()))
                .set(TOOL_SUBMISSION.FEATURES, toJsonb(product.features()))
                .set(TOOL_SUBMISSION.PLANS, toJsonb(product.plans()))
                .set(TOOL_SUBMISSION.FREE_TIER_PLAN_ID, product.freeTierPlanId())
                .set(TOOL_SUBMISSION.HIGHLIGHT_PLAN_ID, product.highlightPlanId())
                .set(TOOL_SUBMISSION.INTEGRATIONS, toJsonb(product.integrations()))
                .set(TOOL_SUBMISSION.DEMO_VIDEO_URL, product.demoVideoUrl())
                .set(TOOL_SUBMISSION.SUBMITTED_BY, payload.company().workEmail())
                .returningResult(TOOL_SUBMISSION.ID)
                .fetchOne(TOOL_SUBMISSION.ID);
        if (submissionId == null) {
            throw new IllegalStateException("Creating the tool submission returned no id");
        }
        return submissionId;
    }

    private void storeImages(DSLContext tx, long submissionId, MultipartFile logo,
                             List<MultipartFile> screenshots, List<String> storedPaths) {
        String directory = SUBMISSION_DIR + submissionId;

        if (logo != null && !logo.isEmpty()) {
            String path = fileStorageService.storeImage(logo, directory, LOGO);
            storedPaths.add(path);
            insertImageMetadata(tx, submissionId, path, LOGO, 0);
        }

        if (screenshots == null) {
            return;
        }
        int order = 1;
        for (MultipartFile screenshot : screenshots) {
            if (screenshot == null || screenshot.isEmpty()) {
                continue;
            }
            String path = fileStorageService.storeImage(screenshot, directory, SCREENSHOT + "-" + order);
            storedPaths.add(path);
            insertImageMetadata(tx, submissionId, path, SCREENSHOT, order);
            order++;
        }
    }

    private void insertImageMetadata(DSLContext tx, long submissionId, String path, String kind, int order) {
        tx.insertInto(TOOL_SUBMISSION_IMAGE_METADATA)
          .set(TOOL_SUBMISSION_IMAGE_METADATA.TOOL_SUBMISSION_ID, submissionId)
          .set(TOOL_SUBMISSION_IMAGE_METADATA.IMAGE_PATH, path)
          .set(TOOL_SUBMISSION_IMAGE_METADATA.KIND, kind)
          .set(TOOL_SUBMISSION_IMAGE_METADATA.DISPLAY_ORDER, order)
          .execute();
    }

    /**
     * Outside the transaction above, and it has to be: {@link SubscriptionService} opens its own on the
     * shared DSLContext, so it runs on another connection and would not see an account that is not
     * committed yet - the subscription's foreign key would fail.
     */
    private void openSubscription(long accountId, VendorSubmissionPayload payload) {
        String planName = payload.planKey().wire();
        var price = dslContext.select(PLAN.ID, PLAN_PRICE.AMOUNT_CENTS)
                              .from(PLAN)
                              .join(PLAN_PRICE).on(PLAN_PRICE.PLAN_ID.eq(PLAN.ID))
                              .where(PLAN.ACCOUNT_TYPE.eq(VENDOR))
                              .and(PLAN.NAME.eq(planName))
                              .and(PLAN_PRICE.RECURRENCE.eq(MONTHLY))
                              .fetchOne();
        if (price == null) {
            throw new IllegalStateException("No monthly price for the vendor plan '" + planName + "'");
        }

        // Nothing has been charged: there is no payment provider yet, so a paid plan's first period opens
        // unpaid and stays that way until the dunning work in TASKS.adoc lands.
        subscriptionService.create(accountId, price.value1(), MONTHLY, price.value2(), CURRENCY);
        LOGGER.info("Opened the first {} period for account {} on the vendor '{}' plan, unpaid",
                    MONTHLY, accountId, planName);
    }

    /**
     * The {@code pricing} reference the directory filters on, read off the plans the vendor described:
     * nothing paid is free, a free tier next to paid ones is freemium, anything else is paid.
     */
    private static String pricingOf(VendorSubmissionPayload.Product product) {
        List<PricingPlan> plans = product.plans() == null ? List.of() : product.plans();
        boolean anyPaid = plans.stream().anyMatch(plan -> !isFree(product, plan));
        if (!anyPaid) {
            return "free";
        }
        boolean anyFree = plans.stream().anyMatch(plan -> isFree(product, plan));
        return anyFree ? "freemium" : "paid";
    }

    /**
     * The form marks one plan as the free tier and forces its price to "0", but the price is free text
     * otherwise - "€0", "0.00" and an empty field all mean the same thing and none of them is a number.
     */
    private static boolean isFree(VendorSubmissionPayload.Product product, PricingPlan plan) {
        if (plan.id() != null && plan.id().equals(product.freeTierPlanId())) {
            return true;
        }
        String price = plan.price() == null ? "" : plan.price().trim();
        if (price.isEmpty() || "free".equalsIgnoreCase(price)) {
            return true;
        }
        String digits = price.replaceAll("[^0-9]", "");
        return !digits.isEmpty() && digits.chars().allMatch(digit -> digit == '0');
    }

    private JSONB toJsonb(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            // Everything passed here came out of Jackson moments ago, so this is a bug, not bad input.
            throw new IllegalStateException("Could not serialise the submission's " + value.getClass()
                                            + " back to JSON", e);
        }
    }
}
