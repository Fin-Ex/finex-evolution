package ru.finex.evolution.impl;

import lombok.experimental.UtilityClass;

/**
 * @author m0nster.mind
 */
@UtilityClass
public class MigrationConsts {

    public static final String MIGRATION_TABLE =
        "create table if not exists db_evolutions(\n" +
        "    id serial primary key,\n" +
        "    component varchar not null,\n" +
        "    version int not null,\n" +
        "    checksum varchar not null,\n" +
        "    up_queries json not null,\n" +
        "    down_queries json not null,\n" +
        "    apply_timestamp timestamp default now()\n" +
        ")";

    @SuppressWarnings("checkstyle:Indentation")
    public static final String MIGRATION_INDEX =
        "create unique index if not exists db_evolutions_component_version_idx on db_evolutions(component, version)";

}
