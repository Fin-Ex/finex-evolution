package ru.finex.evolution;

/**
 * @author m0nster.mind
 */
public interface MigrationService {

    /**
     * Автоматическая миграция зарегистрированных компонентов через {@link ru.finex.evolution.Evolution}.
     * @param autoRollback автоматический роллбек примененных миграций, если они изменились
     */
    void autoMigration(boolean autoRollback);

    /**
     * Миграция определенного компонента и всех его зависимостей.
     * @param component компонент
     * @param autoRollback автоматический роллбек примененных миграций, если они изменились
     */
    void migrate(String component, boolean autoRollback);

}
