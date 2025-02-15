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

package io.olvid.engine.datatypes.containers;


import io.olvid.engine.datatypes.Identity;
import io.olvid.engine.datatypes.UID;

public class DialogType {
    public static final int DELETE_DIALOG_ID = -1;
    public static final int INVITE_SENT_DIALOG_ID = 0;
    public static final int ACCEPT_INVITE_DIALOG_ID = 1;
    public static final int SAS_EXCHANGE_DIALOG_ID = 2;
    public static final int SAS_CONFIRMED_DIALOG_ID = 3;
    public static final int MUTUAL_TRUST_CONFIRMED_DIALOG_ID = 4;
    public static final int INVITE_ACCEPTED_DIALOG_ID = 5;
    public static final int ACCEPT_MEDIATOR_INVITE_DIALOG_ID = 6;
    public static final int MEDIATOR_INVITE_ACCEPTED_DIALOG_ID = 7;
    public static final int ACCEPT_GROUP_INVITE_DIALOG_ID = 8;
    public static final int INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_ID = 9;
    public static final int INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_ID = 10;
    public static final int AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_ID = 11;
    public static final int GROUP_JOINED_DIALOG_ID = 12;

    public final int id;
    public final String contactDisplayNameOrSerializedDetails;
    public final Identity contactIdentity;
    public final byte[] sasToDisplay;
    public final byte[] sasEntered;
    public final Identity mediatorOrGroupOwnerIdentity;
    public final String serializedGroupDetails;
    public final UID groupUid;
    public final Identity[] pendingGroupMemberIdentities;
    public final String[] pendingGroupMemberSerializedDetails;
    public final Long serverTimestamp;

    private DialogType(int id, String contactDisplayNameOrSerializedDetails, Identity contactIdentity, byte[] sasToDisplay, byte[] sasEntered, Identity mediatorOrGroupOwnerIdentity, String serializedGroupDetails, UID groupUid, Identity[] pendingGroupMemberIdentities, String[] pendingGroupMemberSerializedDetails, Long serverTimestamp) {
        this.id = id;
        this.contactDisplayNameOrSerializedDetails = contactDisplayNameOrSerializedDetails;
        this.contactIdentity = contactIdentity;
        this.sasToDisplay = sasToDisplay;
        this.sasEntered = sasEntered;
        this.mediatorOrGroupOwnerIdentity = mediatorOrGroupOwnerIdentity;
        this.serializedGroupDetails = serializedGroupDetails;
        this.groupUid = groupUid;
        this.pendingGroupMemberIdentities = pendingGroupMemberIdentities;
        this.pendingGroupMemberSerializedDetails = pendingGroupMemberSerializedDetails;
        this.serverTimestamp = serverTimestamp;
    }

    public static DialogType createDeleteDialog() {
        return new DialogType(DELETE_DIALOG_ID, null, null, null, null, null, null, null, null, null, null);
    }

    public static DialogType createInviteSentDialog(String contactDisplayName, Identity contactIdentity) {
        return new DialogType(INVITE_SENT_DIALOG_ID, contactDisplayName, contactIdentity, null, null, null, null, null, null, null, null);
    }

    public static DialogType createAcceptInviteDialog(String contactSerializedDetails, Identity contactIdentity, long serverTimestamp) {
        return new DialogType(ACCEPT_INVITE_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, null, null, null, null, null, serverTimestamp);
    }

    public static DialogType createSasExchangeDialog(String contactSerializedDetails, Identity contactIdentity, byte[] sasToDisplay, long serverTimestamp) {
        return new DialogType(SAS_EXCHANGE_DIALOG_ID, contactSerializedDetails, contactIdentity, sasToDisplay, null, null, null, null, null, null, serverTimestamp);
    }

    public static DialogType createSasConfirmedDialog(String contactSerializedDetails, Identity contactIdentity, byte[] sasToDisplay, byte[] sasEntered) {
        return new DialogType(SAS_CONFIRMED_DIALOG_ID, contactSerializedDetails, contactIdentity, sasToDisplay, sasEntered, null, null, null, null, null, null);
    }

    public static DialogType createMutualTrustConfirmedDialog(String contactSerializedDetails, Identity contactIdentity) {
        return new DialogType(MUTUAL_TRUST_CONFIRMED_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, null, null, null, null, null, null);
    }

    public static DialogType createInviteAcceptedDialog(String contactSerializedDetails, Identity contactIdentity) {
        return new DialogType(INVITE_ACCEPTED_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, null, null, null, null, null, null);
    }

    public static DialogType createAcceptMediatorInviteDialog(String contactSerializedDetails, Identity contactIdentity, Identity mediatorIdentity, long serverTimestamp) {
        return new DialogType(ACCEPT_MEDIATOR_INVITE_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, mediatorIdentity, null, null, null, null, serverTimestamp);
    }

    public static DialogType createMediatorInviteAcceptedDialog(String contactSerializedDetails, Identity contactIdentity, Identity mediatorIdentity) {
        return new DialogType(MEDIATOR_INVITE_ACCEPTED_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, mediatorIdentity, null, null, null, null, null);
    }

    public static DialogType createAcceptGroupInviteDialog(String serializedGroupDetails, UID groupUid, Identity groupOwnerIdentity, Identity[] pendingGroupMemberIdentities, String[]  pendingGroupMemberSerializedDetails, long serverTimestamp) {
        return new DialogType(ACCEPT_GROUP_INVITE_DIALOG_ID, null, null, null, null, groupOwnerIdentity, serializedGroupDetails, groupUid, pendingGroupMemberIdentities, pendingGroupMemberSerializedDetails, serverTimestamp);
    }

    public static DialogType createIncreaseMediatorTrustLevelDialog(String contactSerializedDetails, Identity contactIdentity, Identity mediatorIdentity, long serverTimestamp) {
        return new DialogType(INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, mediatorIdentity, null, null, null, null, serverTimestamp);
    }

    public static DialogType createIncreaseGroupOwnerTrustLevelDialog(String serializedGroupDetails, UID groupUid, Identity groupOwnerIdentity, Identity[] pendingGroupMemberIdentities, String[]  pendingGroupMemberSerializedDetails, long serverTimestamp) {
        return new DialogType(INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_ID, null, null, null, null, groupOwnerIdentity, serializedGroupDetails, groupUid, pendingGroupMemberIdentities, pendingGroupMemberSerializedDetails, serverTimestamp);
    }

    public static DialogType createAutoConfirmedContactIntroductionDialog(String contactSerializedDetails, Identity contactIdentity, Identity mediatorIdentity) {
        return new DialogType(AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_ID, contactSerializedDetails, contactIdentity, null, null, mediatorIdentity, null, null, null, null, null);
    }

    public static DialogType createGroupJoinedDialog(String serializedGroupDetails, UID groupUid, Identity groupOwnerIdentity) {
        return new DialogType(GROUP_JOINED_DIALOG_ID, null, null, null, null, groupOwnerIdentity, serializedGroupDetails, groupUid, null, null, null);
    }
}
