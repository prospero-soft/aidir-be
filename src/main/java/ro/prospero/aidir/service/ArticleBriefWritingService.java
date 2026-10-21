package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.data.ArticleBlock;
import ro.prospero.aidir.data.ArticleBriefReviewDTO;
import ro.prospero.aidir.data.ArticleBriefStatus;
import ro.prospero.aidir.data.BriefDraftPayload;
import ro.prospero.aidir.data.EditorDTO;
import ro.prospero.aidir.exception.ApiPayloadValidationException;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefDraftRecord;
import ro.prospero.aidir.jooq.generated.tables.records.ArticleBriefRecord;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ro.prospero.aidir.jooq.generated.Tables.ACCOUNT;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_DRAFT;
import static ro.prospero.aidir.jooq.generated.Tables.ARTICLE_BRIEF_DRAFT_IMAGE;

/**
 * The writing desk: the briefs triage accepted, the editor each is assigned to, and the rounds of drafting
 * that editor sends the client. Any admin account is an editor. Only the assigned editor writes a brief;
 * another admin takes it over by assigning it to themselves.
 *
 * <p>A draft is saved as often as the editor likes and held to the article rules only when sent. Once sent it
 * is never changed: the client's revisions are written as the next round, which starts from it.
 */
@Service
public class ArticleBriefWritingService {
    /** MAX_HEADLINE in src/components/article-submission/constants.ts. */
    static final int MAX_HEADLINE = 120;

    private static final String ADMIN = "admin";

    /** From acceptance until the client takes the article. */
    private static final Set<ArticleBriefStatus> ON_THE_DESK = EnumSet.of(ArticleBriefStatus.ACCEPTED,
                                                                          ArticleBriefStatus.WRITING,
                                                                          ArticleBriefStatus.CLIENT_REVIEW,
                                                                          ArticleBriefStatus.REVISIONS_REQUESTED);

    private final DSLContext dslContext;
    private final ArticleBriefRows rows;
    private final VendorContacts vendorContacts;
    private final FileStorageService fileStorageService;

    public ArticleBriefWritingService(DSLContext dslContext, ArticleBriefRows rows, VendorContacts vendorContacts,
                                      FileStorageService fileStorageService) {
        this.dslContext = dslContext;
        this.rows = rows;
        this.vendorContacts = vendorContacts;
        this.fileStorageService = fileStorageService;
    }

    /** Every brief on the desk, the longest accepted first. */
    public List<ArticleBriefReviewDTO> desk(long callerId) {
        return review(rows.read(dslContext,
                                ARTICLE_BRIEF.STATUS.in(ON_THE_DESK.stream().map(ArticleBriefStatus::wire).toList()),
                                ArticleBriefRows.DraftScope.ALL,
                                ARTICLE_BRIEF.TRIAGED_AT.asc(), ARTICLE_BRIEF.ID.asc()),
                      callerId);
    }

    /** One brief, whatever its status, with every round of its drafting. */
    public ArticleBriefReviewDTO get(long callerId, long briefId) {
        return rows.readOne(dslContext, ARTICLE_BRIEF.ID.eq(briefId), ArticleBriefRows.DraftScope.ALL)
                   .map(stored -> review(List.of(stored), callerId).getFirst())
                   .orElseThrow(() -> notFound(briefId));
    }

    public List<EditorDTO> editors() {
        return dslContext.select(ACCOUNT.ID, ACCOUNT.DISPLAY_NAME, ACCOUNT.EMAIL)
                         .from(ACCOUNT)
                         .where(ACCOUNT.ACCOUNT_TYPE.eq(ADMIN))
                         .orderBy(ACCOUNT.DISPLAY_NAME, ACCOUNT.ID)
                         .fetch(r -> new EditorDTO(r.value1(), r.value2(), r.value3()));
    }

    /**
     * Gives the brief to an editor, which starts the writing of one just accepted. Assigning it again hands it
     * over, drafts and all.
     */
    public void assign(long briefId, long editorId) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            if (!tx.fetchExists(ACCOUNT, ACCOUNT.ID.eq(editorId).and(ACCOUNT.ACCOUNT_TYPE.eq(ADMIN)))) {
                throw new ApiPayloadValidationException("Validating the assignment failed.",
                                                        List.of("editorId: No admin account with id " + editorId));
            }
            ArticleBriefRecord brief = lock(tx, briefId);
            ArticleBriefStatus status = ArticleBriefStatus.fromWire(brief.getStatus());
            if (!ON_THE_DESK.contains(status)) {
                throw conflict("Article brief " + briefId + " is " + status.wire() + ", not on the writing desk");
            }

