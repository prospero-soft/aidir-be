package ro.prospero.aidir.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.TalentSort;

import java.util.Locale;

/**
 * The talent counterpart of {@link ToolSortConverter}: lets {@code ?sort=newest} bind without the endpoint
 * taking a raw String, and lets a misspelled key fail as a 400.
 */
@Component
public class TalentSortConverter implements Converter<String, TalentSort> {

    @Override
    public TalentSort convert(String source) {
        return TalentSort.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
