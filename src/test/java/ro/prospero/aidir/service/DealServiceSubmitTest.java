package ro.prospero.aidir.service;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ro.prospero.aidir.jooq.generated.tables.records.DealRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static ro.prospero.aidir.jooq.generated.Tables.DEAL;

class DealServiceSubmitTest {

    @Test
    void aVendorWithoutAPublishedToolCannotSubmit() {
        DSLContext create = DSL.using(SQLDialect.POSTGRES);
        MockDataProvider database = context -> {
            if (context.sql().toLowerCase().contains("for update")) {
                DealRecord draft = create.newRecord(DEAL);
                draft.setId(7L);
                draft.setAccountId(1L);
                draft.setStatus("draft");
                Result<DealRecord> deals = create.newResult(DEAL);
                deals.add(draft);
                return new MockResult[]{new MockResult(1, deals)};
            }
            Field<Boolean> exists = DSL.field("exists", Boolean.class);
            Record1<Boolean> none = create.newRecord(exists).values(false);
            Result<Record1<Boolean>> answer = create.newResult(exists);
            answer.add(none);
            return new MockResult[]{new MockResult(1, answer)};
        };
        DealRows dealRows = mock(DealRows.class);
        DealService service = new DealService(DSL.using(new MockConnection(database), SQLDialect.POSTGRES), dealRows,
                                              mock(FileStorageService.class));

        ResponseStatusException refused = catchThrowableOfType(ResponseStatusException.class,
                                                                () -> service.submit(1L, 7L));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getReason()).contains("approved and listed");
        verifyNoInteractions(dealRows);
    }
}
