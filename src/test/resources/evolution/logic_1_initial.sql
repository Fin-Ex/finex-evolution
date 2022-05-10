# --- !Ups
create table money_transactions(
    id serial primary key,
    from_user_id int not null references users(id),
    to_user_id int not null references users(id),
    amount numeric not null
);

insert into money_transactions(from_user_id, to_user_id, amount) values(1, 1, 100);

# --- !Downs
drop table if exists money_transactions;