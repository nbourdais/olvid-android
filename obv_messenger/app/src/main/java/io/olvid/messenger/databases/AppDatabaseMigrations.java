/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.messenger.databases;


import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.olvid.engine.Logger;
import io.olvid.engine.crypto.Hash;
import io.olvid.engine.crypto.Suite;
import io.olvid.messenger.App;

class AppDatabaseMigrations {
    static final Migration[] MIGRATIONS = new Migration[] {
            new Migration(49, 50) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 49 TO 50");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `action_shortcut_configuration_table` (`app_widget_id` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `serialized_configuration` TEXT NOT NULL, PRIMARY KEY(`app_widget_id`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_action_shortcut_configuration_table_discussion_id` ON `action_shortcut_configuration_table` (`discussion_id`)");
                }
            },

            new Migration(48, 49) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 48 TO 49");

                    database.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `reception_status` INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_fyle_message_join_with_status_message_id_engine_number` ON `fyle_message_join_with_status` (`message_id`, `engine_number`)");

                    database.execSQL("ALTER TABLE `message_recipient_info_table` ADD COLUMN `undelivered_attachment_numbers` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `message_recipient_info_table` ADD COLUMN `unread_attachment_numbers` TEXT DEFAULT NULL");
                }
            },


            new Migration(47, 48) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 47 TO 48");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `reaction_request_table` (`discussion_id` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `reacter` BLOB NOT NULL, `server_timestamp` INTEGER NOT NULL, `reaction` TEXT NOT NULL, PRIMARY KEY(`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`, `reacter`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_discussion_id` ON `reaction_request_table` (`discussion_id`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_server_timestamp` ON `reaction_request_table` (`server_timestamp`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_reaction_request_table_discussion_id_sender_identifier_sender_thread_identifier_sender_sequence_number` ON `reaction_request_table` (`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`)");
                }
            },


            new Migration(46, 47) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 46 TO 47");

                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `custom_display_name` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `unlock_password` BLOB DEFAULT NULL");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `unlock_salt` BLOB DEFAULT NULL");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `pref_mute_notifications` INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `pref_mute_notifications_timestamp` INTEGER DEFAULT NULL");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `pref_show_neutral_notification_when_hidden` INTEGER NOT NULL DEFAULT 0");

                    database.execSQL("DELETE FROM `invitation_table`");
                    database.execSQL("ALTER TABLE `invitation_table` ADD COLUMN `category_id` INTEGER NOT NULL DEFAULT 0");
                }
            },


            new Migration(45, 46) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 45 TO 46");

                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1");
                    database.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1");
                }
            },


            new Migration(44, 45) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 44 TO 45");

                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `missed_message_count` INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `latest_discussion_sender_sequence_number_table` (`discussion_id` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `latest_sequence_number` INTEGER NOT NULL, PRIMARY KEY(`discussion_id`, `sender_identifier`, `sender_thread_identifier`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                }
            },


            new Migration(43, 44) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 43 TO 44");

                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `personal_note` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `group_table` ADD COLUMN `personal_note` TEXT DEFAULT NULL");
                }
            },


            new Migration(42, 43) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 42 TO 43");

                    database.execSQL("ALTER TABLE `fyle_table` RENAME TO `old_fyle`");
                    database.execSQL("DROP INDEX `index_fyle_table_sha256`");
                    database.execSQL("CREATE TABLE `fyle_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `permanent_file_path` TEXT, `sha256` BLOB)");
                    database.execSQL("CREATE UNIQUE INDEX `index_fyle_table_sha256` ON `fyle_table` (`sha256`)");
                    database.execSQL("INSERT INTO `fyle_table` SELECT `id`, `permanent_file_path`, `sha256` FROM `old_fyle`");
                    database.execSQL("DROP TABLE `old_fyle`");
                }
            },


            new Migration(41, 42) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 41 TO 42");

                    database.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `audio_played` INTEGER NOT NULL DEFAULT 1");
                }
            },


            new Migration(40, 41) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 40 TO 41");

                    database.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `mini_preview` BLOB DEFAULT NULL");
                }
            },


            new Migration(39, 40) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 39 TO 40");

                    database.execSQL("ALTER TABLE `fyle_message_join_with_status` ADD COLUMN `image_resolution` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `image_resolutions` TEXT DEFAULT NULL");
                }
            },


            new Migration(38, 39) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 38 TO 39");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `known_certificate` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `domain_name` TEXT NOT NULL, `certificate_bytes` BLOB NOT NULL, `trust_timestamp` INTEGER, `expiration_timestamp` INTEGER NOT NULL, `issuers` TEXT NOT NULL, `encoded_full_chain` TEXT NOT NULL)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_known_certificate_domain_name` ON `known_certificate` (`domain_name`)");
                }
            },


            new Migration(37, 38) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 37 TO 38");
                    database.execSQL("DROP TABLE IF EXISTS `anonymous_usage_log`");
                    database.execSQL("DROP TABLE IF EXISTS `anonymizer_for_owned_identity`");
                }
            },

            new Migration(36, 37) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 36 TO 37");
                    database.execSQL("ALTER TABLE `call_log_table` RENAME TO `old_call_log_table`");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `call_log_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `bytes_group_owner_and_uid` BLOB, `timestamp` INTEGER NOT NULL, `call_type` INTEGER NOT NULL, `call_status` INTEGER NOT NULL, `duration` INTEGER NOT NULL, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("DROP INDEX `index_call_log_table_bytes_owned_identity`");
                    database.execSQL("DROP INDEX `index_call_log_table_timestamp`");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity` ON `call_log_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_timestamp` ON `call_log_table` (`timestamp`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `call_log_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `call_log_item_contact_join` (`call_log_item_id` INTEGER NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, PRIMARY KEY(`call_log_item_id`, `bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`call_log_item_id`) REFERENCES `call_log_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_owned_identity`, `bytes_contact_identity`) REFERENCES `contact_table`(`bytes_owned_identity`, `bytes_contact_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_item_contact_join_call_log_item_id` ON `call_log_item_contact_join` (`call_log_item_id`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_item_contact_join_bytes_owned_identity_bytes_contact_identity` ON `call_log_item_contact_join` (`bytes_owned_identity`, `bytes_contact_identity`)");

                    try (Cursor cursor = database.query("SELECT `id`, `bytes_contact_identity`, `bytes_owned_identity`, `timestamp`, `call_type`, `call_status`, `duration` FROM `old_call_log_table`")) {
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(0);
                            byte[] bytesContactIdentity = cursor.getBlob(1);
                            byte[] bytesOwnedIdentity = cursor.getBlob(2);
                            long timestamp = cursor.getLong(3);
                            int callType = cursor.getInt(4);
                            int callStatus = cursor.getInt(5);
                            int duration = cursor.getInt(6);

                            database.execSQL("INSERT INTO `call_log_table` VALUES (?,?,?,?,?,?,?)", new Object[]{
                                    id,
                                    bytesOwnedIdentity,
                                    null,
                                    timestamp,
                                    callType,
                                    callStatus,
                                    duration,
                            });
                            database.execSQL("INSERT INTO `call_log_item_contact_join` VALUES (?,?,?)", new Object[]{
                                    id,
                                    bytesOwnedIdentity,
                                    bytesContactIdentity,
                            });
                        }
                    }

                    database.execSQL("DROP TABLE `old_call_log_table`");
                }
            },


            new Migration(35,36) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 35 TO 36");
                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `custom_name_hue` INTEGER DEFAULT NULL");
                }
            },

            new Migration(34,35) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 34 TO 35");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `identity_details` TEXT DEFAULT NULL");

                    database.execSQL("ALTER TABLE `contact_table` RENAME TO  `old_contact_table`");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `contact_table` (`bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_display_name` TEXT, `display_name` TEXT NOT NULL, `sort_display_name` BLOB NOT NULL, `full_search_display_name` TEXT NOT NULL, `identity_details` TEXT, `new_published_details` INTEGER NOT NULL, `device_count` INTEGER NOT NULL, `established_channel_count` INTEGER NOT NULL, `photo_url` TEXT, `custom_photo_url` TEXT, `keycloak_managed` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");

                    try (Cursor cursor = database.query("SELECT `bytes_contact_identity`, `bytes_owned_identity`, `custom_display_name`, `display_name`, `identity_details`, `new_published_details`, `device_count`, `established_channel_count`, `photo_url`, `custom_photo_url`, `keycloak_managed` FROM `old_contact_table`")) {

                        database.execSQL("DROP INDEX `index_contact_table_bytes_owned_identity`");
                        database.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_bytes_owned_identity` ON `contact_table` (`bytes_owned_identity`)");
                        database.execSQL("DROP INDEX `index_contact_table_display_name`");
                        database.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_display_name` ON `contact_table` (`display_name`)");
                        database.execSQL("DROP INDEX `index_contact_table_custom_display_name`");
                        database.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_custom_display_name` ON `contact_table` (`custom_display_name`)");
                        database.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_table_sort_display_name` ON `contact_table` (`sort_display_name`)");

                        Collator collator = Collator.getInstance();

                        while (cursor.moveToNext()) {
                            byte[] bytesContactIdentity = cursor.getBlob(0);
                            byte[] bytesOwnedIdentity = cursor.getBlob(1);
                            String customDisplayName = cursor.getString(2);
                            String displayName = cursor.getString(3);
                            String identityDetails = cursor.getString(4);
                            int newPublishedDetails = cursor.getInt(5);
                            int deviceCount = cursor.getInt(6);
                            int establishedChannelCount = cursor.getInt(7);
                            String photoUrl = cursor.getString(8);
                            String customPhotoUrl = cursor.getString(9);
                            int keycloakManaged = cursor.getInt(10);

                            byte[] sortDisplayName;
                            String fullSearchDisplayName;
                            if (customDisplayName != null) {
                                sortDisplayName = collator.getCollationKey(customDisplayName).toByteArray();
                                fullSearchDisplayName = App.unAccent(customDisplayName + " " + displayName);
                            } else {
                                sortDisplayName = collator.getCollationKey(displayName).toByteArray();
                                fullSearchDisplayName = App.unAccent(displayName);
                            }

                            database.execSQL("INSERT INTO `contact_table` VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?)", new Object[]{
                                    bytesContactIdentity,
                                    bytesOwnedIdentity,
                                    customDisplayName,
                                    displayName,
                                    sortDisplayName,
                                    fullSearchDisplayName,
                                    identityDetails,
                                    newPublishedDetails,
                                    deviceCount,
                                    establishedChannelCount,
                                    photoUrl,
                                    customPhotoUrl,
                                    keycloakManaged
                            });
                        }
                    }

                    database.execSQL("DROP TABLE `old_contact_table`");
                }
            },

            new Migration(33,34) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 33 TO 34");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `remote_delete_and_edit_request_table` (`discussion_id` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `server_timestamp` INTEGER NOT NULL, `request_type` INTEGER NOT NULL, `body` TEXT, `remote_deleter` BLOB, PRIMARY KEY(`discussion_id`, `sender_identifier`, `sender_thread_identifier`, `sender_sequence_number`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_delete_request_table_discussion_id` ON `remote_delete_and_edit_request_table` (`discussion_id`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_delete_request_table_server_timestamp` ON `remote_delete_and_edit_request_table` (`server_timestamp`)");
                }
            },

            new Migration(32,33) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 32 TO 33");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `keycloak_managed` INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `keycloak_managed` INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `keycloak_managed` INTEGER NOT NULL DEFAULT 0");
                }
            },

            new Migration(31,32) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 31 TO 32");
                    database.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `unread` INTEGER NOT NULL DEFAULT 0");
                }
            },

            new Migration(30,31) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 30 TO 31");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `reactions` TEXT DEFAULT NULL");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `reactions_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `bytes_identity` BLOB, `emoji` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_reactions_table_message_id` ON `reactions_table` (`message_id`)");
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reactions_table_message_id_bytes_identity` ON `reactions_table` (`message_id`, `bytes_identity`)");
                }
            },

            new Migration(29,30) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 29 TO 30");
                    database.execSQL("ALTER TABLE `message_metadata_table` ADD COLUMN `bytes_remote_identity` BLOB DEFAULT NULL");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `edited` INTEGER NOT NULL DEFAULT 0");
                }
            },

            new Migration(28,29) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 28 TO 29");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_mute_notifications_timestamp` BIGINT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_discussion_retention_count` BIGINT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_discussion_retention_duration` BIGINT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `shared_settings_version` INTEGER DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `setting_existence_duration` INTEGER DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `setting_visibility_duration` INTEGER DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `setting_read_once` INTEGER NOT NULL DEFAULT 0");
                }
            },

            new Migration(27,28) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 27 TO 28");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_expiration` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `wipe_status` INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `wiped_attachment_count` INTEGER NOT NULL DEFAULT 0");

                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_auto_open_limited_visibility_inbound` INTEGER DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_retain_wiped_outbound_messages` INTEGER DEFAULT NULL");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `message_metadata_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_message_id` ON `message_metadata_table` (`message_id`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_message_id_kind` ON `message_metadata_table` (`message_id`, `kind`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_metadata_table_timestamp` ON `message_metadata_table` (`timestamp`)");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `message_expiration_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message_id` INTEGER NOT NULL, `expiration_timestamp` INTEGER NOT NULL, `wipe_only` INTEGER NOT NULL, FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_expiration_table_message_id` ON `message_expiration_table` (`message_id`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_expiration_table_expiration_timestamp` ON `message_expiration_table` (`expiration_timestamp`)");
                }
            },

            new Migration(26,27) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 26 TO 27");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `api_key_permissions` BIGINT NOT NULL DEFAULT 0");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `api_key_expiration_timestamp` BIGINT DEFAULT NULL");
                }
            },

            new Migration(25,26) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 25 TO 26");
                    database.execSQL("ALTER TABLE `message_recipient_info_table` ADD COLUMN `unsent_attachment_numbers` TEXT DEFAULT NULL");
                }
            },

            new Migration(24,25) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 24 TO 25");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `call_log_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `timestamp` INTEGER NOT NULL, `call_type` INTEGER NOT NULL, `call_status` INTEGER NOT NULL, `duration` INTEGER NOT NULL, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_owned_identity`, `bytes_contact_identity`) REFERENCES `contact_table`(`bytes_owned_identity`, `bytes_contact_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity` ON `call_log_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_bytes_owned_identity_bytes_contact_identity` ON `call_log_table` (`bytes_owned_identity`, `bytes_contact_identity`)");
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_log_table_timestamp` ON `call_log_table` (`timestamp`)");
                }
            },



            new Migration(4,5) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 4 TO 5");
                    database.execSQL("ALTER TABLE `message_table` RENAME TO  `old_message_table`;");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `message_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `content_body` TEXT, `sort_index` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `status` INTEGER NOT NULL, `message_type` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `engine_message_identifier` BLOB, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `has_attachments` INTEGER NOT NULL, FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("DROP INDEX `index_message_table_discussion_id`");
                    database.execSQL("CREATE INDEX `index_message_table_discussion_id` ON `message_table` (`discussion_id`)");
                    database.execSQL("DROP INDEX `index_message_table_engine_message_identifier_engine_number`");
                    database.execSQL("CREATE INDEX `index_message_table_engine_message_identifier` ON `message_table` (`engine_message_identifier`)");
                    database.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`");
                    database.execSQL("CREATE UNIQUE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)");
                    database.execSQL("INSERT INTO `message_table` SELECT `id`, `sender_sequence_number`, `json_body`, `sort_index`, `timestamp`, `status`, `message_type`, `discussion_id`, `engine_message_identifier`, `sender_identifier`, `sender_thread_identifier`, `has_attachments` FROM `old_message_table`");
                    database.execSQL("DROP TABLE `old_message_table`");
                }
            },

            new Migration(3,4) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 3 TO 4");
                    database.execSQL("ALTER TABLE `anonymous_usage_log` ADD COLUMN `api_key` TEXT NOT NULL DEFAULT ''");
                }
            },

            new Migration(2,3) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 2 TO 3");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `api_key_status` INTEGER NOT NULL DEFAULT 0");
                }
            },

            new Migration(1,2) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 1 TO 2");
                    database.execSQL("ALTER TABLE `message_table` RENAME TO  `old_message_table`;");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `message_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `json_body` TEXT NOT NULL, `sort_index` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `status` INTEGER NOT NULL, `message_type` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `engine_message_identifier` BLOB, `engine_number` INTEGER NOT NULL, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `has_attachments` INTEGER NOT NULL, FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
                    database.execSQL("DROP INDEX `index_message_table_discussion_id`");
                    database.execSQL("CREATE INDEX `index_message_table_discussion_id` ON `message_table` (`discussion_id`)");
                    database.execSQL("DROP INDEX `index_message_table_engine_message_identifier_engine_number`");
                    database.execSQL("CREATE INDEX `index_message_table_engine_message_identifier_engine_number` ON `message_table` (`engine_message_identifier`, `engine_number`)");
                    database.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`");
                    database.execSQL("CREATE UNIQUE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)");
                    database.execSQL("INSERT INTO `message_table` SELECT `id`, `sender_sequence_number`, `json_body`, `sort_index`, `timestamp`, `status`, `is_outbound`, `discussion_id`, `engine_message_identifier`, `engine_number`, `sender_identifier`, `sender_thread_identifier`, `has_attachments` FROM `old_message_table`");
                    database.execSQL("DROP TABLE `old_message_table`");
                }
            },




            new Migration(5,6) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 5 TO 6");
                    database.execSQL("CREATE INDEX `index_discussion_table_group_id` ON `discussion_table` (`group_id`)");
                    database.execSQL("CREATE INDEX `index_discussion_table_title` ON `discussion_table` (`title`)");
                    database.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`");
                    database.execSQL("CREATE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)");
                    database.execSQL("CREATE INDEX `index_message_table_message_type_status` ON `message_table` (`message_type`, `status`)");
                    database.execSQL("CREATE INDEX `index_message_table_timestamp` ON `message_table` (`timestamp`)");
                    database.execSQL("CREATE INDEX `index_message_table_sort_index` ON `message_table` (`sort_index`)");
                }
            },
            new Migration(6,7) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 6 TO 7");
                    // contacts
                    database.execSQL("ALTER TABLE `contact_table` RENAME TO  `old_contact_table`;");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `contact_table` (`bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `device_count` INTEGER NOT NULL, `established_channel_count` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("DROP INDEX `index_contact_table_direct_discussion_id`");
                    database.execSQL("DROP INDEX `index_contact_table_bytes_owned_identity`");
                    database.execSQL("CREATE  INDEX `index_contact_table_bytes_owned_identity` ON `contact_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE  INDEX `index_contact_table_display_name` ON `contact_table` (`display_name`)");
                    database.execSQL("INSERT INTO `contact_table` SELECT `bytes_identity`, `bytes_owned_identity`, `display_name`, `device_count`, `established_channel_count` FROM `old_contact_table`");

                    // group
                    database.execSQL("CREATE TABLE IF NOT EXISTS `group_table` (`group_id` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `name` TEXT NOT NULL, `bytes_group_owner_identity` BLOB, PRIMARY KEY(`group_id`, `bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )");
                    database.execSQL("CREATE  INDEX `index_group_table_name` ON `group_table` (`name`)");
                    database.execSQL("CREATE  INDEX `index_group_table_bytes_owned_identity` ON `group_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity` ON `group_table` (`bytes_group_owner_identity`, `bytes_owned_identity`)");
                    try (Cursor cursor = database.query("SELECT `group_id`, `bytes_owned_identity`, `title` FROM `discussion_table` WHERE `group_id` NOT NULL")) {
                        while (cursor.moveToNext()) {
                            byte[] groupId = cursor.getBlob(0);
                            byte[] bytesOwnedIdentity = cursor.getBlob(1);
                            String name = cursor.getString(2);
                            byte[] bytesGroupOwnerIdentity = Arrays.copyOfRange(groupId, 0, groupId.length - 32);
                            try (Cursor cursor1 = database.query("SELECT * FROM `contact_table` WHERE `bytes_contact_identity` = ? AND `bytes_owned_identity` = ?", new Object[]{bytesGroupOwnerIdentity, bytesOwnedIdentity})) {
                                if (!cursor1.moveToFirst()) {
                                    bytesGroupOwnerIdentity = null;
                                }
                            }
                            database.execSQL("INSERT INTO `group_table` VALUES (?,?,?,?)", new Object[]{
                                    groupId,
                                    bytesOwnedIdentity,
                                    name,
                                    bytesGroupOwnerIdentity
                            });
                        }
                    }

                    // contact group join
                    database.execSQL("CREATE TABLE IF NOT EXISTS `contact_group_join` (`group_id` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`group_id`, `bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`group_id`, `bytes_owned_identity`) REFERENCES `group_table`(`group_id`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE  INDEX `index_contact_group_join_group_id_bytes_owned_identity` ON `contact_group_join` (`group_id`, `bytes_owned_identity`)");
                    database.execSQL("CREATE  INDEX `index_contact_group_join_bytes_contact_identity_bytes_owned_identity` ON `contact_group_join` (`bytes_contact_identity`, `bytes_owned_identity`)");
                    database.execSQL("INSERT INTO `contact_group_join` SELECT discussion.`group_id`, `contact_bytes_identity`, `contact_bytes_owned_identity`, `timestamp` FROM `contact_discussion_join` INNER JOIN `discussion_table` AS discussion ON discussion.id = `discussion_id` WHERE discussion.`group_id` NOT NULL");

                    // pending group member
                    database.execSQL("CREATE TABLE IF NOT EXISTS `pending_group_member_table` (`bytes_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `group_id` BLOB NOT NULL, PRIMARY KEY(`bytes_identity`, `bytes_owned_identity`, `group_id`), FOREIGN KEY(`group_id`, `bytes_owned_identity`) REFERENCES `group_table`(`group_id`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE  INDEX `index_pending_group_member_table_group_id_bytes_owned_identity` ON `pending_group_member_table` (`group_id`, `bytes_owned_identity`)");

                    // discussion
                    database.execSQL("ALTER TABLE `discussion_table` RENAME TO  `old_discussion_table`;");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `discussion_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `bytes_owned_identity` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `last_outbound_message_sequence_number` INTEGER NOT NULL, `group_id` BLOB, `bytes_contact_identity` BLOB, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`group_id`, `bytes_owned_identity`) REFERENCES `group_table`(`group_id`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE SET NULL )");
                    database.execSQL("DROP INDEX `index_discussion_table_bytes_owned_identity`");
                    database.execSQL("DROP INDEX `index_discussion_table_group_id`");
                    database.execSQL("DROP INDEX `index_discussion_table_title`");
                    database.execSQL("CREATE  INDEX `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity` ON `discussion_table` (`bytes_contact_identity`, `bytes_owned_identity`)");
                    database.execSQL("CREATE UNIQUE INDEX `index_discussion_table_group_id_bytes_owned_identity` ON `discussion_table` (`group_id`, `bytes_owned_identity`)");
                    database.execSQL("CREATE  INDEX `index_discussion_table_title` ON `discussion_table` (`title`)");
                    database.execSQL("INSERT INTO `discussion_table` SELECT `id`, `title`, `bytes_owned_identity`, `own_sender_identifier`, `last_outbound_message_sequence_number`, `group_id`, NULL FROM `old_discussion_table` WHERE `group_id` NOT NULL");
                    database.execSQL("INSERT INTO `discussion_table` SELECT `id`, `title`, `bytes_owned_identity`, `own_sender_identifier`, `last_outbound_message_sequence_number`, NULL, `contact_bytes_identity` FROM `old_discussion_table` INNER JOIN `contact_discussion_join` ON `id` = `discussion_id` WHERE `group_id` IS NULL");

                    // fyle_message_join_with_status
                    database.execSQL("ALTER TABLE `fyle_message_join_with_status` RENAME TO  `old_fyle_message_join_with_status`");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `fyle_message_join_with_status` (`fyle_id` INTEGER NOT NULL, `message_id` INTEGER NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `file_path` TEXT NOT NULL, `file_name` TEXT NOT NULL, `file_type` TEXT, `status` INTEGER NOT NULL, `size` INTEGER NOT NULL, `progress` REAL NOT NULL, `engine_message_identifier` BLOB, `engine_number` INTEGER, PRIMARY KEY(`fyle_id`, `message_id`), FOREIGN KEY(`fyle_id`) REFERENCES `fyle_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )");
                    database.execSQL("DROP INDEX `index_fyle_message_join_with_status_fyle_id`");
                    database.execSQL("DROP INDEX `index_fyle_message_join_with_status_message_id`");
                    database.execSQL("DROP INDEX `index_fyle_message_join_with_status_engine_message_identifier_engine_number`");
                    database.execSQL("DROP INDEX `index_fyle_message_join_with_status_bytes_owned_identity`");
                    database.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_fyle_id` ON `fyle_message_join_with_status` (`fyle_id`)");
                    database.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_message_id` ON `fyle_message_join_with_status` (`message_id`)");
                    database.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_engine_message_identifier_engine_number` ON `fyle_message_join_with_status` (`engine_message_identifier`, `engine_number`)");
                    database.execSQL("CREATE  INDEX `index_fyle_message_join_with_status_bytes_owned_identity` ON `fyle_message_join_with_status` (`bytes_owned_identity`)");
                    database.execSQL("INSERT INTO `fyle_message_join_with_status` SELECT * FROM  `old_fyle_message_join_with_status`");

                    // invitation
                    database.execSQL("ALTER TABLE `invitation_table` RENAME TO `old_invitation_table`");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `invitation_table` (`dialog_uuid` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `associated_dialog` BLOB NOT NULL, `contact_identity` BLOB, `progress` INTEGER NOT NULL, `invitation_timestamp` INTEGER NOT NULL, PRIMARY KEY(`dialog_uuid`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("DROP INDEX `index_invitation_table_bytes_owned_identity`");
                    database.execSQL("CREATE  INDEX `index_invitation_table_bytes_owned_identity` ON `invitation_table` (`bytes_owned_identity`)");
                    database.execSQL("INSERT INTO `invitation_table` SELECT * FROM  `old_invitation_table`");

                    // identity_table
                    database.execSQL("ALTER TABLE `identity_table` RENAME TO `old_identity_table`");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `identity_table` (`bytes_owned_identity` BLOB NOT NULL, `obv_owned_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `api_key_status` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`))");
                    database.execSQL("INSERT INTO `identity_table` SELECT * FROM  `old_identity_table`");

                    // anonymizer
                    database.execSQL("ALTER TABLE `anonymizer_for_owned_identity` RENAME TO `old_anonymizer_for_owned_identity`");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `anonymizer_for_owned_identity` (`bytes_owned_identity` BLOB NOT NULL, `pseudo` TEXT NOT NULL, PRIMARY KEY(`bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("INSERT INTO `anonymizer_for_owned_identity` SELECT * FROM `old_anonymizer_for_owned_identity`");

                    // message
                    database.execSQL("CREATE  INDEX `index_message_table_discussion_id_status` ON `message_table` (`discussion_id`, `status`)");

                    database.execSQL("DROP TABLE `old_contact_table`");
                    database.execSQL("DROP TABLE `contact_discussion_join`");
                    database.execSQL("DROP TABLE `old_discussion_table`");
                    database.execSQL("DROP TABLE `old_fyle_message_join_with_status`");
                    database.execSQL("DROP TABLE `old_invitation_table`");
                    database.execSQL("DROP TABLE `old_identity_table`");
                    database.execSQL("DROP TABLE `old_anonymizer_for_owned_identity`");
                }
            },
            new Migration(7,8) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 7 TO 8");

                    database.execSQL("ALTER TABLE `identity_table` RENAME TO  `old_identity_table`");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `identity_table` (`bytes_owned_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `api_key_status` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`))");
                    database.execSQL("INSERT INTO `identity_table` SELECT `bytes_owned_identity`, `display_name`, `api_key_status` FROM `old_identity_table`");
                    database.execSQL("DROP TABLE `old_identity_table`");
                }
            },
            new Migration(8,9) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 8 TO 9");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_reply` TEXT DEFAULT NULL");
                }
            },
            new Migration(9,10) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 9 TO 10");
                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `custom_display_name` TEXT DEFAULT NULL");
                    database.execSQL("CREATE  INDEX `index_contact_table_custom_display_name` ON `contact_table` (`custom_display_name`)");
                }
            },
            new Migration(10,11) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 10 TO 11");
                    database.execSQL("ALTER TABLE `contact_table` RENAME TO `old_contact_table`");
                    database.execSQL("ALTER TABLE `invitation_table` RENAME TO `old_invitation_table`");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `contact_table` (`bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_display_name` TEXT, `display_name` TEXT NOT NULL, `identity_details` TEXT NOT NULL, `device_count` INTEGER NOT NULL, `established_channel_count` INTEGER NOT NULL, PRIMARY KEY(`bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("DROP  INDEX `index_contact_table_bytes_owned_identity`");
                    database.execSQL("DROP  INDEX `index_contact_table_display_name`");
                    database.execSQL("DROP  INDEX `index_contact_table_custom_display_name`");
                    database.execSQL("CREATE  INDEX `index_contact_table_bytes_owned_identity` ON `contact_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE  INDEX `index_contact_table_display_name` ON `contact_table` (`display_name`)");
                    database.execSQL("CREATE  INDEX `index_contact_table_custom_display_name` ON `contact_table` (`custom_display_name`)");
                    database.execSQL("INSERT INTO `contact_table` SELECT `bytes_contact_identity`, `bytes_owned_identity`, `custom_display_name`, `display_name`, ('{\"first_name\":\"' || `display_name` || '\"}'), `device_count`, `established_channel_count` FROM `old_contact_table`");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `invitation_table` (`dialog_uuid` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `associated_dialog` BLOB NOT NULL, `invitation_timestamp` INTEGER NOT NULL, PRIMARY KEY(`dialog_uuid`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("DROP  INDEX `index_invitation_table_bytes_owned_identity`");
                    database.execSQL("CREATE  INDEX `index_invitation_table_bytes_owned_identity` ON `invitation_table` (`bytes_owned_identity`)");

                    database.execSQL("DROP TABLE `old_contact_table`");
                    database.execSQL("DROP TABLE `old_invitation_table`");
                }
            },
            new Migration(11,12) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 11 TO 12");
                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `new_published_details` INTEGER NOT NULL DEFAULT 0");
                }
            },
            new Migration(12,13) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 12 TO 13");

                    // groups
                    database.execSQL("ALTER TABLE group_table RENAME TO old_group_table");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `group_table` (`bytes_group_owner_and_uid` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_name` TEXT, `name` TEXT NOT NULL, `new_published_details` INTEGER NOT NULL, `bytes_group_owner_identity` BLOB, `leaving` INTEGER NOT NULL, PRIMARY KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )");

                    try (Cursor cursor = database.query("SELECT `group_id`, `bytes_owned_identity`, `name`, `bytes_group_owner_identity` FROM `old_group_table`")) {
                        database.execSQL("DROP  INDEX `index_group_table_name`");
                        database.execSQL("DROP  INDEX `index_group_table_bytes_owned_identity`");
                        database.execSQL("DROP  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity`");

                        database.execSQL("CREATE  INDEX `index_group_table_name` ON `group_table` (`name`)");
                        database.execSQL("CREATE  INDEX `index_group_table_bytes_owned_identity` ON `group_table` (`bytes_owned_identity`)");
                        database.execSQL("CREATE  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity` ON `group_table` (`bytes_group_owner_identity`, `bytes_owned_identity`)");

                        while (cursor.moveToNext()) {
                            byte[] groupId = cursor.getBlob(0);
                            byte[] bytesOwnedIdentity = cursor.getBlob(1);
                            String name = cursor.getString(2);
                            byte[] bytesGroupOwnerIdentity = cursor.getBlob(3);

                            database.execSQL("INSERT INTO `group_table` VALUES (?,?,?,?,?,?,?)", new Object[]{
                                    groupId,
                                    bytesOwnedIdentity,
                                    null,
                                    name,
                                    0,
                                    bytesGroupOwnerIdentity,
                                    0,
                            });
                        }
                    }


                    // contact group join
                    database.execSQL("ALTER TABLE contact_group_join RENAME TO old_contact_group_join");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `contact_group_join` (`bytes_group_owner_and_uid` BLOB NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`, `bytes_contact_identity`), FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");

                    try (Cursor cursor = database.query("SELECT `group_id`, `bytes_contact_identity`, `bytes_owned_identity`, `timestamp` FROM `old_contact_group_join`")) {
                        database.execSQL("DROP  INDEX `index_contact_group_join_group_id_bytes_owned_identity`");
                        database.execSQL("DROP  INDEX `index_contact_group_join_bytes_contact_identity_bytes_owned_identity`");

                        database.execSQL("CREATE  INDEX `index_contact_group_join_bytes_group_owner_and_uid_bytes_owned_identity` ON `contact_group_join` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)");
                        database.execSQL("CREATE  INDEX `index_contact_group_join_bytes_contact_identity_bytes_owned_identity` ON `contact_group_join` (`bytes_contact_identity`, `bytes_owned_identity`)");

                        while (cursor.moveToNext()) {
                            byte[] groupId = cursor.getBlob(0);
                            byte[] bytesContactIdentity = cursor.getBlob(1);
                            byte[] bytesOwnedIdentity = cursor.getBlob(2);
                            long timestamp = cursor.getLong(3);

                            database.execSQL("INSERT INTO `contact_group_join` VALUES (?,?,?,?)", new Object[]{
                                    groupId,
                                    bytesContactIdentity,
                                    bytesOwnedIdentity,
                                    timestamp
                            });
                        }
                    }


                    // pending group members
                    database.execSQL("DROP TABLE pending_group_member_table");

                    database.execSQL("CREATE TABLE IF NOT EXISTS `pending_group_member_table` (`bytes_identity` BLOB NOT NULL, `display_name` TEXT NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `bytes_group_owner_and_uid` BLOB NOT NULL, `declined` INTEGER NOT NULL, PRIMARY KEY(`bytes_identity`, `bytes_owned_identity`, `bytes_group_owner_and_uid`), FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE  INDEX `index_pending_group_member_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `pending_group_member_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)");


                    // discussion
                    database.execSQL("ALTER TABLE discussion_table RENAME TO old_discussion_table");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `discussion_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `bytes_owned_identity` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `last_outbound_message_sequence_number` INTEGER NOT NULL, `bytes_group_owner_and_uid` BLOB, `bytes_contact_identity` BLOB, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )");

                    try (Cursor cursor = database.query("SELECT `id`, `title`, `bytes_owned_identity`, `sender_thread_identifier`, `last_outbound_message_sequence_number`, `group_id`, `bytes_contact_identity` FROM `old_discussion_table`")) {
                        database.execSQL("DROP  INDEX `index_discussion_table_bytes_owned_identity`");
                        database.execSQL("DROP  INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity`");
                        database.execSQL("DROP  INDEX `index_discussion_table_group_id_bytes_owned_identity`");
                        database.execSQL("DROP  INDEX `index_discussion_table_title`");

                        database.execSQL("CREATE  INDEX `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)");
                        database.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity` ON `discussion_table` (`bytes_contact_identity`, `bytes_owned_identity`)");
                        database.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `discussion_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)");
                        database.execSQL("CREATE  INDEX `index_discussion_table_title` ON `discussion_table` (`title`)");

                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(0);
                            String title = cursor.getString(1);
                            byte[] bytesOwnedIdentity = cursor.getBlob(2);
                            String senderThreadIdentifier = cursor.getString(3);
                            long lastOutbound = cursor.getLong(4);
                            byte[] groupId = cursor.getBlob(5);
                            byte[] bytesContactIdentity = cursor.getBlob(6);

                            if (groupId != null) {
                                database.execSQL("INSERT INTO `discussion_table` VALUES (?,?,?,?,?,?,?)", new Object[]{
                                        id,
                                        title,
                                        bytesOwnedIdentity,
                                        senderThreadIdentifier,
                                        lastOutbound,
                                        groupId,
                                        bytesContactIdentity,
                                });
                            } else {
                                database.execSQL("INSERT INTO `discussion_table` VALUES (?,?,?,?,?,?,?)", new Object[]{
                                        id,
                                        title,
                                        bytesOwnedIdentity,
                                        senderThreadIdentifier,
                                        lastOutbound,
                                        null,
                                        bytesContactIdentity,
                                });
                            }
                        }
                    }

                    // clear invitations
                    database.execSQL("DELETE FROM `invitation_table`");


                    database.execSQL("DROP TABLE `old_discussion_table`");
                    database.execSQL("DROP TABLE `old_contact_group_join`");
                    database.execSQL("DROP TABLE `old_group_table`");
                }
            },
            new Migration(13,14) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 13 TO 14");
                    database.execSQL("ALTER TABLE group_table RENAME TO old_group_table");
                    database.execSQL( "CREATE TABLE IF NOT EXISTS `group_table` (`bytes_group_owner_and_uid` BLOB NOT NULL, `bytes_owned_identity` BLOB NOT NULL, `custom_name` TEXT, `name` TEXT NOT NULL, `new_published_details` INTEGER NOT NULL, `bytes_group_owner_identity` BLOB, PRIMARY KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`), FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_group_owner_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )");

                    database.execSQL("DROP  INDEX `index_group_table_name`");
                    database.execSQL("DROP  INDEX `index_group_table_bytes_owned_identity`");
                    database.execSQL("DROP  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity`");

                    database.execSQL("CREATE  INDEX `index_group_table_name` ON `group_table` (`name`)");
                    database.execSQL("CREATE  INDEX `index_group_table_bytes_owned_identity` ON `group_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE  INDEX `index_group_table_bytes_group_owner_identity_bytes_owned_identity` ON `group_table` (`bytes_group_owner_identity`, `bytes_owned_identity`)");

                    database.execSQL("INSERT INTO `group_table` SELECT bytes_group_owner_and_uid, bytes_owned_identity, custom_name, name, new_published_details, bytes_group_owner_identity  FROM  `old_group_table`");

                    database.execSQL("DROP TABLE `old_group_table`");
                }
            },
            new Migration(14,15) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 14 TO 15");
                    database.execSQL("ALTER TABLE identity_table ADD COLUMN unpublished_details INTEGER NOT NULL DEFAULT 0");
                }
            },
            new Migration(15,16) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 15 TO 16");
                    database.execSQL("ALTER TABLE discussion_table RENAME TO old_discussion_table");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `discussion_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `bytes_owned_identity` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `last_outbound_message_sequence_number` INTEGER NOT NULL, `bytes_group_owner_and_uid` BLOB, `bytes_contact_identity` BLOB, FOREIGN KEY(`bytes_owned_identity`) REFERENCES `identity_table`(`bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bytes_contact_identity`, `bytes_owned_identity`) REFERENCES `contact_table`(`bytes_contact_identity`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`bytes_group_owner_and_uid`, `bytes_owned_identity`) REFERENCES `group_table`(`bytes_group_owner_and_uid`, `bytes_owned_identity`) ON UPDATE NO ACTION ON DELETE NO ACTION )");

                    database.execSQL("DROP INDEX `index_discussion_table_bytes_owned_identity`");
                    database.execSQL("DROP INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity`");
                    database.execSQL("DROP INDEX `index_discussion_table_bytes_group_owner_and_uid_bytes_owned_identity`");
                    database.execSQL("DROP INDEX `index_discussion_table_title`");

                    database.execSQL("CREATE  INDEX `index_discussion_table_bytes_owned_identity` ON `discussion_table` (`bytes_owned_identity`)");
                    database.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_contact_identity_bytes_owned_identity` ON `discussion_table` (`bytes_contact_identity`, `bytes_owned_identity`)");
                    database.execSQL("CREATE UNIQUE INDEX `index_discussion_table_bytes_group_owner_and_uid_bytes_owned_identity` ON `discussion_table` (`bytes_group_owner_and_uid`, `bytes_owned_identity`)");
                    database.execSQL("CREATE  INDEX `index_discussion_table_title` ON `discussion_table` (`title`)");

                    database.execSQL("INSERT INTO discussion_table SELECT * FROM old_discussion_table");
                    database.execSQL("DROP TABLE old_discussion_table");
                }
            },
            new Migration(16,17) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 16 TO 17");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `discussion_customization_table` (`discussion_id` INTEGER NOT NULL, `serialized_color_json` TEXT, PRIMARY KEY(`discussion_id`), FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                }
            },
            new Migration(17,18) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 17 TO 18");
                    database.execSQL("ALTER TABLE message_table RENAME TO old_message_table");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `message_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender_sequence_number` INTEGER NOT NULL, `content_body` TEXT, `json_reply` TEXT, `sort_index` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `status` INTEGER NOT NULL, `message_type` INTEGER NOT NULL, `discussion_id` INTEGER NOT NULL, `engine_message_identifier` BLOB, `sender_identifier` BLOB NOT NULL, `sender_thread_identifier` TEXT NOT NULL, `total_attachment_count` INTEGER NOT NULL, `image_count` INTEGER NOT NULL, FOREIGN KEY(`discussion_id`) REFERENCES `discussion_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");

                    database.execSQL("DROP INDEX `index_message_table_discussion_id`");
                    database.execSQL("DROP INDEX `index_message_table_engine_message_identifier`");
                    database.execSQL("DROP INDEX `index_message_table_sort_index`");
                    database.execSQL("DROP INDEX `index_message_table_timestamp`");
                    database.execSQL("DROP INDEX `index_message_table_message_type_status`");
                    database.execSQL("DROP INDEX `index_message_table_discussion_id_status`");
                    database.execSQL("DROP INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id`");

                    database.execSQL("CREATE INDEX `index_message_table_discussion_id` ON `message_table` (`discussion_id`)");
                    database.execSQL("CREATE INDEX `index_message_table_engine_message_identifier` ON `message_table` (`engine_message_identifier`)");
                    database.execSQL("CREATE INDEX `index_message_table_sort_index` ON `message_table` (`sort_index`)");
                    database.execSQL("CREATE INDEX `index_message_table_timestamp` ON `message_table` (`timestamp`)");
                    database.execSQL("CREATE INDEX `index_message_table_message_type_status` ON `message_table` (`message_type`, `status`)");
                    database.execSQL("CREATE INDEX `index_message_table_discussion_id_status` ON `message_table` (`discussion_id`, `status`)");
                    database.execSQL("CREATE INDEX `index_message_table_sender_sequence_number_sender_thread_identifier_sender_identifier_discussion_id` ON `message_table` (`sender_sequence_number`, `sender_thread_identifier`, `sender_identifier`, `discussion_id`)");

                    database.execSQL("INSERT INTO message_table " +
                            " SELECT m.id, m.sender_sequence_number, m.content_body, m.json_reply, m.sort_index, m.timestamp, m.status, m.message_type, m.discussion_id, m.engine_message_identifier, m.sender_identifier, m.sender_thread_identifier, IFNULL(attachment_count,0), IFNULL(image_count,0) " +
                            " FROM old_message_table AS m " +
                            " LEFT JOIN (SELECT message_id AS mid_un, COUNT(*) AS attachment_count FROM fyle_message_join_with_status GROUP BY message_id)" +
                            " ON m.id = mid_un" +
                            " LEFT JOIN (SELECT message_id AS mid_deux, COUNT(*) AS image_count FROM fyle_message_join_with_status WHERE file_type LIKE 'image/%' OR file_type LIKE 'video/%' GROUP BY message_id)" +
                            " ON m.id = mid_deux");

                    database.execSQL("DROP TABLE old_message_table");
                }
            },
            new Migration(18,19) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 18 TO 19");
                    database.execSQL("ALTER TABLE discussion_table ADD COLUMN last_message_timestamp INTEGER NOT NULL DEFAULT 0");
                    database.execSQL("UPDATE discussion_table SET last_message_timestamp = IFNULL((SELECT MAX(timestamp) FROM message_table WHERE message_table.discussion_id = discussion_table.id), 0)");
                }
            },
            new Migration(19,20) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 19 TO 20");
                    database.execSQL("CREATE TABLE IF NOT EXISTS `message_recipient_info_table` (`message_id` INTEGER NOT NULL, `bytes_contact_identity` BLOB NOT NULL, `return_receipt_nonce` BLOB, `return_receipt_key` BLOB, `engine_message_identifier` BLOB, `timestamp_sent` INTEGER, `timestamp_delivered` INTEGER, `timestamp_read` INTEGER, PRIMARY KEY(`message_id`, `bytes_contact_identity`), FOREIGN KEY(`message_id`) REFERENCES `message_table`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                    database.execSQL("CREATE  INDEX `index_message_recipient_info_table_engine_message_identifier` ON `message_recipient_info_table` (`engine_message_identifier`)");
                    database.execSQL("CREATE  INDEX `index_message_recipient_info_table_bytes_contact_identity` ON `message_recipient_info_table` (`bytes_contact_identity`)");
                    database.execSQL("CREATE  INDEX `index_message_recipient_info_table_message_id` ON `message_recipient_info_table` (`message_id`)");
                    database.execSQL("CREATE  INDEX `index_message_recipient_info_table_return_receipt_nonce` ON `message_recipient_info_table` (`return_receipt_nonce`)");
                    database.execSQL("CREATE  INDEX `index_message_recipient_info_table_message_id_bytes_contact_identity` ON `message_recipient_info_table` (`message_id`, `bytes_contact_identity`)");
                }
            },
            new Migration(20,21) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 20 TO 21");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `background_image_url` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_send_read_receipt` INTEGER DEFAULT NULL");
                }
            },
            new Migration(21,22) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 21 TO 22");
                    database.execSQL("ALTER TABLE `message_table` ADD COLUMN `json_return_receipt` TEXT DEFAULT NULL");
                }
            },
            new Migration(22,23) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 22 TO 23");
                    database.execSQL("ALTER TABLE `identity_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `contact_table` ADD COLUMN `custom_photo_url` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `group_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `group_table` ADD COLUMN `custom_photo_url` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_table` ADD COLUMN `photo_url` TEXT DEFAULT NULL");
                    database.execSQL("ALTER TABLE `discussion_customization_table` ADD COLUMN `pref_mute_notifications` INTEGER NOT NULL DEFAULT 0");
                }
            },
            new Migration(23,24) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    Logger.w("ROOM MIGRATING FROM VERSION 23 TO 24");

                    Pattern pattern = Pattern.compile("^.*/(discussion_backgrounds/[0-9]+-[0-9]+)$");
                    try (Cursor cursor = database.query("SELECT `discussion_id`, `background_image_url` FROM `discussion_customization_table`")) {
                        while (cursor.moveToNext()) {
                            long discussionId = cursor.getLong(0);
                            String imageUrl = cursor.getString(1);
                            if (imageUrl != null) {
                                Matcher m = pattern.matcher(imageUrl);
                                String newUrl = null;
                                if (m.find()) {
                                    newUrl = m.group(1);
                                }
                                database.execSQL("UPDATE `discussion_customization_table` SET `background_image_url` = ? WHERE `discussion_id` = ?", new Object[]{newUrl, discussionId});
                            }
                        }
                    }

                    pattern = Pattern.compile("^.*/(fyles/[0-9A-F]+)$");
                    try (Cursor cursor = database.query("SELECT `sha256`, `permanent_file_path` FROM `fyle_table`")) {
                        while (cursor.moveToNext()) {
                            byte[] sha256 = cursor.getBlob(0);
                            String filePath = cursor.getString(1);
                            if (filePath != null) {
                                Matcher m = pattern.matcher(filePath);
                                String newPath = null;
                                if (m.find()) {
                                    newPath = m.group(1);
                                }
                                database.execSQL("UPDATE `fyle_table` SET `permanent_file_path` = ? WHERE `sha256` = ?", new Object[]{newPath, sha256});
                            }
                        }
                    }

                    pattern = Pattern.compile("^.*/(fyles/[0-9A-F]+)$");
                    Pattern patternIncomplete = Pattern.compile("^.*/inbound_attachments/(.+)@([0-9A-F]+)-([0-9A-F]+)-([0-9A-F]+/[0-9]+)$");
                    Hash sha256 = Suite.getHash(Hash.SHA256);
                    try (Cursor cursor = database.query("SELECT `fyle_id`, `message_id`, `file_path` FROM `fyle_message_join_with_status`")) {
                        while (cursor.moveToNext()) {
                            long fileId = cursor.getLong(0);
                            long messageId = cursor.getLong(1);
                            String filePath = cursor.getString(2);
                            Matcher m = pattern.matcher(filePath);
                            if (m.find()) {
                                String newPath = m.group(1);
                                database.execSQL("UPDATE `fyle_message_join_with_status` SET `file_path` = ? WHERE `fyle_id` = ? AND `message_id` = ?", new Object[]{newPath, fileId, messageId});
                            } else {
                                m = patternIncomplete.matcher(filePath);
                                if (m.find()) {
                                    try {
                                        //noinspection ConstantConditions
                                        byte[] serverBytes = (m.group(1).replace("https:/", "https://")).getBytes(StandardCharsets.UTF_8);
                                        //noinspection ConstantConditions
                                        byte[] compactAuthKey = Logger.fromHexString(m.group(2));
                                        //noinspection ConstantConditions
                                        byte[] compactEncKey = Logger.fromHexString(m.group(3));
                                        String suffix = m.group(4);

                                        byte[] identityBytes = new byte[serverBytes.length + 1 + compactAuthKey.length + compactEncKey.length];
                                        System.arraycopy(serverBytes, 0, identityBytes, 0, serverBytes.length);
                                        identityBytes[serverBytes.length] = (byte) 0x00;
                                        System.arraycopy(compactAuthKey, 0, identityBytes, serverBytes.length + 1, compactAuthKey.length);
                                        System.arraycopy(compactEncKey, 0, identityBytes, serverBytes.length + 1 + compactAuthKey.length, compactEncKey.length);
                                        byte[] uid = sha256.digest(identityBytes);
                                        String newPath = "inbound_attachments/" + Logger.toHexString(uid) + "-" + suffix;

                                        database.execSQL("UPDATE `fyle_message_join_with_status` SET `file_path` = ? WHERE `fyle_id` = ? AND `message_id` = ?", new Object[]{newPath, fileId, messageId});
                                    } catch (Exception e) {
                                        database.execSQL("DELETE FROM `fyle_message_join_with_status` WHERE `fyle_id` = ? AND `message_id` = ?", new Object[]{fileId, messageId});
                                    }
                                } else {
                                    database.execSQL("DELETE FROM `fyle_message_join_with_status` WHERE `fyle_id` = ? AND `message_id` = ?", new Object[]{fileId, messageId});
                                }
                            }
                        }
                    }

                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_discussion_customization_table_discussion_id` ON `discussion_customization_table` (`discussion_id`)");
                }
            },

    };
}
