package ro.prospero.aidir.service;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import ro.prospero.aidir.data.JsonbArrays;

import java.util.List;

import static ro.prospero.aidir.jooq.generated.public_.Tables.TOOL;

/**
 * Predicates over {@code tool} that more than one query needs. The sidebar filter and the alternatives
 * strip both ask "which tools are in these categories", and two spellings of that question are how they
 * end up disagreeing.
 */
final class ToolConditions {

    private ToolConditions() {
    }

    /**
     * Selected categories are OR-ed: picking Design and Marketing asks for tools in either, which is what
     * ticking two boxes in a sidebar is universally taken to mean.
     */
    static Condition inCategories(List<String> categories) {
        if (categories.isEmpty()) {
            return DSL.noCondition();
        }

        List<Condition> each = categories.stream()
                                         .map(c -> DSL.condition("{0} @> {1}",
                                                                 TOOL.CATEGORIES,
                                                                 DSL.val(JsonbArrays.jsonArrayOf(c))))
                                         .toList();
        return DSL.or(each);
    }
}
