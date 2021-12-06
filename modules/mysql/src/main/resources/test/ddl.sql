-- This script will populate the database with the VinylDNS schema
-- It is used for testing with the H2 in-memory database where
-- migration is not necessary.
--
-- This should be run via the INIT parameter in the H2 JDBC URL
-- Ex: "jdbc:h2:mem:vinyldns;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=RUNSCRIPT FROM 'classpath:test/ddl.sql'"
--

CREATE TABLE batch_change
(
    id                  char(36)      not null primary key,
    user_id             char(36)      not null,
    user_name           varchar(45)   not null,
    created_time        datetime      not null,
    comments            varchar(1024) null,
    owner_group_id      char(36)      null,
    approval_status     tinyint       null,
    reviewer_id         char(36)      null,
    review_comment      varchar(1024) null,
    review_timestamp    datetime      null,
    scheduled_time      datetime      null,
    cancelled_timestamp datetime      null
);

create index batch_change_approval_status_index
    on batch_change (approval_status);

create index batch_change_user_id_created_time_index
    on batch_change (user_id, created_time);

create index batch_change_user_id_index
    on batch_change (user_id);

create table group_change
(
    id                char(36)   not null primary key,
    group_id          char(36)   not null,
    created_timestamp bigint(13) not null,
    data              blob       not null
);

create index group_change_group_id_index
    on group_change (group_id);

create table `groups`
(
    id                char(36)     not null primary key,
    name              varchar(256) not null,
    data              blob         not null,
    description       varchar(256) null,
    created_timestamp datetime     not null,
    email             varchar(256) not null
);

create index groups_name_index
    on `groups` (name);

create table membership
(
    user_id  char(36)   not null,
    group_id char(36)   not null,
    is_admin tinyint(1) not null,
    primary key (user_id, group_id)
);

create table message_queue
(
    id              char(36)      not null primary key,
    message_type    tinyint       null,
    in_flight       bit           null,
    data            blob          not null,
    created         datetime      not null,
    updated         datetime      not null,
    timeout_seconds int           not null,
    attempts        int default 0 not null
);

create index message_queue_inflight_index
    on message_queue (in_flight);

create index message_queue_timeout_index
    on message_queue (timeout_seconds);

create index message_queue_updated_index
    on message_queue (updated);

create table record_change
(
    id      char(36)   not null primary key,
    zone_id char(36)   not null,
    created bigint(13) not null,
    type    tinyint    not null,
    data    blob       not null
);

create index record_change_created_index
    on record_change (created);

create index record_change_zone_id_index
    on record_change (zone_id);

create table recordset
(
    id             char(36)     not null primary key,
    zone_id        char(36)     not null,
    name           varchar(256) not null,
    type           tinyint      not null,
    data           blob         not null,
    fqdn           varchar(255) not null,
    owner_group_id char(36)     null,
    constraint recordset_zone_id_name_type_index
    unique (zone_id, name, type)
);

create index recordset_fqdn_index
    on recordset (fqdn);

create index recordset_owner_group_id_index
    on recordset (owner_group_id);

create index recordset_type_index
    on recordset (type);

create table single_change
(
    id                   char(36)     not null primary key,
    seq_num              smallint     not null,
    input_name           varchar(255) not null,
    change_type          varchar(25)  not null,
    data                 blob         not null,
    status               varchar(16)  not null,
    batch_change_id      char(36)     not null,
    record_set_change_id char(36)     null,
    record_set_id        char(36)     null,
    zone_id              char(36)     null,
    constraint fk_single_change_batch_change_id_batch_change
    foreign key (batch_change_id) references batch_change (id)
    on delete cascade
);

create index single_change_batch_change_id_index
    on single_change (batch_change_id);

create index single_change_record_set_change_id_index
    on single_change (record_set_change_id);

create table stats
(
    id      bigint auto_increment primary key,
    name    varchar(255) not null,
    count   bigint       not null,
    created datetime     not null
);

create index stats_name_created_index
    on stats (name, created);

create index stats_name_index
    on stats (name);

create table task
(
    name      varchar(255) not null primary key,
    in_flight bit          not null,
    created   datetime     not null,
    updated   datetime     null
);

create table user
(
    id         char(36)     not null primary key,
    user_name  varchar(256) not null,
    access_key varchar(256) not null,
    data       blob         not null
);

create index user_access_key_index
    on user (access_key);

create index user_user_name_index
    on user (user_name);

create table user_change
(
    change_id         char(36)   not null primary key,
    user_id           char(36)   not null,
    data              blob       not null,
    created_timestamp bigint(13) not null
);

create table zone
(
    id             char(36)     not null primary key,
    name           varchar(256) not null,
    admin_group_id char(36)     not null,
    data           blob         not null,
    constraint zone_name_unique
    unique (name)
);

create index zone_admin_group_id_index
    on zone (admin_group_id);

create index zone_name_index
    on zone (name);

create table zone_access
(
    accessor_id char(36) not null,
    zone_id     char(36) not null,
    primary key (accessor_id, zone_id),
    constraint fk_zone_access_zone_id
    foreign key (zone_id) references zone (id)
    on delete cascade
);

create index zone_access_accessor_id_index
    on zone_access (accessor_id);

create index zone_access_zone_id_index
    on zone_access (zone_id);

create table zone_change
(
    change_id         char(36)   not null primary key,
    zone_id           char(36)   not null,
    data              blob       not null,
    created_timestamp bigint(13) not null
);

create index zone_change_created_timestamp_index
    on zone_change (created_timestamp);

create index zone_change_zone_id_index
    on zone_change (zone_id);

INSERT IGNORE INTO task(name, in_flight, created, updated)
VALUES ('user_sync', 0, NOW(), NULL);
