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

import org.jose4j.jwk.JsonWebKey;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import io.olvid.engine.engine.types.identities.ObvContactActiveOrInactiveReason;
import io.olvid.engine.engine.types.identities.ObvGroup;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.engine.engine.types.identities.ObvMutualScanUrl;
import io.olvid.engine.engine.types.identities.ObvTrustOrigin;

public interface EngineAPI {
    enum ApiKeyPermission {
        CALL,
        WEB_CLIENT,
    }

    enum ApiKeyStatus {
        UNKNOWN,
        VALID,
        LICENSES_EXHAUSTED,
        EXPIRED,
        OPEN_BETA_KEY,
        FREE_TRIAL_KEY,
        AWAITING_PAYMENT_GRACE_PERIOD,
        AWAITING_PAYMENT_ON_HOLD,
        FREE_TRIAL_KEY_EXPIRED,
    }

    // Engine notifications
    void addNotificationListener(String notificationName, EngineNotificationListener engineNotificationListener);
    void removeNotificationListener(String notificationName, EngineNotificationListener engineNotificationListener);
    void startSendingNotifications();
    void stopSendingNotifications();


    // ObvOwnedIdentity
    String getServerOfIdentity(byte[] bytesIdentity);
    ObvIdentity[] getOwnedIdentities() throws Exception;
    ObvIdentity getOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    ObvIdentity generateOwnedIdentity(String server, JsonIdentityDetails jsonIdentityDetails, UUID apiKey, ObvKeycloakState keycloakState);
    UUID getApiKeyForOwnedIdentity(byte[] bytesOwnedIdentity);
    boolean updateApiKeyForOwnedIdentity(byte[] bytesOwnedIdentity, UUID apiKey);
    void recreateServerSession(byte[] bytesOwnedIdentity);
    void deleteOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    JsonIdentityDetailsWithVersionAndPhoto[] getOwnedIdentityPublishedAndLatestDetails(byte[] bytesOwnedOdentity) throws Exception;
    ObvKeycloakState getOwnedIdentityKeycloakState(byte[] bytesOwnedIdentity) throws Exception;
    void saveKeycloakAuthState(byte[] bytesOwnedIdentity, String serializedAuthState) throws Exception;
    void saveKeycloakJwks(byte[] bytesOwnedIdentity, String serializedJwks) throws Exception;
    List<ObvIdentity> getOwnedIdentitiesWithKeycloakPushTopic(String pushTopic) throws Exception;
    String getOwnedIdentityKeycloakUserId(byte[] bytesOwnedIdentity) throws Exception;
    void setOwnedIdentityKeycloakUserId(byte[] bytesOwnedIdentity, String id) throws Exception;
    JsonWebKey getOwnedIdentityKeycloakSignatureKey(byte[] bytesOwnedIdentity) throws Exception;
    void setOwnedIdentityKeycloakSignatureKey(byte[] bytesOwnedIdentity, JsonWebKey signatureKey) throws Exception;
    ObvIdentity bindOwnedIdentityToKeycloak(byte[] bytesOwnedIdentity, ObvKeycloakState keycloakState, String keycloakUserId);
    void unbindOwnedIdentityFromKeycloak(byte[] bytesOwnedIdentity);
    void updateKeycloakPushTopicsIfNeeded(byte[] bytesOwnedIdentity, String serverUrl, List<String> pushTopics);
    void updateKeycloakRevocationList(byte[] bytesOwnedIdentity, long latestRevocationListTimestamp, List<String> signedRevocations);
    void setOwnedIdentityKeycloakSelfRevocationTestNonce(byte[] bytesOwnedIdentity, String serverUrl, String nonce);
    String getOwnedIdentityKeycloakSelfRevocationTestNonce(byte[] bytesOwnedIdentity, String serverUrl);

    void registerToPushNotification(byte[] bytesOwnedIdentity, String firebaseToken, boolean kickOtherDevices, boolean useMultidevice) throws Exception;
    void unregisterToPushNotification(byte[] bytesOwnedIdentity) throws Exception;
    void processAndroidPushNotification(String maskingUidString);

