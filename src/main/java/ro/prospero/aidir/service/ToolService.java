package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolRecord;

import java.util.List;
import java.util.Optional;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL;

/**
 * Reads the published catalogue, which is what the CMS used to be. The details page and the search index
 * both come through here on purpose: two sources for the same listing is how they end up disagreeing about
 * what is listed.
 */
@Service
public class ToolService {
    private final DSLContext dslContext;
    private final ModelMapper beanMapper;

    public ToolService(DSLContext dslContext, @Qualifier("beanMapper") ModelMapper beanMapper) {
        this.dslContext = dslContext;
        this.beanMapper = beanMapper;
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

    private ToolDTO toDto(ToolRecord record) {
        return beanMapper.map(record, ToolDTO.class);
    }
}
