package ro.prospero.aidir.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.NewsSort;

import java.util.Locale;

/** Binds {@code ?sort=latest} as {@link ToolSortConverter} binds the directory's sort. */
@Component
public class NewsSortConverter implements Converter<String, NewsSort> {

    @Override
    public NewsSort convert(String source) {
        return NewsSort.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
