package ro.prospero.aidir.config;

import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@AllArgsConstructor
public class JooqConfig {
    private DataSource dataSource;

    @Bean
    public DSLContext dslContext() {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
}
