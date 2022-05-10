# --- !Ups
create table users(
    id serial primary key,
    "name" varchar not null,
    password varchar not null
);

insert into users("name", password) values('test_user', 'password');

# --- !Downs
drop table if exists users;