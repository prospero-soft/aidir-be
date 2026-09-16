package ro.prospero.aidir.config;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import ro.prospero.aidir.data.ToolSubmissionDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolSubmissionRecord;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MapperConfigTest {

    private final ModelMapper beanMapper = new MapperConfig().beanMapper();

    /**
     * {@code tool_submission} has both an {@code approved} and an {@code approved_at}, and only the
     * first of those is the verdict. A strategy loose enough to read the date into the flag is what
     * broke the catalogue mapping, so this pins which column the queue answers from.
     */
    @Test
    void readsTheVerdictFromApprovedAndNotFromApprovedAt() {
        ToolSubmissionRecord record = new ToolSubmissionRecord();
        record.setId(3L);
        record.setApproved(null);
        record.setApprovedAt(OffsetDateTime.parse("2026-09-16T15:21:05.062342+03:00"));

        ToolSubmissionDTO dto = beanMapper.map(record, ToolSubmissionDTO.class);

        assertThat(dto.getApproved()).isNull();
    }

    @Test
    void readsAnAbsentArrayColumnAsAnEmptyList() {
        ToolSubmissionRecord record = new ToolSubmissionRecord();
        record.setId(5L);

        ToolSubmissionDTO dto = beanMapper.map(record, ToolSubmissionDTO.class);

        assertThat(dto.getTags()).isEmpty();
        assertThat(dto.getCategories()).isEmpty();
    }

    @Test
    void parsesTheJsonbArrayColumns() {
        ToolSubmissionRecord record = new ToolSubmissionRecord();
        record.setId(4L);
        record.setName("Widget");
        record.setTags(JSONB.valueOf("[\"alpha\",\"beta\"]"));
        record.setCategories(JSONB.valueOf("[\"writing\"]"));

        ToolSubmissionDTO dto = beanMapper.map(record, ToolSubmissionDTO.class);

        assertThat(dto.getName()).isEqualTo("Widget");
        assertThat(dto.getTags()).containsExactly("alpha", "beta");
        assertThat(dto.getCategories()).containsExactly("writing");
    }
}
