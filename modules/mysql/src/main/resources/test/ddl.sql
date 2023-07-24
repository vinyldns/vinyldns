-- This script will populate the database with the VinylDNS schema
-- It is used for testing with the H2 in-memory database where
-- migration is not necessary.
--
-- This should be run via the INIT parameter in the H2 JDBC URL
-- Ex: "jdbc:h2:mem:vinyldns;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=RUNSCRIPT FROM 'classpath:test/ddl.sql'"
--

CREATE TABLE IF NOT EXISTS batch_change
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

CREATE INDEX IF NOT EXISTS batch_change_approval_status_index
    on batch_change (approval_status);

CREATE INDEX IF NOT EXISTS batch_change_user_id_created_time_index
    on batch_change (user_id, created_time);

CREATE INDEX IF NOT EXISTS batch_change_user_id_index
    on batch_change (user_id);

CREATE TABLE IF NOT EXISTS group_change
(
    id                char(36)   not null primary key,
    group_id          char(36)   not null,
    created_timestamp bigint(13) not null,
    data              blob       not null
);

CREATE INDEX IF NOT EXISTS group_change_group_id_index
    on group_change (group_id);

CREATE TABLE IF NOT EXISTS `groups`
(
    id                char(36)     not null primary key,
    name              varchar(256) not null,
    data              blob         not null,
    description       varchar(256) null,
    created_timestamp datetime     not null,
    email             varchar(256) not null
);

CREATE INDEX IF NOT EXISTS groups_name_index
    on `groups` (name);

CREATE TABLE IF NOT EXISTS membership
(
    user_id  char(36)   not null,
    group_id char(36)   not null,
    is_admin tinyint(1) not null,
    primary key (user_id, group_id)
);

CREATE TABLE IF NOT EXISTS message_queue
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

CREATE INDEX IF NOT EXISTS message_queue_inflight_index
    on message_queue (in_flight);

CREATE INDEX IF NOT EXISTS message_queue_timeout_index
    on message_queue (timeout_seconds);

CREATE INDEX IF NOT EXISTS message_queue_updated_index
    on message_queue (updated);

CREATE TABLE IF NOT EXISTS record_change
(
    id      char(36)   not null primary key,
    zone_id char(36)   not null,
    created bigint(13) not null,
    type    tinyint    not null,
    data    blob       not null
);

CREATE INDEX IF NOT EXISTS record_change_created_index
    on record_change (created);

CREATE INDEX IF NOT EXISTS record_change_zone_id_index
    on record_change (zone_id);

CREATE TABLE IF NOT EXISTS recordset
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

CREATE INDEX IF NOT EXISTS recordset_fqdn_index
    on recordset (fqdn);

CREATE INDEX IF NOT EXISTS recordset_owner_group_id_index
    on recordset (owner_group_id);

CREATE INDEX IF NOT EXISTS recordset_type_index
    on recordset (type);

CREATE TABLE IF NOT EXISTS recordset_data
(
  recordset_id VARCHAR(36) NOT NULL,
  zone_id VARCHAR(36) NOT NULL,
  fqdn VARCHAR(255) NOT NULL,
  reverse_fqdn VARCHAR(255) NOT NULL,
  type VARCHAR(25) NOT NULL,
  record_data VARCHAR(4096) NOT NULL,
  ip VARBINARY(16)
);

CREATE INDEX IF NOT EXISTS recordset_data_zone_id_index
    on recordset_data (zone_id);

CREATE INDEX IF NOT EXISTS recordset_data_type_index
    on recordset_data (type);

CREATE INDEX IF NOT EXISTS recordset_data_fqdn_index
    on recordset_data (fqdn);

CREATE INDEX IF NOT EXISTS recordset_data_ip_index
    on recordset_data (ip);

CREATE INDEX IF NOT EXISTS recordset_data_recordset_id_index
    on recordset_data (recordset_id);

CREATE INDEX IF NOT EXISTS recordset_data_reverse_fqdn_index
    on recordset_data (reverse_fqdn);

CREATE TABLE IF NOT EXISTS single_change
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

CREATE INDEX IF NOT EXISTS single_change_batch_change_id_index
    on single_change (batch_change_id);

CREATE INDEX IF NOT EXISTS single_change_record_set_change_id_index
    on single_change (record_set_change_id);

CREATE TABLE IF NOT EXISTS stats
(
    id      bigint auto_increment primary key,
    name    varchar(255) not null,
    count   bigint       not null,
    created datetime     not null
);

CREATE INDEX IF NOT EXISTS stats_name_created_index
    on stats (name, created);

CREATE INDEX IF NOT EXISTS stats_name_index
    on stats (name);

CREATE TABLE IF NOT EXISTS task
(
    name      varchar(255) not null primary key,
    in_flight bit          not null,
    created   datetime     not null,
    updated   datetime     null
);

CREATE TABLE IF NOT EXISTS user
(
    id         char(36)     not null primary key,
    user_name  varchar(256) not null,
    access_key varchar(256) not null,
    data       blob         not null
);

CREATE INDEX IF NOT EXISTS user_access_key_index
    on user (access_key);

CREATE INDEX IF NOT EXISTS user_user_name_index
    on user (user_name);

CREATE TABLE IF NOT EXISTS user_change
(
    change_id         char(36)   not null primary key,
    user_id           char(36)   not null,
    data              blob       not null,
    created_timestamp bigint(13) not null
);

CREATE TABLE IF NOT EXISTS zone
(
    id             char(36)     not null primary key,
    name           varchar(256) not null,
    admin_group_id char(36)     not null,
    data           blob         not null,
    constraint zone_name_unique
    unique (name)
);

CREATE INDEX IF NOT EXISTS zone_admin_group_id_index
    on zone (admin_group_id);

CREATE INDEX IF NOT EXISTS zone_name_index
    on zone (name);

CREATE TABLE IF NOT EXISTS zone_access
(
    accessor_id char(36) not null,
    zone_id     char(36) not null,
    primary key (accessor_id, zone_id)
);

CREATE INDEX IF NOT EXISTS zone_access_accessor_id_index
    on zone_access (accessor_id);

CREATE INDEX IF NOT EXISTS zone_access_zone_id_index
    on zone_access (zone_id);

CREATE TABLE IF NOT EXISTS zone_change
(
    change_id         char(36)   not null primary key,
    zone_id           char(36)   not null,
    data              blob       not null,
    created_timestamp bigint(13) not null
);

CREATE INDEX IF NOT EXISTS zone_change_created_timestamp_index
    on zone_change (created_timestamp);

CREATE INDEX IF NOT EXISTS zone_change_zone_id_index
    on zone_change (zone_id);

DELETE FROM task WHERE name = 'user_sync';

INSERT IGNORE INTO task(name, in_flight, created, updated)
VALUES ('user_sync', 0, NOW(), NULL);