    void updateLatestIdentityDetails(byte[] bytesOwnedIdentity, JsonIdentityDetails jsonIdentityDetails) throws Exception;
    void discardLatestIdentityDetails(byte[] bytesOwnedIdentity);
    void publishLatestIdentityDetails(byte[] bytesOwnedIdentity);
    void updateOwnedIdentityPhoto(byte[] bytesOwnedIdentity, String absolutePhotoUrl) throws Exception;

    String serverForIdentity(byte[] bytesIdentity);
    byte[] getServerAuthenticationToken(byte[] bytesOwnedIdentity);

    // ObvContactIdentity
    ObvIdentity[] getContactsOfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    EnumSet<ObvContactActiveOrInactiveReason> getContactActiveOrInactiveReasons(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    boolean forcefullyUnblockContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    boolean reBlockForcefullyUnblockedContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);

    int getContactDeviceCount(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    int getContactEstablishedChannelsCount(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    String getContactTrustedDetailsPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    JsonIdentityDetailsWithVersionAndPhoto[] getContactPublishedAndTrustedDetails(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void trustPublishedContactDetails(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity);
    ObvTrustOrigin[] getContactTrustOrigins(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    boolean doesContactHaveAutoAcceptTrustLevel(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;

    // ObvGroup
    ObvGroup[] getGroupsOfOwnedIdentity(byte[] bytesOwnedIdentity) throws Exception;
    JsonGroupDetailsWithVersionAndPhoto[] getGroupPublishedAndLatestOrTrustedDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    String getGroupTrustedDetailsPhotoUrl(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    void trustPublishedGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);
    void updateLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, JsonGroupDetails jsonGroupDetails) throws Exception;
    void discardLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);
    void publishLatestGroupDetails(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid);
    void updateOwnedGroupPhoto(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, String photoUrl) throws Exception;



    // ObvDialog
    void deletePersistedDialog(UUID uuid) throws Exception;
    void resendAllPersistedDialogs() throws Exception;
    void respondToDialog(ObvDialog dialog) throws Exception;
    void abortProtocol(ObvDialog dialog) throws Exception;

    // Start protocols
    void startTrustEstablishmentProtocol(byte[] bytesRemoteIdentity, String contactDisplayName, byte[] bytesOwnedIdentity) throws Exception;
    ObvMutualScanUrl computeMutualScanSignedNonceUrl(byte[] bytesRemoteIdentity, byte[] bytesOwnedIdentity, String ownDisplayName) throws Exception;
    boolean verifyMutualScanSignedNonceUrl(byte[] bytesOwnedIdentity, ObvMutualScanUrl mutualScanUrl);
    void startMutualScanTrustEstablishmentProtocol(byte[] bytesOwnedIdentity, byte[] bytesRemoteIdentity, byte[] signature) throws Exception;
    void startContactMutualIntroductionProtocol(byte[] bytesOwnedIdentity, byte[] bytesContactIdentityA, byte[][] bytesContactIdentities) throws Exception;
    void startGroupCreationProtocol(String serializedGroupDetailsWithVersionAndPhoto, String photoUrl, byte[] bytesOwnedIdentity, byte[][] bytesRemoteIdentities) throws Exception;
    void restartAllOngoingChannelEstablishmentProtocols(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void recreateAllChannels(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void inviteContactsToGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[][] bytesNewMemberIdentities) throws Exception;
    void removeContactsFromGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[][] bytesRemovedMemberIdentities) throws Exception;
    void reinvitePendingToGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid, byte[] bytesPendingMemberIdentity) throws Exception;
    void leaveGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    void disbandGroup(byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) throws Exception;
    void deleteContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) throws Exception;
    void deleteOwnedIdentityAndNotifyContacts(byte[] bytesOwnedIdentity) throws Exception;
    void queryGroupOwnerForLatestGroupMembers(byte[] bytesGroupOwnerAndUid, byte[] bytesOwnedIdentity) throws Exception;
    void addKeycloakContact(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, String signedContactDetails) throws Exception;

    // Post/receive messages
    byte[] getReturnReceiptNonce();
    byte[] getReturnReceiptKey();
    void deleteReturnReceipt(byte[] bytesOwnedIdentity, byte[] serverUid);
    ObvReturnReceipt decryptReturnReceipt(byte[] returnReceiptKey, byte[] encryptedPayload);
    ObvPostMessageOutput post(byte[] messagePayload, byte[] extendedMessagePayload, ObvOutboundAttachment[] attachments, List<byte[]> bytesContactIdentities, byte[] bytesOwnedIdentity, boolean hasUserContent, boolean isVoipMessage);
    void sendReturnReceipt(byte[] bytesOwnedIdentity, byte[] senderIdentifier, int status, byte[] returnReceiptNonce, byte[] returnReceiptKey, Integer attachmentNumber);
    boolean isOutboxAttachmentSent(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier, int engineNumber);
    boolean isOutboxMessageSent(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier);

    boolean isInboxAttachmentReceived(byte[] bytesOwnedIdentity, byte[] engineMessageIdentifier, int engineNumber);
    void downloadMessages(byte[] bytesOwnedIdentity);
    void downloadSmallAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void downloadLargeAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void pauseAttachmentDownload(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void deleteAttachment(ObvAttachment attachment);
    void deleteAttachment(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void deleteMessageAndAttachments(byte[] bytesOwnedIdentity, byte[] messageIdentifier);
    void deleteMessage(byte[] bytesOwnedIdentity, byte[] messageIdentifier);
    void cancelAttachmentUpload(byte[] bytesOwnedIdentity, byte[] messageIdentifier, int attachmentNumber);
    void resendAllAttachmentNotifications() throws Exception;
    void connectWebsocket(String os, String osVersion, int appBuild, String appVersion);
    void disconnectWebsocket();
    void pingWebsocket(byte[] bytesOwnedIdentity);
    void retryScheduledNetworkTasks();

    // Backups
    void initiateBackup(boolean forExport);
    ObvBackupKeyInformation getBackupKeyInformation() throws Exception;
    void generateBackupKey();
    void setAutoBackupEnabled(boolean enabled);
    void markBackupExported(byte[] backupKeyUid, int version);
    void markBackupUploaded(byte[] backupKeyUid, int version);
    void discardBackup(byte[] backupKeyUid, int version);
    ObvBackupKeyVerificationOutput validateBackupSeed(String backupSeed, byte[] backupContent);
    ObvBackupKeyVerificationOutput verifyBackupSeed(String backupSeed);
    ObvIdentity[] restoreOwnedIdentitiesFromBackup(String backupSeed, byte[] backupContent);
    void restoreContactsAndGroupsFromBackup(String backupSeed, byte[] backupContent,  ObvIdentity[] restoredOwnedIdentities);
    String decryptAppDataBackup(String backupSeed, byte[] backupContent);
    void appBackupSuccess(byte[] bytesBackupKeyUid, int version, String appBackupContent);
    void appBackupFailed(byte[] bytesBackupKeyUid, int version);


    void getTurnCredentials(byte[] bytesOwnedIdentity, UUID callUuid, String callerUsername, String recipientUsername);
    void queryApiKeyStatus(byte[] bytesOwnedIdentity, UUID apiKey);
    void queryApiKeyStatus(String server, UUID apiKey);
    void queryFreeTrial(byte[] bytesOwnedIdentity);
    void startFreeTrial(byte[] bytesOwnedIdentity);
    void verifyReceipt(byte[] bytesOwnedIdentity, String storeToken);
    void queryServerWellKnown(String server);



    // Run once after you upgrade from a version not handling Contact and ContactGroup UserData (profile photos) to a version able to do so
    void downloadAllUserData() throws Exception;

    void vacuumDatabase() throws Exception;
}
