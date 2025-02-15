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

package io.olvid.messenger.databases.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.tasks.ContactDisplayNameFormatChangedTask;
import io.olvid.messenger.settings.SettingsActivity;

@SuppressWarnings("CanBeFinal")
@Entity(
        tableName = Contact.TABLE_NAME,
        primaryKeys = {Contact.BYTES_OWNED_IDENTITY, Contact.BYTES_CONTACT_IDENTITY},
        foreignKeys = {
                @ForeignKey(entity = OwnedIdentity.class,
                        parentColumns = OwnedIdentity.BYTES_OWNED_IDENTITY,
                        childColumns = Contact.BYTES_OWNED_IDENTITY,
                        onDelete = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {Contact.BYTES_OWNED_IDENTITY}),
                @Index(value = {Contact.DISPLAY_NAME}),
                @Index(value = {Contact.CUSTOM_DISPLAY_NAME}),
                @Index(value = {Contact.SORT_DISPLAY_NAME}),
        }
)
public class Contact {
    public static final String TABLE_NAME = "contact_table";

    public static final String BYTES_OWNED_IDENTITY = "bytes_owned_identity";
    public static final String BYTES_CONTACT_IDENTITY = "bytes_contact_identity";
    public static final String CUSTOM_DISPLAY_NAME = "custom_display_name";
    public static final String DISPLAY_NAME = "display_name";
    public static final String SORT_DISPLAY_NAME = "sort_display_name";
    public static final String FULL_SEARCH_DISPLAY_NAME = "full_search_display_name";
    public static final String IDENTITY_DETAILS = "identity_details";
    public static final String NEW_PUBLISHED_DETAILS = "new_published_details";
    public static final String DEVICE_COUNT = "device_count";
    public static final String ESTABLISHED_CHANNEL_COUNT = "established_channel_count";
    public static final String PHOTO_URL = "photo_url";
    public static final String CUSTOM_PHOTO_URL = "custom_photo_url"; // set to "" to remove the default photo_url, null to use the default
    public static final String KEYCLOAK_MANAGED = "keycloak_managed";
    public static final String CUSTOM_NAME_HUE = "custom_name_hue";
    public static final String PERSONAL_NOTE = "personal_note";
    public static final String ACTIVE = "active";

    public static final int PUBLISHED_DETAILS_NOTHING_NEW = 0;
    public static final int PUBLISHED_DETAILS_NEW_UNSEEN = 1;
    public static final int PUBLISHED_DETAILS_NEW_SEEN = 2;


    @ColumnInfo(name = BYTES_CONTACT_IDENTITY)
    @NonNull
    public byte[] bytesContactIdentity;

    @ColumnInfo(name = BYTES_OWNED_IDENTITY)
    @NonNull
    public byte[] bytesOwnedIdentity;

    @ColumnInfo(name = CUSTOM_DISPLAY_NAME)
    @Nullable
    public String customDisplayName;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @ColumnInfo(name = DISPLAY_NAME)
    @NonNull
    public String displayName;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @ColumnInfo(name = SORT_DISPLAY_NAME)
    @NonNull
    public byte[] sortDisplayName;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @ColumnInfo(name = FULL_SEARCH_DISPLAY_NAME)
    @NonNull
    public String fullSearchDisplayName;

    @ColumnInfo(name = IDENTITY_DETAILS)
    @Nullable
    public String identityDetails;

    @ColumnInfo(name = NEW_PUBLISHED_DETAILS)
    public int newPublishedDetails;

    @ColumnInfo(name = DEVICE_COUNT)
    public int deviceCount;

    @ColumnInfo(name = ESTABLISHED_CHANNEL_COUNT)
    public int establishedChannelCount;

    @ColumnInfo(name = PHOTO_URL)
    @Nullable
    public String photoUrl;

    @ColumnInfo(name = CUSTOM_PHOTO_URL)
    @Nullable
    public String customPhotoUrl;

    @ColumnInfo(name = KEYCLOAK_MANAGED)
    public boolean keycloakManaged;

    @ColumnInfo(name = CUSTOM_NAME_HUE)
    @Nullable
    public Integer customNameHue;

    @ColumnInfo(name = PERSONAL_NOTE)
    @Nullable
    public String personalNote;

    @ColumnInfo(name = ACTIVE)
    public boolean active;

    // Constructor required by Room
    public Contact(@NonNull byte[] bytesContactIdentity, @NonNull byte[] bytesOwnedIdentity, @Nullable String customDisplayName, @NonNull String displayName, @NonNull byte[] sortDisplayName, @NonNull String fullSearchDisplayName, @Nullable String identityDetails, int newPublishedDetails, int deviceCount, int establishedChannelCount, @Nullable String photoUrl, @Nullable String customPhotoUrl, boolean keycloakManaged, @Nullable Integer customNameHue, @Nullable String personalNote, boolean active) {
        this.bytesContactIdentity = bytesContactIdentity;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.customDisplayName = customDisplayName;
        this.displayName = displayName;
        this.sortDisplayName = sortDisplayName;
        this.fullSearchDisplayName = fullSearchDisplayName;
        this.identityDetails = identityDetails;
        this.newPublishedDetails = newPublishedDetails;
        this.deviceCount = deviceCount;
        this.establishedChannelCount = establishedChannelCount;
        this.photoUrl = photoUrl;
        this.customPhotoUrl = customPhotoUrl;
        this.keycloakManaged = keycloakManaged;
        this.customNameHue = customNameHue;
        this.personalNote = personalNote;
        this.active = active;
    }


