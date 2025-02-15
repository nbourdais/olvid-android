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

package io.olvid.messenger;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

//import net.openid.appauth.AuthState;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.Engine;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.engine.engine.types.identities.ObvIdentity;
import io.olvid.engine.engine.types.identities.ObvKeycloakState;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.CustomSSLSocketFactory;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.databases.tasks.backup.RestoreAppDataFromBackupTask;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.openid.KeycloakManager;
import io.olvid.messenger.services.PeriodicTasksScheduler;
import io.olvid.messenger.settings.SettingsActivity;

public class AppSingleton {
    private static final String LATEST_IDENTITY_SHARED_PREFERENCE_KEY = "last_identity";
    private static final String FIREBASE_TOKEN_SHARED_PREFERENCE_KEY = "firefase_token";
    private static final String LAST_BUILD_EXECUTED_PREFERENCE_KEY = "last_build";

    public static final String FYLE_DIRECTORY = "fyles";
    public static final String DISCUSSION_BACKGROUNDS_DIRECTORY = "discussion_backgrounds";
    public static final String CUSTOM_PHOTOS_DIRECTORY = "custom_photos";


    private static final AppSingleton instance = new AppSingleton();

    private final Engine engine;
    private final SSLSocketFactory sslSocketFactory;
    @SuppressWarnings({"FieldCanBeLocal", "unused", "RedundantSuppression"})
    private final EngineNotificationProcessor engineNotificationProcessor;
    private final ObjectMapper jsonObjectMapper;
    private final AppDatabase db;
    private final Observer<List<OwnedIdentity>> firstIdentitySelector;

    @NonNull private final SharedPreferences sharedPreferences;

