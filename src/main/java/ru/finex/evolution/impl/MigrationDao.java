package ru.finex.evolution.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * @author m0nster.mind
 */
@Singleton
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:Indentation"})
public class MigrationDao {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DataSource dataSource;

    @Inject
    public MigrationDao(@Named("Migration") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void install() {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            Savepoint savepoint = beginTrx(connection);

            try {
                Statement statement = connection.createStatement();
                statement.addBatch(MigrationConsts.MIGRATION_TABLE);
                statement.addBatch(MigrationConsts.MIGRATION_INDEX);
                statement.executeBatch();
                statement.close();
                savepoint = null;
            } finally {
                flushTrx(connection, savepoint, autoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getChecksumsByComponent(String component) {
        String query =
                "select checksum\n" +
                        "from db_evolutions\n" +
                        "where component = ?\n" +
                        "order by version asc";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, component);
            try (ResultSet results = statement.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (results.next()) {
                    result.add(results.getString(1));
                }

                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("checkstyle:NestedTryDepth")
    public void rollbackAndDeleteRecursive(String component, int version) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            Savepoint savepoint = beginTrx(connection);

            try {
                List<String> queries = getDownQueriesByComponentAndUpperVersion(connection, component, version);
                try (Statement statement = connection.createStatement()) {
                    for (String query : queries) {
                        statement.addBatch(query);
                    }

                    statement.executeBatch();
                }

                delete(connection, component, version);
                savepoint = null;
            } finally {
                flushTrx(connection, savepoint, autoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private List<String> getDownQueriesByComponentAndUpperVersion(Connection connection, String component, int version) throws SQLException, JsonProcessingException {
        String query =
                "select down_queries\n" +
                        "from db_evolutions\n" +
                        "where component = ? and version >= ?\n" +
                        "order by version desc";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, component);
            statement.setInt(2, version);
            try (ResultSet results = statement.executeQuery()) {
                boolean hasResult = false;
                List<String> result = new ArrayList<>();
                while (results.next()) {
                    List<String> queries = mapper.readValue(
                            results.getString(1),
                            new TypeReference<List<String>>() {}
                    );
                    result.addAll(queries);
                    hasResult = true;
                }

                if (!hasResult) {
                    throw new NullPointerException(String.format(
                        "Down queries not found for %s component and %d version.",
                        component, version
                    ));
                }

                return result;
            }
        }
    }

    private void delete(Connection connection, String component, int version) throws SQLException {
        String query =
                "delete from db_evolutions\n" +
                        "where component = ? and version >= ?\n";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, component);
            statement.setInt(2, version);
            statement.execute();
        }
    }

    public void applyAndSave(MigrationData data, String checksum) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            Savepoint savepoint = beginTrx(connection);

            try {
                apply(connection, data.getUpQueries());
                save(connection, data, checksum);
                savepoint = null;
            } finally {
                flushTrx(connection, savepoint, autoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void apply(Connection connection, List<String> queries) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String query : queries) {
                statement.addBatch(query);
            }

            statement.executeBatch();
        }
    }

    private void save(Connection connection, MigrationData data, String checksum) throws SQLException, JsonProcessingException {
        String query =
                "insert into db_evolutions(\n" +
                        "    component,\n" +
                        "    version,\n" +
                        "    checksum,\n" +
                        "    up_queries,\n" +
                        "    down_queries\n" +
                        ") values (?, ?, ?, ?::json, ?::json)";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, data.getComponent());
            statement.setInt(2, data.getVersion());
            statement.setString(3, checksum);
            statement.setObject(4, mapper.writeValueAsString(data.getUpQueries()));
            statement.setObject(5, mapper.writeValueAsString(data.getDownQueries()));
            statement.execute();
        }
    }

    private static Savepoint beginTrx(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        return connection.setSavepoint();
    }

    private static void flushTrx(Connection connection, Savepoint savepoint, boolean autoCommit) throws SQLException {
        try {
            if (savepoint != null) {
                connection.rollback(savepoint);
            }
            connection.commit();
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

}
