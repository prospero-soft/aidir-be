package ro.prospero.aidir.data;

import java.util.List;

/**
 * A talent browse request with every parameter already defaulted and clamped, so the service below never has
 * to decide what a missing page size or a negative page number means.
 *
 * <p>The page sizes differ from {@link ToolBrowseQuery}: the talent directory offers 12/24/48 rather than
 * 10/20/50, so inheriting the tool defaults would silently reject the largest option the page can ask for.
 */
public record TalentBrowseQuery(String query,
                                String jobTitle,
                                List<String> keywords,
                                List<String> locations,
                                TalentLocationScope locationScope,
                                String workplace,
                                String employmentType,
                                List<String> skills,
                                List<String> languages,
                                TalentSort sort,
                                int page,
                                int size) {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 48;

    public static TalentBrowseQuery of(String query,
                                       String jobTitle,
                                       List<String> keywords,
                                       List<String> locations,
                                       TalentLocationScope locationScope,
                                       String workplace,
                                       String employmentType,
                                       List<String> skills,
                                       List<String> languages,
                                       TalentSort sort,
                                       Integer page,
                                       Integer size) {
        return new TalentBrowseQuery(blankToNull(query),
                                     blankToNull(jobTitle),
                                     clean(keywords),
                                     clean(locations),
                                     locationScope == null ? TalentLocationScope.CURRENT : locationScope,
                                     blankToNull(workplace),
                                     blankToNull(employmentType),
                                     clean(skills),
                                     clean(languages),
                                     sort == null ? TalentSort.NEWEST : sort,
                                     Math.max(0, page == null ? 0 : page),
                                     Math.clamp(size == null ? DEFAULT_SIZE : size, 1, MAX_SIZE));
    }

    public boolean hasQuery() {
        return query != null;
    }

    public int offset() {
        return page * size;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                     .filter(v -> v != null && !v.isBlank())
                     .map(String::trim)
                     .distinct()
                     .toList();
    }
}
