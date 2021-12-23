# FinEx Evolution
Small database migration library with `javax.inject` support.

# Requirements
 - Java 17+

# Usage
## Maven dependency
### Add maven repository with library
```xml
<repositories>
    <repository>
        <id>finex-repository</id>
        <url>https://maven.pkg.github.com/Fin-Ex/*</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```
### Attach dependency to project
```xml
<dependency>
    <groupId>ru.finex</groupId>
    <artifactId>finex-evolution</artifactId>
    <version>1.0</version>
</dependency>
```

## Usage
### Provide required components
#### DataSource
FinEx Evolution required a DataSource to execute migrations and control table versions.
DataSource injects into FinEx Evolution as named bean, name: `Migration`. To example provide it from hibernate with Guice:
```java
public class DataSourceProvider implements Provider<DataSource> {

    private final URL hibernateConfig;
    private final EnvConfigurator configurator;

    @Inject
    public DataSourceProvider(@Named("HibernateConfig") URL hibernateConfig, EnvConfigurator configurator) {
        this.hibernateConfig = hibernateConfig;
        this.configurator = configurator;
    }

    @Override
    public DataSource get() {
        Configuration configuration = new Configuration().configure(hibernateConfig);
        Properties properties = configuration.getProperties();
        configurator.configure(properties);
        Class<?> providerClass = ClassUtils.forName(properties.getProperty("hibernate.connection.provider_class"));
        ConnectionProvider connectionProvider = (ConnectionProvider) ClassUtils.createInstance(providerClass);
        if (connectionProvider instanceof Configurable configurable) {
            configurable.configure(properties);
        }

        return connectionProvider.unwrap(DataSource.class);
    }
}
```

```java
public class DbModule extends AbstractModule {

    @Override
    protected void configure() {
        // ...
        bind(DataSource.class).annotatedWith(Names.named("Migration")).toProvider(DataSourceProvider.class);
        // ...
    }

}
```

#### Reflections
FinEx Evolution use reflections library to scan classpath resources to find migration scenarios and find all usages of `Evolution`.
Provide reflections with Guice:
```java
public class ReflectionsModule extends AbstractModule {
    
    @Override 
    protected void configure() {
        bind(Reflections.class).toInstance(new Reflections(
            new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .addScanners(Scanners.Resources)
            )
        );
    }
    
}
```

#### Register service
```java
public class MigrationModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(MigrationService.class).to(MigrationServiceImpl.class);
    }
    
}
```

## Setup migration component
### Components
FinEx Evolution using multi-schema philosophy based on components. Component includes many tables and database objects with prefix, example:
```text
pew_table_a
pew_table_b
cpt_table_a
cpt_table_b
```
Where `pew_table_a` and `pew_table_b` is `pew` component, `cpt_table_a` and `cpt_table_b` is `cpt` component.

All components versioning is independent.

Enable `pew` component:
```java
@Evolution("pew")
public class MyApplication {
    
    public static void main(String[] args) {
        // ...
    }
    
}
```

If component is enable, FinEx Evolution try to find and execute migrations for it.

### Migration files
Migration files must be placed in project resources. File pattern: `[component]_[version]_[description].sql`, where:
 - `[component]` - component name
 - `[version]` - schema version
 - `[description]` - any description to easy search and control migrations

#### File format
```sql
# --- !Ups
create table pew(
    id int primary key,
    my_text varchar not null
);

# --- !Downs
drop table if exists pew;
```
All SQL code within `# --- !Ups` is "apply" code to migration into specified schema version.

SQL code inside `# --- !Downs` is rollback code, they used in error cases, to example - migration has been failed by some reason, all changes what doing within it going to rollback with it code. 
Beware of write rollback code: migration can be partially executed!

### Execute migrations
Just call `autoMigration` or `migration(String component)` from `MigrationService`.

```java
public class SomeClass {

    @Inject
    private MigrationService migrationService;
    
    public void doMigration() {
        migrationService.autoMigration(false);
    }

}

```