    private AppSingleton() {
        this.bytesCurrentIdentity = null;
        this.jsonObjectMapper = new ObjectMapper();
        this.jsonObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.sharedPreferences = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_app), Context.MODE_PRIVATE);

        int lastBuildExecuted = sharedPreferences.getInt(LAST_BUILD_EXECUTED_PREFERENCE_KEY, 0);

        if (lastBuildExecuted != 0 && lastBuildExecuted < 89) {
            runNoBackupFolderMigration();
        }
        if (lastBuildExecuted != 0 && lastBuildExecuted < 120) {
            SettingsActivity.setContactDisplayNameFormat(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY);
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 124) {
            // clear missing google service dialog hide preference
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(SettingsActivity.USER_DIALOG_HIDE_GOOGLE_APIS);
            editor.apply();
        }

        if (lastBuildExecuted != 0 && lastBuildExecuted < 136) {
            // clear deprecated scaled_turn preference
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("pref_key_scaled_turn");
            editor.apply();
        }

        {
            // generate App directories
            File fylesDirectory = new File(App.getContext().getNoBackupFilesDir(), FYLE_DIRECTORY);
            //noinspection ResultOfMethodCallIgnored
            fylesDirectory.mkdirs();
            File discussionBackgroundsDirectory = new File(App.getContext().getNoBackupFilesDir(), DISCUSSION_BACKGROUNDS_DIRECTORY);
            //noinspection ResultOfMethodCallIgnored
            discussionBackgroundsDirectory.mkdirs();
            File customPhotosDirectory = new File(App.getContext().getNoBackupFilesDir(), CUSTOM_PHOTOS_DIRECTORY);
            //noinspection ResultOfMethodCallIgnored
            customPhotosDirectory.mkdirs();
        }

        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getDefault();
        } catch (Exception e) {
            Log.e("Logger", "Failed to initialize custom SSLSocketFactory");
            e.printStackTrace();
        }
        if (sslContext != null) {
            this.sslSocketFactory = new CustomSSLSocketFactory(sslContext.getSocketFactory());
        } else {
            this.sslSocketFactory = null;
        }

        // initialize Engine
        try {
            this.engine = new Engine(App.getContext().getNoBackupFilesDir(), null, this.sslSocketFactory,
                    new Logger.LogOutputter() {
                        @Override
                        public void d(String s, String s1) {
                            Log.d(s, s1);
                        }

                        @Override
                        public void i(String s, String s1) {
                            Log.i(s, s1);
                        }

                        @Override
                        public void w(String s, String s1) {
                            Log.w(s, s1);
                        }

                        @Override
                        public void e(String s, String s1) {
                            Log.e(s, s1);
                        }
                    },
                    SettingsActivity.useDebugLogLevel() ? Logger.DEBUG : BuildConfig.LOG_LEVEL);
        } catch (Exception e) {
            Log.e("Engine", "Error starting obv engine!");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        this.engineNotificationProcessor = new EngineNotificationProcessor(this.engine);
        this.engine.startSendingNotifications();

        db = AppDatabase.getInstance();
        bytesCurrentIdentityLiveData = new MutableLiveData<>();
        currentIdentityLiveData = Transformations.switchMap(bytesCurrentIdentityLiveData, (byte[] bytesOwnedIdentity) -> {
            if (bytesOwnedIdentity != null) {
                return AppDatabase.getInstance().ownedIdentityDao().getLiveData(bytesOwnedIdentity);
            }
            return null;
        });
        availableIdentities = db.ownedIdentityDao().getAllNotHiddenLiveData();

        // this observer is used in case there is no latest identity, or the latest identity cannot be found
        firstIdentitySelector = new Observer<List<OwnedIdentity>>() {
            @Override
            public void onChanged(List<OwnedIdentity> ownedIdentities) {
                if (ownedIdentities != null) {
                    if (ownedIdentities.size() != 0) {
                        selectIdentity(ownedIdentities.get(0).bytesOwnedIdentity, null);
                    }
                    availableIdentities.removeObserver(this);
                }
            }
        };
        selectLatestOpenedIdentity();

        this.contactNamesCache = new MutableLiveData<>();
        this.contactHuesCache = new MutableLiveData<>();
        this.contactPhotoUrlsCache = new MutableLiveData<>();
        this.contactKeycloakManagedCache = new MutableLiveData<>();
        this.contactInactiveCache = new MutableLiveData<>();

        Observer<OwnedIdentity> currentIdentityObserverForNameCache = new Observer<OwnedIdentity>() {
            byte[] bytesPreviousOwnedIdentity = null;

            @Override
            public void onChanged(OwnedIdentity ownedIdentity) {
                if (ownedIdentity == null) {
                    bytesPreviousOwnedIdentity = null;
                    App.runThread(() -> instance.reloadCachedDisplayNamesAndHues(null));
                } else if (!Arrays.equals(bytesPreviousOwnedIdentity, ownedIdentity.bytesOwnedIdentity)) {
                    bytesPreviousOwnedIdentity = ownedIdentity.bytesOwnedIdentity;
                    App.runThread(() -> instance.reloadCachedDisplayNamesAndHues(ownedIdentity));
                }
            }
        };

        this.deactivatedIdentities = new HashSet<>();

        new Handler(Looper.getMainLooper()).post(() -> currentIdentityLiveData.observeForever(currentIdentityObserverForNameCache));

        if (lastBuildExecuted != BuildConfig.VERSION_CODE) {
            App.runThread(() -> {
                AndroidNotificationManager.createChannels(lastBuildExecuted);
                runBuildUpgrade(lastBuildExecuted);
            });
        }

        App.runThread(() -> {
            try {
                // get all ownedIdentities and give relevant ones to KeycloakManager
                ObvIdentity[] ownedIdentities = this.engine.getOwnedIdentities();
                for (ObvIdentity ownedIdentity : ownedIdentities) {
                    if (ownedIdentity.isKeycloakManaged() && ownedIdentity.isActive()) {
                        ObvKeycloakState keycloakState = this.engine.getOwnedIdentityKeycloakState(ownedIdentity.getBytesIdentity());
                        if (keycloakState != null) {
                            KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                                    ownedIdentity,
                                    keycloakState.keycloakServer,
                                    keycloakState.clientId,
                                    keycloakState.clientSecret,
                                    keycloakState.jwks,
                                    keycloakState.signatureKey,
                                    keycloakState.serializedAuthState,
                                    keycloakState.latestRevocationListTimestamp,
                                    false
                            );
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    // region Owned identity list and current management

    private final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("AppSingleton-currentIdentitySelector-Executor");
    @Nullable private byte[] bytesCurrentIdentity;
    private boolean currentIdentityIsHidden;
    @NonNull private final MutableLiveData<byte[]> bytesCurrentIdentityLiveData;
    @NonNull private final LiveData<OwnedIdentity> currentIdentityLiveData;
    @NonNull private final LiveData<List<OwnedIdentity>> availableIdentities;

    public interface IdentitySelectedCallback {
        void onIdentitySelected(@Nullable OwnedIdentity ownedIdentity);
    }

    public void selectIdentity(byte[] bytesIdentity, IdentitySelectedCallback callback) {
        executor.execute(() -> {
            AndroidNotificationManager.clearHiddenIdentityNotifications();
            if (bytesIdentity != null) {
                OwnedIdentity ownedIdentity = AppDatabase.getInstance().ownedIdentityDao().get(bytesIdentity);
                if (ownedIdentity != null) {
                    this.bytesCurrentIdentity = bytesIdentity;
                    this.bytesCurrentIdentityLiveData.postValue(bytesIdentity);

                    if (ownedIdentity.isHidden()) {
                        currentIdentityIsHidden = true;
                    } else {
                        currentIdentityIsHidden = false;
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, Logger.toHexString(bytesIdentity));
                        editor.apply();
                    }

                    if (callback != null) {
                        callback.onIdentitySelected(ownedIdentity);
                    }
                    // show any identity specific app dialog immediately
                    App.showAppDialogsForSelectedIdentity(bytesIdentity);
                    KeycloakManager.showAuthenticationRequiredNotificationForSelectedIdentityIfNeeded(bytesIdentity);
                    return;
                }
            }

            this.bytesCurrentIdentity = null;
            this.bytesCurrentIdentityLiveData.postValue(null);
            if (callback != null) {
                callback.onIdentitySelected(null);
            }
        });
    }

    @Nullable
    public static byte[] getBytesCurrentIdentity() {
        return instance.bytesCurrentIdentity;
    }

    @NonNull
    public static LiveData<OwnedIdentity> getCurrentIdentityLiveData() {
        return instance.currentIdentityLiveData;
    }

    private void selectLatestOpenedIdentity() {
        String lastIdentityHexString = sharedPreferences.getString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, null);
        if (lastIdentityHexString != null) {
            selectIdentity(Logger.fromHexString(lastIdentityHexString), ownedIdentity -> {
                if (ownedIdentity == null) {
                    new Handler(Looper.getMainLooper()).post(() -> availableIdentities.observeForever(firstIdentitySelector));
                }
            });
        } else {
            new Handler(Looper.getMainLooper()).post(() -> availableIdentities.observeForever(firstIdentitySelector));
        }
    }

    public void ownedIdentityBecameHidden(@NonNull byte[] bytesOwnedIdentity) {
        if (Arrays.equals(bytesCurrentIdentity, bytesOwnedIdentity)) {
            currentIdentityIsHidden = true;
        }
        String latestIdentityHex = sharedPreferences.getString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, null);
        if (Logger.toHexString(bytesOwnedIdentity).equals(latestIdentityHex)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(LATEST_IDENTITY_SHARED_PREFERENCE_KEY);
            editor.apply();
        }
    }

    public void ownedIdentityWasDeleted(byte[] bytesOwnedIdentity) {
        if (Arrays.equals(bytesCurrentIdentity, bytesOwnedIdentity)) {
            new Handler(Looper.getMainLooper()).post(() -> availableIdentities.observeForever(firstIdentitySelector));
        }
        String latestIdentityHex = sharedPreferences.getString(LATEST_IDENTITY_SHARED_PREFERENCE_KEY, null);
        if (Logger.toHexString(bytesOwnedIdentity).equals(latestIdentityHex)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(LATEST_IDENTITY_SHARED_PREFERENCE_KEY);
            editor.apply();
        }
    }

    public static boolean isCurrentIdentityHidden() {
        return instance.currentIdentityIsHidden;
    }

    public void closeHiddenProfile() {
        executor.execute(() -> {
            if (currentIdentityIsHidden && App.shouldActivitiesBeKilledOnLockAndHiddenProfileClosedOnBackground()) {
                selectLatestOpenedIdentity();

                Intent closeOpenActivitiesIntent = new Intent(App.CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION);
                closeOpenActivitiesIntent.setPackage(App.getContext().getPackageName());
                LocalBroadcastManager.getInstance(App.getContext()).sendBroadcast(closeOpenActivitiesIntent);
            }
        });
    }

    // endregion

    // region Identity creation and restore (and active status)

    @NonNull private final HashSet<BytesKey> deactivatedIdentities;

    public static void markIdentityActive(@NonNull OwnedIdentity ownedIdentity, boolean active) {
        synchronized (instance) {
            if (active) {
                if (instance.deactivatedIdentities.remove(new BytesKey(ownedIdentity.bytesOwnedIdentity))) {
                    // only called if the HashSet indeed contained the identity
                    if (Arrays.equals(ownedIdentity.bytesOwnedIdentity, getInstance().bytesCurrentIdentity)) {
                        App.openAppDialogIdentityActivated(ownedIdentity);

                        if (ownedIdentity.keycloakManaged) {
                            try {
                                ObvIdentity obvIdentity = getEngine().getOwnedIdentity(ownedIdentity.bytesOwnedIdentity);
                                ObvKeycloakState keycloakState = getEngine().getOwnedIdentityKeycloakState(ownedIdentity.bytesOwnedIdentity);
                                if (obvIdentity != null && keycloakState != null) {
                                    KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                                            obvIdentity,
                                            keycloakState.keycloakServer,
                                            keycloakState.clientId,
                                            keycloakState.clientSecret,
                                            keycloakState.jwks,
                                            keycloakState.signatureKey,
                                            keycloakState.serializedAuthState,
                                            keycloakState.latestRevocationListTimestamp,
                                            false
                                    );
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } else {
                instance.deactivatedIdentities.add(new BytesKey(ownedIdentity.bytesOwnedIdentity));
                App.openAppDialogIdentityDeactivated(ownedIdentity);
            }
        }
    }


    public interface GenerateIdentitySuccessCallback {
        void run(ObvIdentity ownedIdentity);
    }

    public void generateIdentity(@NonNull final String server,
                                 @NonNull final UUID apiKey,
                                 @NonNull final JsonIdentityDetails identityDetails,
                                 @Nullable final String absolutePhotoUrl,
                                 @Nullable final String customDisplayName,
                                 @Nullable final byte[] unlockPassword,
                                 @Nullable final byte[] unlockSalt,
                                 @Nullable final String keycloakServer,
                                 @Nullable final String clientId,
                                 @Nullable final String clientSecret,
                                 @Nullable final JsonWebKeySet jwks,
                                 @Nullable final JsonWebKey signatureKey,
                                 @Nullable final String serializedKeycloakState,
                                 @Nullable final GenerateIdentitySuccessCallback successCallback,
                                 @Nullable final Runnable failureCallback) {
        App.runThread(() -> {
            ObvKeycloakState keycloakState = null;
            if (keycloakServer != null && serializedKeycloakState != null && jwks != null && clientId != null && signatureKey != null) {
                keycloakState = new ObvKeycloakState(keycloakServer, clientId, clientSecret, jwks, signatureKey, serializedKeycloakState, 0);
            }

            ObvIdentity obvOwnedIdentity = engine.generateOwnedIdentity(server, identityDetails, apiKey, keycloakState);

            if (obvOwnedIdentity != null) {
                if (keycloakState != null) {
                    KeycloakManager.getInstance().registerKeycloakManagedIdentity(obvOwnedIdentity, keycloakServer, clientId, clientSecret, jwks, signatureKey, serializedKeycloakState, 0, true);
                }
                OwnedIdentity ownedIdentity;
                try {
                    ownedIdentity = new OwnedIdentity(obvOwnedIdentity, OwnedIdentity.API_KEY_STATUS_UNKNOWN);
                    ownedIdentity.customDisplayName = customDisplayName;
                    ownedIdentity.unlockPassword = unlockPassword;
                    ownedIdentity.unlockSalt = unlockSalt;
                    db.ownedIdentityDao().insert(ownedIdentity);
                } catch (Exception e) {
                    // unable to create ownedIdentity on app side, try delete on engine side
                    try {
                        engine.deleteOwnedIdentity(obvOwnedIdentity.getBytesIdentity());
                    } catch (Exception ex) {
                        // nothing more we can do here!
                    }
                    if (failureCallback != null) {
                        failureCallback.run();
                    }
                    return;
                }

                if (absolutePhotoUrl != null) {
                    try {
                        engine.updateOwnedIdentityPhoto(obvOwnedIdentity.getBytesIdentity(), absolutePhotoUrl);
                        engine.publishLatestIdentityDetails(obvOwnedIdentity.getBytesIdentity());
                    } catch (Exception e) {
                        // error with the photo, too bad...
                    }
                }

                selectIdentity(ownedIdentity.bytesOwnedIdentity, (OwnedIdentity ignored) -> {
                    if (successCallback != null) {
                        successCallback.run(obvOwnedIdentity);
                    }

                    String token = AppSingleton.retrieveFirebaseToken();
                    for (int i = 0; i < 5; i++) {
                        try {
                            engine.registerToPushNotification(obvOwnedIdentity.getBytesIdentity(), token, false, false);
                            break;
                        } catch (Exception e) {
                            Logger.d("Error registering newly generated Identity to push notification.");
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                if (failureCallback != null) {
                    failureCallback.run();
                }
            }
        });
    }

    public void restoreBackup(final Context activityContext, final String backupSeed, final byte[] encryptedBackupContent, final Runnable successCallback, final Runnable failureCallback) {
        App.runThread(() -> {
            Logger.w("💾 Initiating à backup restore");
            if (AppDatabase.getInstance().ownedIdentityDao().countAll() != 0) {
                App.toast(R.string.toast_message_something_went_wrong, Toast.LENGTH_SHORT);
                if (failureCallback != null) {
                    failureCallback.run();
                }
                return;
            }

            ObvIdentity[] obvOwnedIdentities = engine.restoreOwnedIdentitiesFromBackup(backupSeed, encryptedBackupContent);
            if (obvOwnedIdentities != null && obvOwnedIdentities.length != 0) {
                try {
                    final String token = AppSingleton.retrieveFirebaseToken();

                    db.runInTransaction((Callable<Void>) () -> {
                        for (ObvIdentity obvOwnedIdentity : obvOwnedIdentities) {
                            OwnedIdentity ownedIdentity = new OwnedIdentity(obvOwnedIdentity, OwnedIdentity.API_KEY_STATUS_UNKNOWN);

                            try {
                                if (getEngine().getOwnedIdentityPublishedAndLatestDetails(obvOwnedIdentity.getBytesIdentity()).length == 2) {
                                    ownedIdentity.unpublishedDetails = OwnedIdentity.UNPUBLISHED_DETAILS_EXIST;
                                }
                            } catch (Exception ignored) {
                                // failed to check if there are unpublished details, nobody cares!
                            }
                            db.ownedIdentityDao().insert(ownedIdentity);

                            Logger.w("💾 Restored own identity");

                            if (obvOwnedIdentity.isKeycloakManaged() && obvOwnedIdentity.isActive()) {
                                Logger.w("💾 Identity is keycloak managed --> registering with the KeycloakManager");
                                try {
                                    ObvKeycloakState keycloakState = engine.getOwnedIdentityKeycloakState(obvOwnedIdentity.getBytesIdentity());
                                    if (keycloakState != null) {
                                        KeycloakManager.getInstance().registerKeycloakManagedIdentity(
                                                obvOwnedIdentity,
                                                keycloakState.keycloakServer,
                                                keycloakState.clientId,
                                                keycloakState.clientSecret,
                                                keycloakState.jwks,
                                                keycloakState.signatureKey,
                                                keycloakState.serializedAuthState,
                                                0,
                                                false
                                        );
                                    }
                                } catch (Exception ignored) {
                                    Logger.w("💾 Unable to register with the KeycloakManager");
                                }
                            }

                            if (obvOwnedIdentity.isActive()) {
                                for (int i = 0; i < 5; i++) {
                                    try {
                                        engine.registerToPushNotification(obvOwnedIdentity.getBytesIdentity(), token, true, false);
                                        break;
                                    } catch (Exception e) {
                                        Logger.d("Error registering newly generated Identity to push notification.");
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    if (failureCallback != null) {
                        failureCallback.run();
                    }
                    return;
                }


                // once contacts and groups are restored in engine we can restore nicknames and settings
                EngineNotificationListener backupRestoredListener = new EngineNotificationListener() {
                    private Long registrationNumber = null;

                    @Override
                    public void callback(String notificationName, HashMap<String, Object> userInfo) {
                        if (EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED.equals(notificationName)) {
                            Logger.w("💾 Received backup restore finished notification --> restoring app data");
                            getEngine().removeNotificationListener(EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED, this);

                            String appDataBackup = engine.decryptAppDataBackup(backupSeed, encryptedBackupContent);

                            if (appDataBackup != null) {
                                Boolean success = new RestoreAppDataFromBackupTask(appDataBackup).call();
                                if (success != null && !success) {
                                    Logger.w("💾 Failed to restore som app data");
                                    App.toast(R.string.toast_message_unable_to_restore_app_data, Toast.LENGTH_LONG);
                                } else {
                                    Logger.w("💾 App data successfully restored");
                                }
                            }

                            List<OwnedIdentity> ownedIdentities = db.ownedIdentityDao().getAllNotHidden();
                            byte[] bytesOwnedIdentityToSelect;
                            if (ownedIdentities == null || ownedIdentities.size() == 0) {
                                bytesOwnedIdentityToSelect = null;
                            } else {
                                bytesOwnedIdentityToSelect = ownedIdentities.get(0).bytesOwnedIdentity;
                            }
                            selectIdentity(bytesOwnedIdentityToSelect, (OwnedIdentity ignored) -> {
                                if (successCallback != null) {
                                    successCallback.run();
                                }
                                App.openCurrentOwnedIdentityDetails(activityContext);

                                try {
                                    engine.downloadAllUserData();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }

                    @Override
                    public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
                        this.registrationNumber = registrationNumber;
                    }

                    @Override
                    public long getEngineNotificationListenerRegistrationNumber() {
                        if (registrationNumber != null) {
                            return registrationNumber;
                        }
                        return -1;
                    }

                    @Override
                    public boolean hasEngineNotificationListenerRegistrationNumber() {
                        return registrationNumber != null;
                    }
                };

                getEngine().addNotificationListener(EngineNotifications.ENGINE_BACKUP_RESTORATION_FINISHED, backupRestoredListener);

                // before restoring the contacts, we reset the default discussion ephemeral settings to avoid wrongfully setting them
                SettingsActivity.setDefaultDiscussionReadOnce(false);
                SettingsActivity.setDefaultDiscussionVisibilityDuration(null);
                SettingsActivity.setDefaultDiscussionExistenceDuration(null);

                Logger.w("💾 Initiating contact and groups restore");
                engine.restoreContactsAndGroupsFromBackup(backupSeed, encryptedBackupContent, obvOwnedIdentities);
                Logger.w("💾 Contacts and groups restored on Engine side");
            } else {
                if (failureCallback != null) {
                    failureCallback.run();
                }
            }
        });
    }

    // endregion

    // region Singleton methods

    public static void storeFirebaseToken(String token) {
        SharedPreferences.Editor editor = instance.sharedPreferences.edit();
        editor.putString(FIREBASE_TOKEN_SHARED_PREFERENCE_KEY, token);
        editor.apply();
    }

    @Nullable
    public static String retrieveFirebaseToken() {
        if (SettingsActivity.disablePushNotifications()) {
            return null;
        } else {
            return instance.sharedPreferences.getString(FIREBASE_TOKEN_SHARED_PREFERENCE_KEY, null);
        }
    }

    private static void setLastBuildExecutedVersion(@SuppressWarnings("SameParameterValue") int buildVersion) {
        SharedPreferences.Editor editor = instance.sharedPreferences.edit();
        editor.putInt(LAST_BUILD_EXECUTED_PREFERENCE_KEY, buildVersion);
        editor.apply();
    }

    public static AppSingleton getInstance() {
        return instance;
    }

    public static Engine getEngine() {
        return instance.engine;
    }

    public static SSLSocketFactory getSslSocketFactory() {
        return instance.sslSocketFactory;
    }

    public static ObjectMapper getJsonObjectMapper() {
        return instance.jsonObjectMapper;
    }

    // endregion

    // region Contact names and info caches for main thread access

    @NonNull private final MutableLiveData<HashMap<BytesKey, String>> contactNamesCache;
    @NonNull private final MutableLiveData<HashMap<BytesKey, Integer>> contactHuesCache;
    @NonNull private final MutableLiveData<HashMap<BytesKey, String>> contactPhotoUrlsCache;
    @NonNull private final MutableLiveData<HashSet<BytesKey>> contactKeycloakManagedCache;
    @NonNull private final MutableLiveData<HashSet<BytesKey>> contactInactiveCache;

    @NonNull
    public static LiveData<HashMap<BytesKey, String>> getContactNamesCache() {
        return getInstance().contactNamesCache;
    }

    @NonNull
    public static LiveData<HashMap<BytesKey, Integer>> getContactHuesCache() {
        return getInstance().contactHuesCache;
    }

    @NonNull
    public static LiveData<HashMap<BytesKey, String>> getContactPhotoUrlsCache() {
        return getInstance().contactPhotoUrlsCache;
    }

    @NonNull
    public static LiveData<HashSet<BytesKey>> getContactKeycloakManagedCache() {
        return getInstance().contactKeycloakManagedCache;
    }

    @NonNull
    public static LiveData<HashSet<BytesKey>> getContactInactiveCache() {
        return getInstance().contactInactiveCache;
    }

    public static void reloadCachedDisplayNamesAndHues() {
        instance.reloadCachedDisplayNamesAndHues(getCurrentIdentityLiveData().getValue());
    }

    private void reloadCachedDisplayNamesAndHues(@Nullable OwnedIdentity ownedIdentity) {
        if (ownedIdentity == null) {
            getInstance().contactNamesCache.postValue(new HashMap<>());
            getInstance().contactHuesCache.postValue(new HashMap<>());
            getInstance().contactPhotoUrlsCache.postValue(new HashMap<>());
            getInstance().contactKeycloakManagedCache.postValue(new HashSet<>());
            getInstance().contactInactiveCache.postValue(new HashSet<>());
            return;
        }
        List<Contact> contacts = AppDatabase.getInstance().contactDao().getAllForOwnedIdentitySync(ownedIdentity.bytesOwnedIdentity);
        HashMap<BytesKey, String> contactNamesHashMap = new HashMap<>();
        HashMap<BytesKey, Integer> contactHuesHashMap = new HashMap<>();
        HashMap<BytesKey, String> contactPhotoUrlsHashMap = new HashMap<>();
        HashSet<BytesKey> contactKeycloakManagedHashSet = new HashSet<>();
        HashSet<BytesKey> contactInactiveHashSet = new HashSet<>();
        for (Contact contact : contacts) {
            BytesKey key = new BytesKey(contact.bytesContactIdentity);
            contactNamesHashMap.put(key, contact.getCustomDisplayName());
            if (contact.customNameHue != null) {
                contactHuesHashMap.put(key, contact.customNameHue);
            }
            if (contact.getCustomPhotoUrl() != null) {
                contactPhotoUrlsHashMap.put(key, contact.getCustomPhotoUrl());
            }
            if (contact.keycloakManaged) {
                contactKeycloakManagedHashSet.add(key);
            }
            if (!contact.active) {
                contactInactiveHashSet.add(key);
            }
        }

        BytesKey ownKey = new BytesKey(ownedIdentity.bytesOwnedIdentity);
        contactNamesHashMap.put(ownKey, App.getContext().getString(R.string.text_you));
        if (ownedIdentity.photoUrl != null) {
            contactPhotoUrlsHashMap.put(ownKey, ownedIdentity.photoUrl);
        }
        if (ownedIdentity.keycloakManaged) {
            contactKeycloakManagedHashSet.add(ownKey);
        }
        if (!ownedIdentity.active) {
            contactInactiveHashSet.add(ownKey);
        }

        getInstance().contactNamesCache.postValue(contactNamesHashMap);
        getInstance().contactHuesCache.postValue(contactHuesHashMap);
        getInstance().contactPhotoUrlsCache.postValue(contactPhotoUrlsHashMap);
        getInstance().contactKeycloakManagedCache.postValue(contactKeycloakManagedHashSet);
        getInstance().contactInactiveCache.postValue(contactInactiveHashSet);
    }

    public static String getContactCustomDisplayName(byte[] bytesContactIdentity) {
        if (getContactNamesCache().getValue() == null) {
            return null;
        }
        return getContactNamesCache().getValue().get(new BytesKey(bytesContactIdentity));
    }

    public static Integer getContactCustomHue(byte[] bytesContactIdentity) {
        if (getContactHuesCache().getValue() == null) {
            return null;
        }
        return getContactHuesCache().getValue().get(new BytesKey(bytesContactIdentity));
    }

    public static String getContactPhotoUrl(byte[] bytesContactIdentity) {
        if (getContactPhotoUrlsCache().getValue() == null) {
            return null;
        }
        return getContactPhotoUrlsCache().getValue().get(new BytesKey(bytesContactIdentity));
    }

    public static boolean getContactKeycloakManaged(byte[] bytesContactIdentity) {
        if (getContactKeycloakManagedCache().getValue() == null) {
            return false;
        }
        return getContactKeycloakManagedCache().getValue().contains(new BytesKey(bytesContactIdentity));
    }

    public static boolean getContactInactive(byte[] bytesContactIdentity) {
        if (getContactInactiveCache().getValue() == null) {
            return false;
        }
        return getContactInactiveCache().getValue().contains(new BytesKey(bytesContactIdentity));
    }

    public static void updateCachedCustomDisplayName(byte[] bytesContactIdentity, String customDisplayName) {
        if (getContactNamesCache().getValue() == null) {
            return;
        }
        HashMap<BytesKey, String> hashMap = getContactNamesCache().getValue();
        hashMap.put(new BytesKey(bytesContactIdentity), customDisplayName);
        getInstance().contactNamesCache.postValue(hashMap);
    }

    public static void updateCachedCustomHue(byte[] bytesContactIdentity, Integer customHue) {
        if (getContactHuesCache().getValue() == null) {
            return;
        }
        HashMap<BytesKey, Integer> hashMap = getContactHuesCache().getValue();
        if (customHue != null) {
            hashMap.put(new BytesKey(bytesContactIdentity), customHue);
        } else {
            hashMap.remove(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactHuesCache.postValue(hashMap);
    }

    public static void updateCachedPhotoUrl(byte[] bytesContactIdentity, String photoUrl) {
        if (getContactPhotoUrlsCache().getValue() == null) {
            return;
        }
        HashMap<BytesKey, String> hashMap = getContactPhotoUrlsCache().getValue();
        if (photoUrl != null) {
            hashMap.put(new BytesKey(bytesContactIdentity), photoUrl);
        } else {
            hashMap.remove(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactPhotoUrlsCache.postValue(hashMap);
    }

    public static void updateCachedKeycloakManaged(byte[] bytesContactIdentity, boolean managed) {
        if (getContactKeycloakManagedCache().getValue() == null) {
            return;
        }
        HashSet<BytesKey> hashSet = getContactKeycloakManagedCache().getValue();
        if (managed) {
            hashSet.add(new BytesKey(bytesContactIdentity));
        } else {
            hashSet.remove(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactKeycloakManagedCache.postValue(hashSet);
    }

    public static void updateCachedActive(byte[] bytesContactIdentity, boolean active) {
        if (getContactInactiveCache().getValue() == null) {
            return;
        }
        HashSet<BytesKey> hashSet = getContactInactiveCache().getValue();
        if (active) {
            hashSet.remove(new BytesKey(bytesContactIdentity));
        } else {
            hashSet.add(new BytesKey(bytesContactIdentity));
        }
        getInstance().contactInactiveCache.postValue(hashSet);
    }


    // endregion

    // region Upgrade after new build

    private void runBuildUpgrade(int lastBuildExecuted) {
        try {
            if (lastBuildExecuted < 88) {
                // trigger the download of all waiting userData in the engine
                engine.downloadAllUserData();
            }
            if (lastBuildExecuted < 99) {
                // reset the apiKey to de hardcoded one
                boolean success = true;
                for (ObvIdentity ownedIdentity: engine.getOwnedIdentities()) {
                    success &= engine.updateApiKeyForOwnedIdentity(ownedIdentity.getBytesIdentity(), UUID.fromString(BuildConfig.HARDCODED_API_KEY));
                }
                if (!success) {
                    Logger.e("Error resetting API key to HARDCODED one");
                    throw new Exception();
                }
            }
            if (lastBuildExecuted < 127) {
                // recompute the number of images in all messages as the filtering method was changed
                long migrationStartTime = System.currentTimeMillis();
                AppDatabase db = AppDatabase.getInstance();
                List<Message> messages = db.messageDao().getAllWithImages();
                for (Message message: messages) {
                    if (message.recomputeAttachmentCount(db)) {
                        db.messageDao().updateAttachmentCount(message.id, message.totalAttachmentCount, message.imageCount, message.wipedAttachmentCount, message.imageResolutions);
                    }
                }
                Logger.i("Build 126/127 image migration performed in " + (System.currentTimeMillis()-migrationStartTime) + "ms");
            }
            if (lastBuildExecuted != 0 && lastBuildExecuted < 145) {
                App.openAppDialogIntroducingMultiProfile();
            }
            PeriodicTasksScheduler.resetAllPeriodicTasksFollowingAnUpdate(App.getContext());
            setLastBuildExecutedVersion(BuildConfig.VERSION_CODE);
        } catch (Exception e) {
            // upgrade failed, will be tried again at next startup...
            Logger.w("Build Upgrade failed");
            e.printStackTrace();
        }
    }

    private void runNoBackupFolderMigration() {
        String[] filesToMigrate = new String[]{"inbound_attachments", "identity_photos", "discussion_backgrounds", "fyles", "engine_db.sqlite"};
        File filesDir = App.getContext().getFilesDir();
        File noBackupFilesDir = App.getContext().getNoBackupFilesDir();
        //noinspection ResultOfMethodCallIgnored
        noBackupFilesDir.mkdirs();
        for (String fileToMigrate: filesToMigrate) {
            File oldFile = new File(filesDir, fileToMigrate);
            File newFile = new File(noBackupFilesDir, fileToMigrate);
            if (oldFile.exists()) {
                if (!oldFile.renameTo(newFile)) {
                    Log.e("Logger", "Error migrating " + fileToMigrate + " to no backup folder.");
                }
            }
        }

        File databaseDir = App.getContext().getDatabasePath("app_database").getParentFile();
        String[] dbFilesToMigrate = new String[]{"app_database", "app_database-wal", "app_database-shm"};
        for (String dbFileToMigrate: dbFilesToMigrate) {
            File oldFile = new File(databaseDir, dbFileToMigrate);
            File newFile = new File(noBackupFilesDir, dbFileToMigrate);
            if (oldFile.exists()) {
                if (!oldFile.renameTo(newFile)) {
                    Log.e("Logger", "Error migrating " + dbFileToMigrate + " to no backup folder.");
                }
            }
        }
    }

    // endregion
}
