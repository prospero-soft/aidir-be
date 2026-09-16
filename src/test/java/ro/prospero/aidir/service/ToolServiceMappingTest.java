package ro.prospero.aidir.service;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolRecord;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolServiceMappingTest {

    @Test
    void mapsAListedToolIncludingTheDateItWasApproved() {
        OffsetDateTime submitted = OffsetDateTime.parse("2026-09-01T10:00:00+03:00");
        OffsetDateTime approved = OffsetDateTime.parse("2026-09-16T15:21:05.062342+03:00");

        ToolRecord record = new ToolRecord();
        record.setId(7L);
        record.setName("Widget");
        record.setUrl("https://example.com");
        record.setPricing("freemium");
        record.setShortDescription("short");
        record.setLongDescription("long");
        record.setTags(JSONB.valueOf("[\"alpha\",\"beta\"]"));
        record.setCategories(JSONB.valueOf("[\"writing\"]"));
        record.setSubmittedAt(submitted);
        record.setApprovedAt(approved);

        ToolDTO dto = ToolService.toDto(record);

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.tags()).containsExactly("alpha", "beta");
        assertThat(dto.categories()).containsExactly("writing");
        assertThat(dto.submittedAt()).isEqualTo(submitted);
        assertThat(dto.approvedAt()).isEqualTo(approved);
    }

    @Test
    void readsAToolWithNoTagsOrCategoriesAsEmptyLists() {
        ToolRecord record = new ToolRecord();
        record.setId(8L);

        ToolDTO dto = ToolService.toDto(record);

        assertThat(dto.tags()).isEqualTo(List.of());
        assertThat(dto.categories()).isEqualTo(List.of());
        assertThat(dto.approvedAt()).isNull();
    }
}
