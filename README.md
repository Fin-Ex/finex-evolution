# FinEx Evolution
Small database migration library with `javax.inject` support with smells of [Play Evolutions](https://www.playframework.com/documentation/2.8.x/Evolutions).

# Requirements
 - Java 17+ (or Java 8 with port)
 - PostgreSQL 9.3+

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
Java 17+
```xml
<dependency>
    <groupId>ru.finex</groupId>
    <artifactId>finex-evolution</artifactId>
    <version>1.1</version>
</dependency>
```

Java 8
```xml
<dependency>
    <groupId>ru.finex</groupId>
    <artifactId>finex-evolution</artifactId>
    <version>1.1.j8</version>
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

#### Classpath scanner
FinEx Evolution require implementing classpath scanner to scan resources to find migration scenarios and find all usages of `Evolution`.
Provide reflections with Guice:
```java
@Singleton
public class ClasspathScannerImpl implements ClasspathScanner {

    private final Reflections reflections;

    @Inject
    public ClasspathScannerImpl(Reflections reflections) {
        this.reflections = reflections;
    }

    @Override
    public Collection<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotation) {
        return reflections.getTypesAnnotatedWith(annotation);
    }
    
    @Override
    public Collection<String> getResources(Pattern pattern) {
        return reflections.getResources(pattern);
    }
    
}
```

Or provide OSGi plugin classes:

```java
@Singleton
public class ClasspathScannerImpl implements ClasspathScanner {

    private final List<Class<?>> classes = new ArrayList<>();
    private final List<String> resources = new ArrayList<>();

    @Inject
    public ClasspathScannerImpl(BundleContext context) {
        Bundle bundle = context.getBundle();
        BundleWiring wiring = bundle.adapt(BundleWiring.class);
        Collection<String> resources = wiring.listResources("/", "*", BundleWiring.LISTRESOURCES_RECURSE);
        for (String resource : resources) {
            String resourceName = resource.replaceAll("/", ".");
            if (resource.endsWith(".class")) {
                String className = resourceName.substring(0, resourceName.length() - ".class".length());

                Class<?> type;
                try {
                    type = Class.forName(className);
                } catch (Exception e) {
                    continue;
                }

                classes.add(type);
            } else {
                resources.add(resourceName);
            }
        }
    }

    @Override
    public Collection<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotation) {
        return classes.stream()
            .filter(e -> e.getAnnotationsByType(annotation).length > 0)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<String> getResources(Pattern pattern) {
        return resources.stream()
            .filter(e -> pattern.matcher(e).find())
            .collect(Collectors.toList());
    }

}
```

Module to bind implementation of classpath scanner:
```java
public class ClasspathModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ClasspathScanner.class).to(ClasspathScannerImpl.class);
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
All SQL code after `# --- !Ups` is "apply" code to migration into specified schema version.

SQL code after `# --- !Downs` is rollback code, they used in error cases, to example - migration has been failed by some reason, all changes what doing within it going to rollback with it code. 
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