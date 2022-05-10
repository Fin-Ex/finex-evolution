# --- !Ups
create table app_logs(
    id serial primary key,
    "time" timestamp not null,
    "level" int not null,
    message varchar not null
);

# --- !Downs
drop table if exists app_logs;