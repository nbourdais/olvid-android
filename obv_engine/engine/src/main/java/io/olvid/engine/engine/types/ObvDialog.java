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

package io.olvid.engine.engine.types;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import io.olvid.engine.encoder.DecodingException;
import io.olvid.engine.encoder.Encoded;
import io.olvid.engine.engine.types.identities.ObvIdentity;

public class ObvDialog {
    private final UUID uuid;
    private final Encoded encodedElements;
    private final byte[] bytesOwnedIdentity;
    private final Category category;
    private Encoded encodedResponse;

    public UUID getUuid() {
        return uuid;
    }

    public Encoded getEncodedElements() {
        return encodedElements;
    }

    public byte[] getBytesOwnedIdentity() {
        return bytesOwnedIdentity;
    }

    public Encoded getEncodedResponse() {
        return encodedResponse;
    }

    public Category getCategory() {
        return category;
    }

    public ObvDialog(UUID uuid, Encoded encodedElements, byte[] bytesOwnedIdentity, Category category) {
        this.uuid = uuid;
        this.encodedElements = encodedElements;
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.category = category;
        this.encodedResponse = null;
    }

    public static ObvDialog of(Encoded encoded, ObjectMapper jsonObjectMapper) throws Exception {
        Encoded[] list = encoded.decodeList();
        if (list.length != 4) {
            throw new DecodingException();
        }
        return new ObvDialog(
                list[0].decodeUuid(),
                list[1],
                list[2].decodeBytes(),
                Category.of(list[3], jsonObjectMapper)
        );
    }

    public Encoded encode(ObjectMapper jsonObjectMapper) {
        return Encoded.of(new Encoded[]{
                Encoded.of(uuid),
                encodedElements,
                Encoded.of(bytesOwnedIdentity),
                category.encode(jsonObjectMapper)
        });
    }

    // region Dialog response setters

    public void setResponseToAcceptInvite(boolean acceptInvite) throws Exception {
        switch (this.category.id) {
            case Category.ACCEPT_INVITE_DIALOG_CATEGORY:
                encodedResponse = Encoded.of(acceptInvite);
                break;
            default:
                throw new Exception();
        }
    }

    public void setResponseToSasExchange(byte[] otherSas) throws Exception {
        switch (this.category.id) {
            case Category.SAS_EXCHANGE_DIALOG_CATEGORY:
                encodedResponse = Encoded.of(otherSas);
                break;
            default:
                throw new Exception();
        }
    }

    public void setResponseToAcceptMediatorInvite(boolean acceptInvite) throws Exception {
        switch (this.category.id) {
            case Category.ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                encodedResponse = Encoded.of(acceptInvite);
                break;
            default:
                throw new Exception();
        }
    }

    public void setResponseToAcceptGroupInvite(boolean acceptInvite) throws Exception {
        switch (this.category.id) {
            case Category.ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
            case Category.INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY:
                encodedResponse = Encoded.of(acceptInvite);
                break;
            default:
                throw new Exception();
        }
    }
    // endregion






    public static class Category {
        public static final int INVITE_SENT_DIALOG_CATEGORY = 0;
        public static final int ACCEPT_INVITE_DIALOG_CATEGORY = 1;
        public static final int SAS_EXCHANGE_DIALOG_CATEGORY = 2;
        public static final int SAS_CONFIRMED_DIALOG_CATEGORY = 3;
        public static final int MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY = 4;
        public static final int INVITE_ACCEPTED_DIALOG_CATEGORY = 5;
        public static final int ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY = 6;
        public static final int MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY = 7;
        public static final int ACCEPT_GROUP_INVITE_DIALOG_CATEGORY = 8;
        public static final int INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_CATEGORY = 9;
        public static final int INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY = 10;
        public static final int AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_CATEGORY = 11;
        public static final int GROUP_JOINED_DIALOG_CATEGORY = 12;

        private final int id;
        private final byte[] bytesContactIdentity;
        private final String contactDisplayNameOrSerializedDetails;
        private final byte[] sasToDisplay;
        private final byte[] sasEntered;
        private final byte[] bytesMediatorOrGroupOwnerIdentity;
        private final String serializedGroupDetails;
        private final byte[] bytesGroupUid;
        private final ObvIdentity[] pendingGroupMemberIdentities;
        public final Long serverTimestamp;


