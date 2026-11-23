package ro.prospero.aidir.service;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolConditionsTest {

    @Test
    void asksForTheTrialAndThePaymentTypeOnTheSamePlan() {
        String sql = DSL.using(SQLDialect.POSTGRES)
                        .renderInlined(ToolConditions.hasTrialOn(List.of("subscription")));

        assertThat(sql).contains("@>")
                       .contains("\"paymentType\":\"subscription\"")
                       .contains("\"freeTrial\":true");
    }
}
