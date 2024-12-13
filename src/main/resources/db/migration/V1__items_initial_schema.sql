CREATE TABLE items(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    user_name TEXT NOT NULL
);

create index i_items_user on items(user_name);

create extension btree_gist;

create table user_ratelimiter(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY not null,
    user_name text not null,
    limiter_range tstzrange not null,
    create_dt timestamp with time zone not null,
    constraint user_limiter_exclusion_constraint
        exclude using gist (user_name with =, limiter_range with &&)
);

create index i_user_ratelimiter_create_dt on user_ratelimiter(create_dt);

create table shedlock(
    name varchar(64) not null,
    lock_until timestamp not null,
    locked_at timestamp not null,
    locked_by varchar(255) not null,
    primary key(name)
)