        private Category(int id, byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, byte[] sasToDisplay, byte[] sasEntered, byte[] bytesMediatorOrGroupOwnerIdentity, String serializedGroupDetails, byte[] bytesGroupUid, ObvIdentity[] pendingGroupMemberIdentities, Long serverTimestamp) {
            this.id = id;
            this.bytesContactIdentity = bytesContactIdentity;
            this.contactDisplayNameOrSerializedDetails = contactDisplayNameOrSerializedDetails;
            this.sasToDisplay = sasToDisplay;
            this.sasEntered = sasEntered;
            this.bytesMediatorOrGroupOwnerIdentity = bytesMediatorOrGroupOwnerIdentity;
            this.serializedGroupDetails = serializedGroupDetails;
            this.bytesGroupUid = bytesGroupUid;
            this.pendingGroupMemberIdentities = pendingGroupMemberIdentities;
            this.serverTimestamp = serverTimestamp;
        }

        public int getId() {
            return id;
        }


        public byte[] getSasToDisplay() {
            return sasToDisplay;
        }

        public String getSerializedGroupDetails() {
            return serializedGroupDetails;
        }

        public byte[] getBytesGroupUid() {
            return bytesGroupUid;
        }

        public byte[] getBytesGroupOwnerAndUid() {
            byte[] out = new byte[bytesMediatorOrGroupOwnerIdentity.length + bytesGroupUid.length];
            System.arraycopy(bytesMediatorOrGroupOwnerIdentity, 0, out, 0, bytesMediatorOrGroupOwnerIdentity.length);
            System.arraycopy(bytesGroupUid, 0, out, bytesMediatorOrGroupOwnerIdentity.length, bytesGroupUid.length);
            return out;
        }

        public String getContactDisplayNameOrSerializedDetails() {
            return contactDisplayNameOrSerializedDetails;
        }

        public byte[] getBytesMediatorOrGroupOwnerIdentity() {
            return bytesMediatorOrGroupOwnerIdentity;
        }

        public byte[] getBytesContactIdentity() {
            return bytesContactIdentity;
        }

        public ObvIdentity[] getPendingGroupMemberIdentities() {
            return pendingGroupMemberIdentities;
        }

        private static Category of(Encoded encoded, ObjectMapper jsonObjectMapper) throws Exception {
            Encoded[] list = encoded.decodeList();
            if (list.length != 2) {
                throw new DecodingException();
            }
            int id = (int) list[0].decodeLong();
            byte[] bytesContactIdentity = null;
            String contactDisplayNameOrSerializedDetails = null;
            byte[] sasToDisplay = null;
            byte[] sasEntered = null;
            byte[] bytesMediatorOrGroupOwnerIdentity = null;
            String serializedGroupDetails = null;
            byte[] bytesGroupUid = null;
            ObvIdentity[] pendingGroupMemberIdentities = null;
            Long serverTimestamp = null;

            Encoded[] vars = list[1].decodeList();
            switch (id) {
                case SAS_CONFIRMED_DIALOG_CATEGORY: {
                    if (vars.length != 4) {
                        throw new DecodingException();
                    }
                    bytesContactIdentity = vars[0].decodeBytes();
                    contactDisplayNameOrSerializedDetails = vars[1].decodeString();
                    sasToDisplay = vars[2].decodeBytes();
                    sasEntered = vars[3].decodeBytes();
                    break;
                }
                case SAS_EXCHANGE_DIALOG_CATEGORY: {
                    if (vars.length != 4) {
                        throw new DecodingException();
                    }
                    bytesContactIdentity = vars[0].decodeBytes();
                    contactDisplayNameOrSerializedDetails = vars[1].decodeString();
                    sasToDisplay = vars[2].decodeBytes();
                    serverTimestamp = vars[3].decodeLong();
                    break;
                }
                case ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                case INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_CATEGORY: {
                    if (vars.length != 4) {
                        throw new DecodingException();
                    }
                    bytesContactIdentity = vars[0].decodeBytes();
                    contactDisplayNameOrSerializedDetails = vars[1].decodeString();
                    bytesMediatorOrGroupOwnerIdentity = vars[2].decodeBytes();
                    serverTimestamp = vars[3].decodeLong();
                    break;
                }
                case MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                case AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_CATEGORY: {
                    if (vars.length != 3) {
                        throw new DecodingException();
                    }
                    bytesContactIdentity = vars[0].decodeBytes();
                    contactDisplayNameOrSerializedDetails = vars[1].decodeString();
                    bytesMediatorOrGroupOwnerIdentity = vars[2].decodeBytes();
                    break;
                }
                case ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                case INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY: {
                    if (vars.length != 5) {
                        throw new DecodingException();
                    }
                    serializedGroupDetails = vars[0].decodeString();
                    bytesGroupUid = vars[1].decodeBytes();
                    bytesMediatorOrGroupOwnerIdentity = vars[2].decodeBytes();
                    Encoded[] pendingEncodeds = vars[3].decodeList();
                    pendingGroupMemberIdentities = new ObvIdentity[pendingEncodeds.length];
                    for (int i=0; i<pendingEncodeds.length; i++) {
                        pendingGroupMemberIdentities[i] = ObvIdentity.of(pendingEncodeds[i], jsonObjectMapper);
                    }
                    serverTimestamp = vars[4].decodeLong();
                    break;
                }
                case ACCEPT_INVITE_DIALOG_CATEGORY: {
                    if (vars.length != 3) {
                        throw new DecodingException();
                    }
                    bytesContactIdentity = vars[0].decodeBytes();
                    contactDisplayNameOrSerializedDetails = vars[1].decodeString();
                    serverTimestamp = vars[2].decodeLong();
                    break;
                }
                case GROUP_JOINED_DIALOG_CATEGORY: {
                    if (vars.length != 3) {
                        throw new DecodingException();
                    }
                    serializedGroupDetails = vars[0].decodeString();
                    bytesGroupUid = vars[1].decodeBytes();
                    bytesMediatorOrGroupOwnerIdentity = vars[2].decodeBytes();
                    break;
                }
                default:
                    if (vars.length != 2) {
                        throw new DecodingException();
                    }
                    bytesContactIdentity = vars[0].decodeBytes();
                    contactDisplayNameOrSerializedDetails = vars[1].decodeString();
                    break;
            }
            return new Category(id, bytesContactIdentity, contactDisplayNameOrSerializedDetails, sasToDisplay, sasEntered, bytesMediatorOrGroupOwnerIdentity, serializedGroupDetails, bytesGroupUid, pendingGroupMemberIdentities, serverTimestamp);
        }

