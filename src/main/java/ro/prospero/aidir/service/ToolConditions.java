package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import ro.prospero.aidir.data.JsonbArrays;

import java.util.List;
import java.util.Map;

import static ro.prospero.aidir.jooq.generated.Tables.TOOL;

/**
 * Predicates over {@code tool} that more than one query needs. The sidebar filter and the alternatives
 * strip both ask "which tools are in these categories", and two spellings of that question are how they
 * end up disagreeing.
 */
final class ToolConditions {

    private ToolConditions() {
    }

    /**
     * Selected values are OR-ed: picking Design and Marketing asks for tools in either, which is what
     * ticking two boxes in a sidebar is universally taken to mean.
     */
    static Condition containsAny(Field<JSONB> column, List<String> values) {
        if (values.isEmpty()) {
            return DSL.noCondition();
        }

        List<Condition> each = values.stream()
                                     .map(v -> DSL.condition("{0} @> {1}",
                                                             column,
                                                             DSL.val(JsonbArrays.jsonArrayOf(v))))
                                     .toList();
        return DSL.or(each);
    }

    static Condition hasTrial() {
        return DSL.condition("{0} @> {1}", TOOL.PLANS, DSL.val(JsonbArrays.jsonArrayOf(Map.of("freeTrial", true))));
    }

    /**
     * Whether one plan of any of these payment types offers a trial. Containment matches both properties on
     * the same array element, so a trial on a tool's Contact Sales plan does not count for its subscription.
     */
    static Condition hasTrialOn(List<String> paymentTypes) {
        List<Condition> each = paymentTypes.stream()
                                           .map(type -> DSL.condition(
                                                   "{0} @> {1}",
                                                   TOOL.PLANS,
                                                   DSL.val(JsonbArrays.jsonArrayOf(Map.of("paymentType", type,
                                                                                          "freeTrial", true)))))
                                           .toList();
        return DSL.or(each);
    }
}
