package ro.prospero.aidir.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.ToolSort;

import java.util.Locale;

/**
 * Spring binds enum request parameters with {@code Enum.valueOf}, which is case-sensitive, so
 * {@code ?sort=newest} would be a 400 and only {@code ?sort=NEWEST} would work. Query strings are written
 * by hand and read by people; this lets the front end use the lower-case form without the endpoint taking
 * a raw String and validating it itself.
 *
 * <p>An unknown value still fails: the IllegalArgumentException below surfaces as a 400, which is what a
 * misspelled sort key deserves.
 */
@Component
public class ToolSortConverter implements Converter<String, ToolSort> {

    @Override
    public ToolSort convert(String source) {
        return ToolSort.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
