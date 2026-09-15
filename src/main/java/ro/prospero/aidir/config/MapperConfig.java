package ro.prospero.aidir.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSONB;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.NameTokenizers;
import org.modelmapper.jooq.RecordValueReader;
import org.modelmapper.spi.MappingContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import ro.prospero.aidir.data.ToolDTO;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolRecord;
import ro.prospero.aidir.jooq.generated.public_.tables.records.ToolSubmissionRecord;

import java.util.List;

@Configuration
public class MapperConfig {

    private final ObjectMapper objectMapper;

    public MapperConfig() {
        this.objectMapper = Jackson2ObjectMapperBuilder.json().build();
    }


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
        Converter<JSONB, List<String>> jsonbToList =
                ctx -> {
                    JSONB source = ctx.getSource();
                    if (source == null) return null;
                    try {
                        return objectMapper.readValue(
                                source.data(),
                                new TypeReference<List<String>>() {
                                }
                        );
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse JSONB", e);
                    }
                };

        ModelMapper modelMapper = new ModelMapper();

        modelMapper.typeMap(ToolSubmissionRecord.class, ToolDTO.class)
                   .addMappings(m -> {
                       m.using(jsonbToList).map(ToolSubmissionRecord::getTags, ToolDTO::setTags);
                       m.using(jsonbToList).map(ToolSubmissionRecord::getCategories, ToolDTO::setCategories);
                   });

        modelMapper.typeMap(ToolRecord.class, ToolDTO.class)
                   .addMappings(m -> {
                       m.using(jsonbToList).map(ToolRecord::getTags, ToolDTO::setTags);
                       m.using(jsonbToList).map(ToolRecord::getCategories, ToolDTO::setCategories);
                   });

        return modelMapper;
    }

    public static <T> List<T> mapList(ModelMapper m, List<?> source, Class<T> type) {
        return source.stream().map(e -> m.map(e, type)).toList();
    }
}
