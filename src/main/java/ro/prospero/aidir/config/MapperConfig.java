package ro.prospero.aidir.config;

import org.jooq.JSONB;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.convention.NameTokenizers;
import org.modelmapper.jooq.RecordValueReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import ro.prospero.aidir.data.JsonbArrays;
import ro.prospero.aidir.data.ToolSubmissionDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolSubmissionRecord;

import java.util.List;

@Configuration
public class MapperConfig {

    @Bean
    @Qualifier("recordMapper")
    public ModelMapper recordMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                   .addValueReader(new RecordValueReader())
                   .setSourceNameTokenizer(NameTokenizers.UNDERSCORE)
                   .setSkipNullEnabled(true);

        return modelMapper;
    }

    @Bean
    @Primary
    @Qualifier("beanMapper")
    public ModelMapper beanMapper() {
        Converter<JSONB, List<String>> jsonbToList = ctx -> JsonbArrays.readStringArray(ctx.getSource());

        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        modelMapper.typeMap(ToolSubmissionRecord.class, ToolSubmissionDTO.class)
                   .addMappings(m -> {
                       m.using(jsonbToList).map(ToolSubmissionRecord::getTags, ToolSubmissionDTO::setTags);
                       m.using(jsonbToList).map(ToolSubmissionRecord::getCategories,
                                                ToolSubmissionDTO::setCategories);
                   });

        modelMapper.validate();

        return modelMapper;
    }

    public static <T> List<T> mapList(ModelMapper m, List<?> source, Class<T> type) {
        return source.stream().map(e -> m.map(e, type)).toList();
    }
}