            tx.update(ARTICLE_BRIEF)
              .set(ARTICLE_BRIEF.EDITOR_ID, editorId)
              .set(ARTICLE_BRIEF.ASSIGNED_AT, OffsetDateTime.now())
              .set(ARTICLE_BRIEF.STATUS,
                   (status == ArticleBriefStatus.ACCEPTED ? ArticleBriefStatus.WRITING : status).wire())
              .where(ARTICLE_BRIEF.ID.eq(briefId))
              .execute();
        });
    }

    /**
     * Saves the round being written, or starts the next one when the client sent the last back. Images and a
     * cover the save no longer keeps are deleted once it is committed, unless an earlier round still shows them.
     *
     * @param featuredImage the new cover; null keeps the one the draft had
     * @param newImages     the in-article images {@code keptImages} marks as new, in body order
     */
    public ArticleBriefReviewDTO saveDraft(long callerId, long briefId, BriefDraftPayload payload,
                                           MultipartFile featuredImage, List<MultipartFile> newImages) {
        List<Integer> keptImages = payload.keptImages();
        List<String> violations = new ArrayList<>(ArticleOrderChecks.keptImageViolations(keptImages,
                                                                                         newImages.size()));
        violations.addAll(ArticleBodyRules.checkImageNumbering(payload.body(), keptImages.size()));
        if (keptImages.size() > SubmittedArticleService.MAX_IMAGES) {
            violations.add("additionalImages: Add at most " + SubmittedArticleService.MAX_IMAGES
                           + " images to the article");
        }
        if (featuredImage != null) {
            ArticleOrderChecks.checkImage("featuredImage", featuredImage, violations);
        }
        for (int i = 0; i < newImages.size(); i++) {
            ArticleOrderChecks.checkImage("additionalImages[" + i + "]", newImages.get(i), violations);
        }
        ArticleOrderChecks.throwIfAny("draft", violations);

        StagedImages staged = new StagedImages(fileStorageService, "briefs");
        List<String> superseded;
        try {
            String newCover = featuredImage == null ? null : staged.store(featuredImage, "featured");
            List<String> newPaths = new ArrayList<>();
            for (int i = 0; i < newImages.size(); i++) {
                newPaths.add(staged.store(newImages.get(i), "image-" + i));
            }

            superseded = dslContext.transactionResult(cfg -> {
                DSLContext tx = DSL.using(cfg);
                ArticleBriefRecord brief = lock(tx, briefId);
                requireEditor(brief, callerId);
                ArticleBriefStatus status = ArticleBriefStatus.fromWire(brief.getStatus());
                if (status != ArticleBriefStatus.WRITING && status != ArticleBriefStatus.REVISIONS_REQUESTED) {
                    throw conflict(status == ArticleBriefStatus.CLIENT_REVIEW
                                           ? "The draft is with the client; wait for their answer to write more."
                                           : "Article brief " + briefId + " is " + status.wire()
                                             + ", not being written");
                }
                if (newCover != null && brief.getFeaturedImagePath() != null) {
                    ArticleOrderChecks.throwIfAny("draft", List.of(
                            "featuredImage: The client supplied the cover, so the draft keeps it"));
                }

                ArticleBriefDraftRecord last = latestDraft(tx, briefId);
                boolean open = last != null && last.getSentAt() == null;
                List<String> basePaths = last == null ? List.of() : imagePaths(tx, last.getId());
                List<String> paths = keptImagePaths(keptImages, basePaths, newPaths);
                String previousCover = last == null ? null : last.getFeaturedImagePath();

                ArticleBriefDraftRecord draft;
                if (open) {
                    draft = last;
                    tx.deleteFrom(ARTICLE_BRIEF_DRAFT_IMAGE)
                      .where(ARTICLE_BRIEF_DRAFT_IMAGE.ARTICLE_BRIEF_DRAFT_ID.eq(draft.getId()))
                      .execute();
                } else {
                    draft = tx.newRecord(ARTICLE_BRIEF_DRAFT);
                    draft.setArticleBriefId(briefId);
                    draft.setRound(last == null ? 1 : last.getRound() + 1);
                }
                draft.setHeadline(payload.headline());
                draft.setBody(ArticleJson.writeBody(payload.body()));
                draft.setSources(ArticleJson.write(payload.sources()));
                draft.setFeaturedImagePath(newCover != null ? newCover : previousCover);
                draft.setWrittenBy(callerId);
                draft.setUpdatedAt(OffsetDateTime.now());
                draft.store();
                for (int i = 0; i < paths.size(); i++) {
                    tx.insertInto(ARTICLE_BRIEF_DRAFT_IMAGE)
                      .set(ARTICLE_BRIEF_DRAFT_IMAGE.ARTICLE_BRIEF_DRAFT_ID, draft.getId())
                      .set(ARTICLE_BRIEF_DRAFT_IMAGE.POSITION, i)
                      .set(ARTICLE_BRIEF_DRAFT_IMAGE.PATH, paths.get(i))
                      .execute();
                }
                if (status == ArticleBriefStatus.REVISIONS_REQUESTED) {
                    tx.update(ARTICLE_BRIEF)
                      .set(ARTICLE_BRIEF.STATUS, ArticleBriefStatus.WRITING.wire())
                      .where(ARTICLE_BRIEF.ID.eq(briefId))
                      .execute();
                }

                // A new round's predecessor still shows what it started from, so only an open round drops files.
                List<String> dropped = new ArrayList<>();
                if (open) {
                    dropped.addAll(basePaths);
                    dropped.removeAll(paths);
                    if (newCover != null && previousCover != null) {
                        dropped.add(previousCover);
                    }
                }
                return dropped.stream().filter(path -> unreferenced(tx, path)).toList();
            });
        } catch (RuntimeException exception) {
            staged.discard();
            throw exception;
        }
        superseded.forEach(fileStorageService::delete);
        return get(callerId, briefId);
    }

    /** Sends the round being written to the client, once it passes what a published article is held to. */
    public void send(long callerId, long briefId) {
        dslContext.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            ArticleBriefRecord brief = lock(tx, briefId);
            requireEditor(brief, callerId);
            ArticleBriefStatus status = ArticleBriefStatus.fromWire(brief.getStatus());
            ArticleBriefDraftRecord draft = latestDraft(tx, briefId);
            if (status != ArticleBriefStatus.WRITING || draft == null || draft.getSentAt() != null) {
                throw conflict(status == ArticleBriefStatus.CLIENT_REVIEW
                                       ? "The draft is already with the client."
                                       : "There is no unsent draft of this brief to send; save one first.");
            }

            ArticleOrderChecks.throwIfAny("draft", sendViolations(brief, draft, imagePaths(tx, draft.getId()).size()));

            OffsetDateTime now = OffsetDateTime.now();
            tx.update(ARTICLE_BRIEF_DRAFT)
              .set(ARTICLE_BRIEF_DRAFT.SENT_AT, now)
              .where(ARTICLE_BRIEF_DRAFT.ID.eq(draft.getId()))
              .execute();
            tx.update(ARTICLE_BRIEF)
              .set(ARTICLE_BRIEF.STATUS, ArticleBriefStatus.CLIENT_REVIEW.wire())
              .where(ARTICLE_BRIEF.ID.eq(briefId))
              .execute();
        });
    }

    /**
     * Writes and sends a round for the caller through the calls the writing page makes: assigning the brief to
     * them if someone else holds it or nobody does, saving the draft, then sending it. A brief with a cover of
     * its own ignores the {@code featuredImage} given. Meant for development, to put drafts in front of vendors
     * without writing them by hand.
     */
    public void devSendDraft(long callerId, long briefId, BriefDraftPayload payload, MultipartFile featuredImage,
                             List<MultipartFile> newImages) {
        ArticleBriefRecord brief = dslContext.selectFrom(ARTICLE_BRIEF)
                                             .where(ARTICLE_BRIEF.ID.eq(briefId))
                                             .fetchOptional()
                                             .orElseThrow(() -> notFound(briefId));
        if (brief.getEditorId() == null || brief.getEditorId() != callerId) {
            assign(briefId, callerId);
        }
        saveDraft(callerId, briefId, payload, brief.getFeaturedImagePath() == null ? featuredImage : null, newImages);
        send(callerId, briefId);
    }

    /**
     * The article rules, and the brief's order: no more follow links than the client paid for, and a cover. The
     * writing tier's word range is the editor's target, not a rule; only the article minimum is enforced.
     */
    private static List<String> sendViolations(ArticleBriefRecord brief, ArticleBriefDraftRecord draft, int images) {
        List<ArticleBlock> body = ArticleJson.body(draft.getBody());
        List<String> violations = new ArrayList<>(ArticleBodyRules.checkBody(body, images));
        if (draft.getHeadline().length() > MAX_HEADLINE) {
            violations.add("headline: Keep it to " + MAX_HEADLINE + " characters or fewer");
        }
        int links = ArticleBodyRules.bodyLinks(body).size();
        if (links > brief.getLinks()) {
            violations.add("body: The client paid for " + brief.getLinks() + " follow link"
                           + (brief.getLinks() == 1 ? "" : "s") + ", so the article can carry no more");
        }
        if (draft.getFeaturedImagePath() == null && brief.getFeaturedImagePath() == null) {
            violations.add("featuredImage: Add the image to use as the article cover");
        }
        return violations;
    }

    /** The images {@code keptImages} lays out, in body order: kept ones from the base round, then each new one. */
    static List<String> keptImagePaths(List<Integer> keptImages, List<String> basePaths, List<String> newPaths) {
        if (keptImages.stream().anyMatch(position -> position != null && position >= basePaths.size())) {
            ArticleOrderChecks.throwIfAny("draft", List.of("keptImages: The draft has " + basePaths.size()
                                                           + " stored image" + (basePaths.size() == 1 ? "" : "s")));
        }
        List<String> paths = new ArrayList<>();
        Iterator<String> fresh = newPaths.iterator();
        for (Integer position : keptImages) {
            paths.add(position == null ? fresh.next() : basePaths.get(position));
        }
        return paths;
    }

    private List<ArticleBriefReviewDTO> review(List<ArticleBriefRows.Stored> briefs, long callerId) {
        if (briefs.isEmpty()) {
            return List.of();
        }
        Map<Long, VendorContacts.Contact> vendors =
                vendorContacts.of(briefs.stream().map(ArticleBriefRows.Stored::accountId).toList());
        List<Long> editorIds = briefs.stream().map(ArticleBriefRows.Stored::editorId)
                                     .filter(id -> id != null).distinct().toList();
        Map<Long, String> editorNames = editorIds.isEmpty() ? Map.of()
                : dslContext.select(ACCOUNT.ID, ACCOUNT.DISPLAY_NAME)
                            .from(ACCOUNT)
                            .where(ACCOUNT.ID.in(editorIds))
                            .fetchMap(ACCOUNT.ID, ACCOUNT.DISPLAY_NAME);

        return briefs.stream().map(stored -> {
            VendorContacts.Contact vendor = VendorContacts.orUnknown(vendors, stored.accountId());
            Long editorId = stored.editorId();
            ArticleBriefReviewDTO.Editor editor = editorId == null ? null
                    : new ArticleBriefReviewDTO.Editor(editorId, editorNames.get(editorId));
            return new ArticleBriefReviewDTO(stored.brief(), stored.accountId(), vendor.name(), vendor.email(),
                                             editor, editorId != null && editorId == callerId);
        }).toList();
    }

    private static ArticleBriefDraftRecord latestDraft(DSLContext tx, long briefId) {
        return tx.selectFrom(ARTICLE_BRIEF_DRAFT)
                 .where(ARTICLE_BRIEF_DRAFT.ARTICLE_BRIEF_ID.eq(briefId))
                 .orderBy(ARTICLE_BRIEF_DRAFT.ROUND.desc())
                 .limit(1)
                 .fetchOne();
    }

    private static List<String> imagePaths(DSLContext tx, long draftId) {
        return tx.select(ARTICLE_BRIEF_DRAFT_IMAGE.PATH)
                 .from(ARTICLE_BRIEF_DRAFT_IMAGE)
                 .where(ARTICLE_BRIEF_DRAFT_IMAGE.ARTICLE_BRIEF_DRAFT_ID.eq(draftId))
                 .orderBy(ARTICLE_BRIEF_DRAFT_IMAGE.POSITION)
                 .fetch(ARTICLE_BRIEF_DRAFT_IMAGE.PATH);
    }

    /** Rounds share files, so one is only deleted once no round shows it as an image or a cover. */
    private static boolean unreferenced(DSLContext tx, String path) {
        return !tx.fetchExists(ARTICLE_BRIEF_DRAFT_IMAGE, ARTICLE_BRIEF_DRAFT_IMAGE.PATH.eq(path))
               && !tx.fetchExists(ARTICLE_BRIEF_DRAFT, ARTICLE_BRIEF_DRAFT.FEATURED_IMAGE_PATH.eq(path));
    }

    /** Locked so two saves of the same brief - two tabs, a save and a send - apply one after the other. */
    private static ArticleBriefRecord lock(DSLContext tx, long briefId) {
        ArticleBriefRecord brief = tx.selectFrom(ARTICLE_BRIEF)
                                     .where(ARTICLE_BRIEF.ID.eq(briefId))
                                     .forUpdate()
                                     .fetchOne();
        if (brief == null) {
            throw notFound(briefId);
        }
        return brief;
    }

    private static void requireEditor(ArticleBriefRecord brief, long callerId) {
        if (brief.getEditorId() == null || brief.getEditorId() != callerId) {
            throw conflict(brief.getEditorId() == null
                                   ? "Assign this brief to yourself to write it."
                                   : "This brief is assigned to another editor; take it over to write it.");
        }
    }

    private static ResponseStatusException notFound(long briefId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No article brief with id " + briefId);
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
