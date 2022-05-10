package ru.finex.evolution;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.name.Names;
import lombok.Cleanup;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

/**
 * @author m0nster.mind
 */
public class MigrationTest {

    private PostgreSQLContainer<?> postgres;
    private Injector injector;

    @BeforeEach
    public void upPostgres() {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse(PostgreSQLContainer.IMAGE).withTag("9.6.12"));
        postgres.start();

        injector = Guice.createInjector(Stage.PRODUCTION, new DbModule(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @Test
    public void multischemaTest() {
        MigrationService migrationService = injector.getInstance(MigrationService.class);
        migrationService.autoMigration(false);

        DataSource dataSource = injector.getInstance(Key.get(DataSource.class, Names.named("Migration")));
        verifyAuthSchema(dataSource);
        verifyLogicSchema(dataSource);
    }

    @Test
    public void authSchemaTest() {
        MigrationService migrationService = injector.getInstance(MigrationService.class);
        migrationService.migrate("auth", false);

        DataSource dataSource = injector.getInstance(Key.get(DataSource.class, Names.named("Migration")));
        verifyAuthSchema(dataSource);
    }

    @Test
    public void logicSchemaTest() {
        MigrationService migrationService = injector.getInstance(MigrationService.class);
        migrationService.migrate("logic", false);

        DataSource dataSource = injector.getInstance(Key.get(DataSource.class, Names.named("Migration")));
        verifyLogicSchema(dataSource);
    }

    @SneakyThrows
    private static void verifyAuthSchema(DataSource dataSource) {
        @Cleanup Connection connection = dataSource.getConnection();
        @Cleanup PreparedStatement statement = connection.prepareStatement("select * from users");
        @Cleanup ResultSet resultSet = statement.executeQuery();
        Assertions.assertTrue(resultSet.next(), "Empty result set");
        Assertions.assertEquals("test_user", resultSet.getString("name"));
        Assertions.assertEquals("password", resultSet.getString("password"));
        Assertions.assertFalse(resultSet.next(), "More than 1 record in table");
    }

    @SneakyThrows
    private static void verifyLogicSchema(DataSource dataSource) {
        @Cleanup Connection connection = dataSource.getConnection();
        @Cleanup PreparedStatement statement = connection.prepareStatement("select * from money_transactions");
        @Cleanup ResultSet resultSet = statement.executeQuery();
        Assertions.assertTrue(resultSet.next(), "Empty result set");
        Assertions.assertEquals(1, resultSet.getInt("from_user_id"));
        Assertions.assertEquals(1, resultSet.getInt("to_user_id"));
        Assertions.assertEquals(new BigDecimal("100"), resultSet.getBigDecimal("amount"));
        Assertions.assertFalse(resultSet.next(), "More than 1 record in table");
    }

    @AfterEach
    public void downPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

}
