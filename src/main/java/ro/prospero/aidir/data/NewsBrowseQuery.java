package ro.prospero.aidir.data;

import java.util.List;

/**
 * A news list request with every parameter defaulted and clamped. {@code exclude} holds slugs to leave out,
 * which is how the list skips the articles the page already shows in its hero.
 */
public record NewsBrowseQuery(String query,
                              String topic,
                              NewsSort sort,
                              List<String> exclude,
                              int page,
                              int size) {

    public static final int DEFAULT_SIZE = 15;
    public static final int MAX_SIZE = 50;

    public static NewsBrowseQuery of(String query,
                                     String topic,
                                     NewsSort sort,
                                     List<String> exclude,
                                     Integer page,
                                     Integer size) {
        return new NewsBrowseQuery(blankToNull(query),
                                   blankToNull(topic),
                                   sort == null ? NewsSort.LATEST : sort,
                                   exclude == null ? List.of()
                                           : exclude.stream().filter(s -> s != null && !s.isBlank()).distinct().toList(),
                                   Math.max(0, page == null ? 0 : page),
                                   Math.clamp(size == null ? DEFAULT_SIZE : size, 1, MAX_SIZE));
    }

    public int offset() {
        return page * size;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