        private Encoded encode(ObjectMapper jsonObjectMapper) {
            Encoded encodedVars = null;
            switch (id) {
                case INVITE_SENT_DIALOG_CATEGORY:
                case MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY:
                case INVITE_ACCEPTED_DIALOG_CATEGORY: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(bytesContactIdentity),
                            Encoded.of(contactDisplayNameOrSerializedDetails),
                    });
                    break;
                }
                case ACCEPT_INVITE_DIALOG_CATEGORY: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(bytesContactIdentity),
                            Encoded.of(contactDisplayNameOrSerializedDetails),
                            Encoded.of(serverTimestamp),
                    });
                    break;
                }
                case SAS_EXCHANGE_DIALOG_CATEGORY: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(bytesContactIdentity),
                            Encoded.of(contactDisplayNameOrSerializedDetails),
                            Encoded.of(sasToDisplay),
                            Encoded.of(serverTimestamp),
                    });
                    break;
                }
                case SAS_CONFIRMED_DIALOG_CATEGORY: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(bytesContactIdentity),
                            Encoded.of(contactDisplayNameOrSerializedDetails),
                            Encoded.of(sasToDisplay),
                            Encoded.of(sasEntered),
                    });
                    break;
                }
                case ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY:
                case INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_CATEGORY: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(bytesContactIdentity),
                            Encoded.of(contactDisplayNameOrSerializedDetails),
                            Encoded.of(bytesMediatorOrGroupOwnerIdentity),
                            Encoded.of(serverTimestamp),
                    });
                    break;
                }
                case MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY:
                case AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_CATEGORY: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(bytesContactIdentity),
                            Encoded.of(contactDisplayNameOrSerializedDetails),
                            Encoded.of(bytesMediatorOrGroupOwnerIdentity),
                    });
                    break;
                }
                case ACCEPT_GROUP_INVITE_DIALOG_CATEGORY:
                case INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY: {
                    Encoded[] pendingEncodeds = new Encoded[pendingGroupMemberIdentities.length];
                    for (int i = 0; i < pendingGroupMemberIdentities.length; i++) {
                        try {
                            pendingEncodeds[i] = pendingGroupMemberIdentities[i].encode(jsonObjectMapper);
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(serializedGroupDetails),
                            Encoded.of(bytesGroupUid),
                            Encoded.of(bytesMediatorOrGroupOwnerIdentity),
                            Encoded.of(pendingEncodeds),
                            Encoded.of(serverTimestamp),
                    });
                    break;
                }
                case GROUP_JOINED_DIALOG_CATEGORY: {
                    encodedVars = Encoded.of(new Encoded[]{
                            Encoded.of(serializedGroupDetails),
                            Encoded.of(bytesGroupUid),
                            Encoded.of(bytesMediatorOrGroupOwnerIdentity),
                    });
                    break;
                }
            }
            return Encoded.of(new Encoded[]{
                    Encoded.of(id),
                    encodedVars
            });
        }

        public static Category createInviteSent(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails) {
            return new Category(INVITE_SENT_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, null, null, null, null, null, null, null);
        }

        public static Category createAcceptInvite(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, long serverTimestamp) {
            return new Category(ACCEPT_INVITE_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, null, null, null, null, null, null, serverTimestamp);
        }

        public static Category createSasExchange(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, byte[] sasToDisplay, long serverTimestamp) {
            return new Category(SAS_EXCHANGE_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, sasToDisplay, null, null, null, null, null, serverTimestamp);
        }

        public static Category createSasConfirmed(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, byte[] sasToDisplay, byte[] sasEntered) {
            return new Category(SAS_CONFIRMED_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, sasToDisplay, sasEntered, null, null, null, null, null);
        }

        public static Category createMutualTrustConfirmed(byte[] bytesContactIdentity, String contactSerializedDetails) {
            return new Category(MUTUAL_TRUST_CONFIRMED_DIALOG_CATEGORY, bytesContactIdentity, contactSerializedDetails, null, null, null, null, null, null, null);
        }

        public static Category createInviteAccepted(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails) {
            return new Category(INVITE_ACCEPTED_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, null, null, null, null, null, null, null);
        }

        public static Category createAcceptMediatorInvite(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, byte[] bytesMediatorIdentity, long serverTimestamp) {
            return new Category(ACCEPT_MEDIATOR_INVITE_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, null, null, bytesMediatorIdentity, null, null, null, serverTimestamp);
        }

        public static Category createMediatorInviteAccepted(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, byte[] bytesMediatorIdentity) {
            return new Category(MEDIATOR_INVITE_ACCEPTED_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, null, null, bytesMediatorIdentity, null, null, null, null);
        }

        public static Category createAcceptGroupInvite(String serializedGroupDetails, byte[] groupId, byte[] bytesGroupOwnerIdentity, ObvIdentity[] pendingGroupMemberIdentities, long serverTimestamp) {
            return new Category(ACCEPT_GROUP_INVITE_DIALOG_CATEGORY, null, null, null, null, bytesGroupOwnerIdentity, serializedGroupDetails, groupId, pendingGroupMemberIdentities, serverTimestamp);
        }

        public static Category createIncreaseMediatorTrustLevel(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, byte[] bytesMediatorIdentity, long serverTimestamp) {
            return new Category(INCREASE_MEDIATOR_TRUST_LEVEL_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, null, null, bytesMediatorIdentity, null, null, null, serverTimestamp);
        }

        public static Category createIncreaseGroupOwnerTrustLevel(String groupName, byte[] groupId, byte[] bytesGroupOwnerIdentity, ObvIdentity[] pendingGroupMemberIdentities, long serverTimestamp) {
            return new Category(INCREASE_GROUP_OWNER_TRUST_LEVEL_DIALOG_CATEGORY, null, null, null, null, bytesGroupOwnerIdentity, groupName, groupId, pendingGroupMemberIdentities, serverTimestamp);
        }

        public static Category createAutoConfirmedContactIntroduction(byte[] bytesContactIdentity, String contactDisplayNameOrSerializedDetails, byte[] bytesMediatorIdentity) {
            return new Category(AUTO_CONFIRMED_CONTACT_INTRODUCTION_DIALOG_CATEGORY, bytesContactIdentity, contactDisplayNameOrSerializedDetails, null, null, bytesMediatorIdentity, null, null, null, null);
        }

        public static Category createGroupJoined(String serializedGroupDetails, byte[] bytesGroupUid, byte[] bytesGroupOwnerIdentity) {
            return new Category(GROUP_JOINED_DIALOG_CATEGORY, null, null, null, null , bytesGroupOwnerIdentity, serializedGroupDetails, bytesGroupUid, null, null);
        }
    }
}
