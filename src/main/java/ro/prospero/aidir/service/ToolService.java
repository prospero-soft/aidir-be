package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.config.UploadsConfig;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL;
import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL_IMAGE_METADATA;

/**
 * Reads the published catalogue, which is what the CMS used to be. The details page and the search index
 * both come through here on purpose: two sources for the same listing is how they end up disagreeing about
 * what is listed.
 */
@Service
public class ToolService {
    private static final String LOGO = "logo";

    private final DSLContext dslContext;
    private final ModelMapper beanMapper;
    private final UploadsConfig uploadsConfig;

    public ToolService(DSLContext dslContext,
                       @Qualifier("beanMapper") ModelMapper beanMapper,
                       UploadsConfig uploadsConfig) {
        this.dslContext = dslContext;
        this.beanMapper = beanMapper;
        this.uploadsConfig = uploadsConfig;
    }

    public Optional<ToolDTO> find(long id) {
        return dslContext.selectFrom(TOOL)
                         .where(TOOL.ID.eq(id))
                         .and(TOOL.PUBLISHED.isTrue())
                         .fetchOptional()
                         .map(this::toDto);
    }

    public List<ToolDTO> getAll() {
        return dslContext.selectFrom(TOOL)
                         .where(TOOL.PUBLISHED.isTrue())
                         .orderBy(TOOL.APPROVED_AT.desc())
                         .fetch()
                         .stream()
                         .map(this::toDto)
                         .toList();
    }

    /**
     * Logo URLs for a whole page of tools in one query - a directory page is a card per row, and a lookup
     * per card is the request storm this exists to avoid.
     *
     * <p>The stored path is relative to the uploads root, so it is returned with the public base path in
     * front of it: what comes back addresses the file as-is, and no caller has to know where media is
     * mounted. Tools with no logo are simply absent from the map.
     */
    public Map<Long, String> findLogoUrls(Collection<Long> toolIds) {
        if (toolIds.isEmpty()) {
            return Map.of();
        }

        return dslContext.select(TOOL_IMAGE_METADATA.TOOL_ID, TOOL_IMAGE_METADATA.IMAGE_PATH)
                         .from(TOOL_IMAGE_METADATA)
                         .where(TOOL_IMAGE_METADATA.TOOL_ID.in(toolIds))
                         .and(TOOL_IMAGE_METADATA.KIND.eq(LOGO))
                         .orderBy(TOOL_IMAGE_METADATA.DISPLAY_ORDER.asc())
                         .fetch()
                         .stream()
                         // A tool should only ever have one logo; if a second slipped in, the first wins
                         // rather than the query blowing up on a duplicate key.
                         .collect(Collectors.toMap(r -> r.get(TOOL_IMAGE_METADATA.TOOL_ID),
                                                   r -> publicUrl(r.get(TOOL_IMAGE_METADATA.IMAGE_PATH)),
                                                   (first, second) -> first));
    }

    private String publicUrl(String relativePath) {
        String base = uploadsConfig.getPublicBasePath();
        String path = relativePath.replace('\\', '/');
        if (base.endsWith("/") || path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }

    private ToolDTO toDto(ToolRecord record) {
        return beanMapper.map(record, ToolDTO.class);
    }
}
