package ru.finex.evolution;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.RequiredArgsConstructor;
import ru.finex.evolution.impl.MigrationServiceImpl;

import java.util.Properties;
import javax.sql.DataSource;

/**
 * Provides database dependencies.
 * @author m0nster.mind
 */
@RequiredArgsConstructor
public class DbModule extends AbstractModule {

    private final String url;
    private final String user;
    private final String password;

    @Override
    protected void configure() {
        bind(DataSource.class).annotatedWith(Names.named("Migration")).toInstance(dataSource());
        bind(ClasspathScanner.class).to(ClasspathScannerImpl.class);
        bind(MigrationService.class).to(MigrationServiceImpl.class);
    }

    private DataSource dataSource() {
        Properties properties = new Properties();
        properties.put("user", user);
        properties.put("password", password);
        return new SimpleDataSource(url, properties);
    }

}
