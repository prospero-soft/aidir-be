package ro.prospero.aidir.jooq;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;

public class JooqGenerator {
    public static void main(String[] args) throws Exception {
        Jdbc jdbc = new Jdbc()
                .withDriver("org.postgresql.Driver")
                .withUrl("jdbc:postgresql://localhost:5432/aidir")
                .withUser("aidir")
                .withPassword("aidir");

        Database database = new Database()
                .withName("org.jooq.meta.postgres.PostgresDatabase")
                .withIncludes(".*")
                .withExcludes("""
                                      PG_.*
                                      | DATABASECHANGELOG
                                      | DATABASECHANGELOGLOCK
                                      """);
        Target target = new Target()
                .withDirectory("/src/main/java")
                .withPackageName("ro.prospero.aidir.jooq.generated");
        Configuration configuration = new Configuration()
                .withGenerator(new Generator()
                                       .withDatabase(database)
                                       .withTarget(target))
                .withJdbc(jdbc);

        GenerationTool.generate(configuration);
    }

}