    // Constructor used when inserting a new contact
    @Ignore
    public Contact(@NonNull byte[] bytesContactIdentity, @NonNull byte[] bytesOwnedIdentity, @NonNull JsonIdentityDetails identityDetails, boolean hasUntrustedPublishedDetails, @Nullable String photoUrl, boolean keycloakManaged, boolean active) throws Exception {
        this.bytesContactIdentity = bytesContactIdentity;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.customDisplayName = null;
        this.setIdentityDetailsAndDisplayName(identityDetails);
        if (hasUntrustedPublishedDetails) {
            this.newPublishedDetails = PUBLISHED_DETAILS_NEW_UNSEEN;
        } else {
            this.newPublishedDetails = PUBLISHED_DETAILS_NOTHING_NEW;
        }
        this.deviceCount = 0;
        this.establishedChannelCount = 0;
        this.photoUrl = photoUrl;
        this.customPhotoUrl = null;
        this.keycloakManaged = keycloakManaged;
        this.customNameHue = null;
        this.personalNote = null;
        this.active = active;
    }

    @NonNull
    private String computeFullSearchDisplayName(JsonIdentityDetails jsonIdentityDetails) {
        if (jsonIdentityDetails == null) {
            return this.customDisplayName == null ? "" : this.customDisplayName;
        } else {
            if (customDisplayName == null) {
                return App.unAccent(jsonIdentityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FOR_SEARCH, false));
            } else {
                return App.unAccent(customDisplayName + " " + jsonIdentityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FOR_SEARCH, false));
            }
        }
    }

    public void setIdentityDetailsAndDisplayName(@Nullable JsonIdentityDetails jsonIdentityDetails) throws Exception {
        if (jsonIdentityDetails == null) {
            this.identityDetails = null;
            this.displayName = "";
            this.sortDisplayName = new byte[0];
        } else {
            this.identityDetails = AppSingleton.getJsonObjectMapper().writeValueAsString(jsonIdentityDetails);
            this.displayName = jsonIdentityDetails.formatDisplayName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName());
            this.sortDisplayName = ContactDisplayNameFormatChangedTask.computeSortDisplayName(jsonIdentityDetails, this.customDisplayName, SettingsActivity.getSortContactsByLastName());
        }
        this.fullSearchDisplayName = computeFullSearchDisplayName(jsonIdentityDetails);
    }

    public void setCustomDisplayName(@Nullable String customDisplayName) {
        this.customDisplayName = customDisplayName;
        JsonIdentityDetails identityDetails = getIdentityDetails();
        if (identityDetails == null) {
            this.sortDisplayName = new byte[0];
        } else {
            this.sortDisplayName = ContactDisplayNameFormatChangedTask.computeSortDisplayName(identityDetails, customDisplayName, SettingsActivity.getSortContactsByLastName());
        }
        this.fullSearchDisplayName = computeFullSearchDisplayName(identityDetails);
    }

    @Nullable
    public JsonIdentityDetails getIdentityDetails() {
        if (identityDetails == null) {
            return null;
        }
        try {
            return AppSingleton.getJsonObjectMapper().readValue(identityDetails, JsonIdentityDetails.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return Arrays.equals(bytesContactIdentity, contact.bytesContactIdentity) &&
                Arrays.equals(bytesOwnedIdentity, contact.bytesOwnedIdentity);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(bytesContactIdentity);
        result = 31 * result + Arrays.hashCode(bytesOwnedIdentity);
        return result;
    }

    public String getCustomDisplayName() {
        if (customDisplayName == null) {
            return displayName;
        }
        return customDisplayName;
    }

    public String getCustomPhotoUrl() {
        if (customPhotoUrl == null) {
            return photoUrl;
        } else if (customPhotoUrl.length() == 0) {
            return null;
        }
        return customPhotoUrl;
    }


    public void delete() {
        AppDatabase db = AppDatabase.getInstance();
        db.runInTransaction(() -> {
            // get direct discussion
            Discussion discussion = db.discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
            if (discussion != null) {
                discussion.lockWithMessage(db);
            }
            // delete the contact
            db.contactDao().delete(this);

            // get all unsent (not passed to the engine because of a lack of channel) MessageRecipientInfo and delete them
            //  --> update message status accordingly
            List<MessageRecipientInfo> messageRecipientInfoList = db.messageRecipientInfoDao().getUnsentForContact(bytesOwnedIdentity, bytesContactIdentity);
            for (MessageRecipientInfo messageRecipientInfo: messageRecipientInfoList) {
                db.messageRecipientInfoDao().delete(messageRecipientInfo);

                Message message = db.messageDao().get(messageRecipientInfo.messageId);
                if (message != null) {
                    if (message.refreshOutboundStatus()) {
                        db.messageDao().updateStatus(message.id, message.status);
                    }
                }
            }
        });
    }
}
