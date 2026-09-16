package ro.prospero.aidir.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ro.prospero.aidir.data.TalentLocationScope;

import java.util.Locale;

/**
 * Binds the hyphenated form the query string carries - {@code ?locationScope=current-or-relocate} - to the
 * enum constant. The front end's radio values are hyphenated and the enum is not; translating here keeps
 * both sides idiomatic rather than making one of them spell the other's convention.
 */
@Component
public class TalentLocationScopeConverter implements Converter<String, TalentLocationScope> {

    @Override
    public TalentLocationScope convert(String source) {
        return TalentLocationScope.valueOf(source.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
