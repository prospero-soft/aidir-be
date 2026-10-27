package ro.prospero.aidir.data;

import java.util.List;

/** One page of the news list. {@code total} counts every article the filters match, not just this page. */
public record NewsPage(List<NewsCardDTO> items, long total, int page, int size) {
}
