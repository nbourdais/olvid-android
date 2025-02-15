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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.icu.lang.UCharacter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import androidx.emoji2.bundled.BundledEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.EmojiSpan;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.messenger.activities.ContactDetailsActivity;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.activities.GroupCreationActivity;
import io.olvid.messenger.activities.GroupDetailsActivity;
import io.olvid.messenger.activities.LockScreenActivity;
import io.olvid.messenger.appdialogs.AppDialogTag;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.activities.MessageDetailsActivity;
import io.olvid.messenger.activities.OwnedIdentityDetailsActivity;
import io.olvid.messenger.activities.ShortcutActivity;
import io.olvid.messenger.customClasses.PreviewUtils;
import io.olvid.messenger.customClasses.SecureAlertDialogBuilder;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Message;
import io.olvid.messenger.gallery.GalleryActivity;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.services.AvailableSpaceHelper;
import io.olvid.messenger.services.MessageExpirationService;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.webrtc.WebrtcCallActivity;
import io.olvid.messenger.appdialogs.AppDialogShowActivity;
import io.olvid.messenger.services.PeriodicTasksScheduler;
import io.olvid.messenger.services.NetworkStateMonitorReceiver;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.webrtc.WebrtcCallService;


public class App extends Application implements DefaultLifecycleObserver {
    public static final String CAMERA_PICTURE_FOLDER = "camera_pictures";
    public static final String WEBCLIENT_ATTACHMENT_FOLDER = "webclient_attachment_folder";
    public static final String TIMESTAMP_FILE_NAME_FORMAT = "yyyy-MM-dd@HH-mm-ss";

    public static final String NEW_APP_DIALOG_BROADCAST_ACTION = "new_app_dialog_to_show";
    public static final String CURRENT_HIDDEN_PROFILE_CLOSED_BROADCAST_ACTION = "current_hidden_profile_closed";

    private static final LinkedHashMap<String, HashMap<String, Object>> dialogsToShow = new LinkedHashMap<>();
    private static final HashMap<BytesKey, LinkedHashMap<String, HashMap<String, Object>>> dialogsToShowForOwnedIdentity = new HashMap<>();
    private static final Lock dialogsToShowLock = new ReentrantLock();
    private static boolean blockAppDialogs = false;

    private static Application application;
    public static long appStartTimestamp = 0;

    private static Application getApplication() {
        return application;
    }
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static Context getContext() {
        return getApplication().getApplicationContext();
    }

    private static boolean isVisible = false;
    private static boolean isAppDialogShowing = false;
    private static boolean killActivitiesOnLockAndCloseHiddenProfileOnBackground = true;

    public static boolean isVisible() { return isVisible; }


    public static void doNotKillActivitiesOnLockOrCloseHiddenProfileOnBackground() {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
    }

    public static boolean shouldActivitiesBeKilledOnLockAndHiddenProfileClosedOnBackground() { return killActivitiesOnLockAndCloseHiddenProfileOnBackground; }

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        appStartTimestamp = System.currentTimeMillis();

        SettingsActivity.setDefaultNightMode();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && UnifiedForegroundService.willUnifiedForegroundServiceStartForegroun()) {
                startForegroundService(new Intent(this, UnifiedForegroundService.class));
            } else {
                startService(new Intent(this, UnifiedForegroundService.class));
            }
        } catch (IllegalStateException e) {
            Logger.i("App was started in the background, unable to start UnifiedForegroundService.");
        }

        runThread(new AppStartupTasks());
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            PreviewUtils.purgeCache();
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        isVisible = true;
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = true;
        UnifiedForegroundService.onAppForeground(getContext());

//        // we no longer poll for new messages on app foreground, this is almost always useless
//        if (AppSingleton.getCurrentIdentityLiveData().getValue() != null) {
//            AppSingleton.getEngine().downloadMessages(AppSingleton.getCurrentIdentityLiveData().getValue().bytesOwnedIdentity);
//        }

        runThread(() -> AvailableSpaceHelper.refreshAvailableSpace(false));

        AndroidNotificationManager.clearNeutralNotification();
        showAppDialogs();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isVisible = false;
        UnifiedForegroundService.onAppBackground(getContext());
    }

    // call this before launching an intent that will put the app in background so that the lock screen does not kill running activities
    // typically, when attaching a file to a discussion :)
    public static void prepareForStartActivityForResult(AppCompatActivity activity) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(activity, LockScreenActivity.class);
        activity.startActivity(lockIntent);
    }
    public static void prepareForStartActivityForResult(Fragment fragment) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(fragment.getContext(), LockScreenActivity.class);
        fragment.startActivity(lockIntent);
    }

    public static void startActivityForResult(AppCompatActivity activity, Intent intent, int requestCode) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(activity, LockScreenActivity.class);
        activity.startActivity(lockIntent);
        //noinspection deprecation
        activity.startActivityForResult(intent, requestCode);
    }

    public static void startActivityForResult(Fragment fragment, Intent intent, int requestCode) {
        killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
        Intent lockIntent = new Intent(fragment.getContext(), LockScreenActivity.class);
        fragment.startActivity(lockIntent);
        //noinspection deprecation
        fragment.startActivityForResult(intent, requestCode);
    }

    public static void openDiscussionActivity(Context activityContext, long discussionId) {
        Intent intent = new Intent(getContext(), DiscussionActivity.class);
        intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityContext.startActivity(intent);
    }

    public static void openOneToOneDiscussionActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, boolean closeOtherDiscussions) {
        Intent intent = new Intent(getContext(), DiscussionActivity.class);
        intent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(DiscussionActivity.BYTES_CONTACT_IDENTITY_INTENT_EXTRA, bytesContactIdentity);
        if (closeOtherDiscussions) {
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        activityContext.startActivity(intent);
    }

    public static void openGroupDiscussionActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupUid) {
        Intent intent = new Intent(getContext(), DiscussionActivity.class);
        intent.putExtra(DiscussionActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(DiscussionActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupUid);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityContext.startActivity(intent);
    }

    public static void openDiscussionGalleryActivity(Context activityContext, long discussionId, long messageId, long fyleId) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        activityContext.startActivity(intent);
    }

    public static void openDraftGalleryActivity(Context activityContext, long draftMessageId, long fyleId) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.DRAFT_INTENT_EXTRA, true);
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, draftMessageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        activityContext.startActivity(intent);
    }

    public static void openMessageGalleryActivity(Context activityContext, long messageId, long fyleId) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.DRAFT_INTENT_EXTRA, false);
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        activityContext.startActivity(intent);
    }

    public static void openOwnedIdentityGalleryActivity(Context activityContext, byte[] bytesOwnedIdentity, long messageId, long fyleId) {
        Intent intent = new Intent(getContext(), GalleryActivity.class);
        intent.putExtra(GalleryActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(GalleryActivity.INITIAL_MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(GalleryActivity.INITIAL_FYLE_ID_INTENT_EXTRA, fyleId);
        activityContext.startActivity(intent);
    }


    public static void openGroupCreationActivity(Context activityContext) {
        Intent intent = new Intent(getContext(), GroupCreationActivity.class);
        activityContext.startActivity(intent);
    }


    private static void openAppDialogShower() {
        Intent newAppDialogIntent = new Intent(NEW_APP_DIALOG_BROADCAST_ACTION);
        newAppDialogIntent.setPackage(App.getContext().getPackageName());
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(newAppDialogIntent);
    }

    public static void openCurrentOwnedIdentityDetails(Context activityContext) {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.setAction(MainActivity.FORWARD_ACTION);
        intent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, OwnedIdentityDetailsActivity.class.getName());
        activityContext.startActivity(intent);
    }

    public static void openContactDetailsActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        Intent intent = new Intent(getContext(), ContactDetailsActivity.class);
        intent.putExtra(ContactDetailsActivity.CONTACT_BYTES_CONTACT_IDENTITY_INTENT_EXTRA, bytesContactIdentity);
        intent.putExtra(ContactDetailsActivity.CONTACT_BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        activityContext.startActivity(intent);
    }

    public static void startWebrtcCall(Context context, byte[] bytesOwnedIdentity, byte[] bytesContactIdentity) {
        Intent serviceIntent = new Intent(getContext(), WebrtcCallService.class);
        serviceIntent.setAction(WebrtcCallService.ACTION_START_CALL);
        Bundle bytesContactIdentitiesBundle = new Bundle();
        bytesContactIdentitiesBundle.putByteArray(WebrtcCallService.SINGLE_CONTACT_IDENTITY_BUNDLE_KEY, bytesContactIdentity);
        serviceIntent.putExtra(WebrtcCallService.CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA, bytesContactIdentitiesBundle);
        serviceIntent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        context.startService(serviceIntent);

        Intent activityIntent = new Intent(getContext(), WebrtcCallActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(activityIntent);
    }

    public static void startWebrtcMultiCall(Context context, byte[] bytesOwnedIdentity, List<Contact> contacts, byte[] bytesGroupOwnerAndUid) {
        Intent serviceIntent = new Intent(getContext(), WebrtcCallService.class);
        serviceIntent.setAction(WebrtcCallService.ACTION_START_CALL);
        serviceIntent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        Bundle bytesContactIdentitiesBundle = new Bundle();
        int count = 0;
        for (Contact contact: contacts) {
            bytesContactIdentitiesBundle.putByteArray(Integer.toString(count), contact.bytesContactIdentity);
            count++;
        }
        serviceIntent.putExtra(WebrtcCallService.CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA, bytesContactIdentitiesBundle);
        if (bytesGroupOwnerAndUid != null) {
            serviceIntent.putExtra(WebrtcCallService.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupOwnerAndUid);
        }
        context.startService(serviceIntent);

        Intent activityIntent = new Intent(getContext(), WebrtcCallActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(activityIntent);
    }

    public static void handleWebrtcMessage(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, Message.JsonWebrtcMessage jsonWebrtcMessage, long downloadTimestamp, long serverTimestamp) {
        if (jsonWebrtcMessage.getCallIdentifier() == null || jsonWebrtcMessage.getMessageType() == null|| jsonWebrtcMessage.getSerializedMessagePayload() == null) {
            return;
        }
        int messageType = jsonWebrtcMessage.getMessageType();

        if (downloadTimestamp - serverTimestamp > WebrtcCallService.CALL_TIMEOUT_MILLIS) {
            if (messageType == WebrtcCallService.START_CALL_MESSAGE_TYPE) {
                runThread(() -> {
                    CallLogItem callLogItem = new CallLogItem(bytesOwnedIdentity, CallLogItem.TYPE_INCOMING, CallLogItem.STATUS_MISSED, serverTimestamp);
                    callLogItem.id = AppDatabase.getInstance().callLogItemDao().insert(callLogItem);
                    CallLogItemContactJoin callLogItemContactJoin = new CallLogItemContactJoin(callLogItem.id, bytesOwnedIdentity, bytesContactIdentity);
                    AppDatabase.getInstance().callLogItemDao().insert(callLogItemContactJoin);

                    AndroidNotificationManager.displayMissedCallNotification(bytesOwnedIdentity, bytesContactIdentity);

                    Discussion discussion = AppDatabase.getInstance().discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
                    if (discussion != null) {
                        Message missedCallMessage = Message.createPhoneCallMessage(discussion.id, bytesContactIdentity, callLogItem);
                        AppDatabase.getInstance().messageDao().insert(missedCallMessage);
                        if (discussion.updateLastMessageTimestamp(missedCallMessage.timestamp)) {
                            AppDatabase.getInstance().discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                        }
                    }
                });
            }
            Logger.i("Discarded an outdated webrtc message (age = " + (downloadTimestamp - serverTimestamp) + "ms)");
            return;
        }

        Intent intent = new Intent(getContext(), WebrtcCallService.class);
        intent.setAction(WebrtcCallService.ACTION_MESSAGE);
        intent.putExtra(WebrtcCallService.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(WebrtcCallService.BYTES_CONTACT_IDENTITY_INTENT_EXTRA, bytesContactIdentity);
        intent.putExtra(WebrtcCallService.CALL_IDENTIFIER_INTENT_EXTRA, jsonWebrtcMessage.getCallIdentifier().toString());
        intent.putExtra(WebrtcCallService.MESSAGE_TYPE_INTENT_EXTRA, messageType);
        intent.putExtra(WebrtcCallService.SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA, jsonWebrtcMessage.getSerializedMessagePayload());

        if (messageType == WebrtcCallService.START_CALL_MESSAGE_TYPE) {
            getContext().startService(intent);
        } else {
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        }
    }

    public static void openGroupDetailsActivity(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) {
        Intent intent = new Intent(getContext(), GroupDetailsActivity.class);
        intent.putExtra(GroupDetailsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(GroupDetailsActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupOwnerAndUid);
        activityContext.startActivity(intent);
    }

    public static void openGroupDetailsActivityForEditDetails(Context activityContext, byte[] bytesOwnedIdentity, byte[] bytesGroupOwnerAndUid) {
        Intent intent = new Intent(getContext(), GroupDetailsActivity.class);
        intent.putExtra(GroupDetailsActivity.BYTES_OWNED_IDENTITY_INTENT_EXTRA, bytesOwnedIdentity);
        intent.putExtra(GroupDetailsActivity.BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA, bytesGroupOwnerAndUid);
        intent.putExtra(GroupDetailsActivity.EDIT_DETAILS_INTENT_EXTRA, true);
        activityContext.startActivity(intent);
    }

    public static void openMessageDetails(Context activityContext, long messageId, boolean hasAttachments, boolean isInbound) {
        Intent intent = new Intent(getContext(), MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_INTENT_EXTRA, messageId);
        intent.putExtra(MessageDetailsActivity.HAS_ATTACHMENT_INTENT_EXTRA, hasAttachments);
        intent.putExtra(MessageDetailsActivity.IS_INBOUND_INTENT_EXTRA, isInbound);
        activityContext.startActivity(intent);
    }

    public static void showMainActivityTab(Context activityContext, int tabId) {
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.putExtra(MainActivity.TAB_TO_SHOW_INTENT_EXTRA, tabId);
        activityContext.startActivity(intent);
    }

    public static void openFyleInExternalViewer(Activity activity, FyleMessageJoinWithStatusDao.FyleAndStatus fyleAndStatus, Runnable onOpenCallback) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fyleAndStatus.getContentUri(), fyleAndStatus.fyleMessageJoinWithStatus.getNonNullMimeType());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (getContext().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext());
            if (prefs.getBoolean(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP, false)) {
                if (onOpenCallback != null) {
                    onOpenCallback.run();
                }
                killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
                Intent lockIntent = new Intent(activity, LockScreenActivity.class);
                activity.startActivity(lockIntent);
                activity.startActivity(intent);
            } else {
                View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_view_message_and_checkbox, null);
                TextView message = dialogView.findViewById(R.id.dialog_message);
                message.setText(R.string.dialog_message_open_external_app_warning);
                CheckBox checkBox = dialogView.findViewById(R.id.checkbox);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(SettingsActivity.USER_DIALOG_HIDE_OPEN_EXTERNAL_APP, isChecked);
                    editor.apply();
                });

                AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog);
                builder.setTitle(R.string.dialog_title_open_external_app_warning)
                        .setView(dialogView)
                        .setNegativeButton(R.string.button_label_cancel, null)
                        .setPositiveButton(R.string.button_label_proceed, (dialog, which) -> {
                            if (onOpenCallback != null) {
                                onOpenCallback.run();
                            }
                            killActivitiesOnLockAndCloseHiddenProfileOnBackground = false;
                            Intent lockIntent = new Intent(activity, LockScreenActivity.class);
                            activity.startActivity(lockIntent);
                            activity.startActivity(intent);
                        });
                builder.create().show();
            }
        } else {
            App.toast(R.string.toast_message_unable_to_open_file, Toast.LENGTH_SHORT);
        }
    }


    public static CharSequence getNiceDateString(Context context, long timestamp) {
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestamp)) {
            // same day
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else if (timestamp < now
                && (timestamp + 86_400_000*6 > now || DateUtils.isToday(timestamp + 86_400_000*6))) {
            // same week
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else {
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        }
    }

    public static CharSequence getLongNiceDateString(Context context, long timestamp) {
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestamp)) {
            // same day
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else if (timestamp < now
                && (timestamp + 86_400_000*6 > now || DateUtils.isToday(timestamp + 86_400_000*6))) {
            // same week
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        } else {
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH) + context.getString(R.string.text_date_time_separator) + DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
        }
    }

    public static CharSequence getDayOfDateString(Context context, long timestamp) {
        long now = System.currentTimeMillis();
        if (DateUtils.isToday(timestamp)) {
            // same day
            return context.getString(R.string.text_today);
        } else if ((timestamp < now && timestamp + 86_400_000 > now) ||
                DateUtils.isToday(timestamp + 86_400_000)) {
            // yesterday
            return context.getString(R.string.text_yesterday);
        } else if ((timestamp < now && timestamp + 86_400_000*6 > now) ||
                DateUtils.isToday(timestamp + 86_400_000*6)) {
            // same week
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY);
        } else {
            return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY);
        }
    }


    static final HashMap<String, SimpleDateFormat> bestTimeFormatterCache = new HashMap<>();
    public static CharSequence getPreciseAbsoluteDateString(Context context, long timestamp) {
        return getPreciseAbsoluteDateString(context, timestamp, "\n");
    }

    public static CharSequence getPreciseAbsoluteDateString(Context context, long timestamp, String separator) {
        Locale locale = context.getResources().getConfiguration().locale;
        SimpleDateFormat formatter = bestTimeFormatterCache.get(locale.toString());
        if (formatter == null) {
            String patternDay = DateFormat.getBestDateTimePattern(locale, "yyyy MMM dd");
            String patternTime = DateFormat.getBestDateTimePattern(locale, "jj mm ss");
            String pattern = patternDay + separator + patternTime;
            formatter = new SimpleDateFormat(pattern, locale);
            bestTimeFormatterCache.put(locale.toString(), formatter);
        }
        return formatter.format(new Date(timestamp));
    }

    public static String absolutePathFromRelative(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        return new File(getContext().getNoBackupFilesDir(), relativePath).getAbsolutePath();
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({android.widget.Toast.LENGTH_SHORT, android.widget.Toast.LENGTH_LONG})
    public @interface ToastLength {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({android.view.Gravity.CENTER, android.view.Gravity.BOTTOM, android.view.Gravity.TOP})
    public @interface ToastGravity {}

    public static void toast(@StringRes final int resId, @ToastLength final int duration) {
        toast(getContext().getString(resId), duration, Gravity.BOTTOM);
    }

    public static void toast(@StringRes final int resId, @ToastLength final int duration, @ToastGravity final int gravity) {
        toast(getContext().getString(resId), duration, gravity);
    }

    public static void toast(final String message, @ToastLength final int duration, @ToastGravity final int gravity) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast toast;
            if (App.isVisible() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
                LayoutInflater layoutInflater = (LayoutInflater) App.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (layoutInflater == null) {
                    return;
                }
                @SuppressLint("InflateParams") View toastLayout = layoutInflater.inflate(R.layout.view_toast, null);
                TextView text = toastLayout.findViewById(R.id.toast_text);
                text.setText(message);
                toast = new Toast(getContext());
                toast.setDuration(duration);
                toast.setView(toastLayout);
            } else {
                toast = Toast.makeText(App.getContext(), message, duration);
            }
            switch (gravity) {
                case Gravity.TOP:
                    toast.setGravity(Gravity.TOP, 0, getContext().getResources().getDisplayMetrics().densityDpi * 96 / 160);
                    break;
                case Gravity.CENTER:
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    break;
                case Gravity.BOTTOM:
                default:
                    break;
            }
            toast.show();
        });
    }


    // region App-wide dialogs

    public static void releaseAppDialogShowing() {
        dialogsToShowLock.lock();
        isAppDialogShowing = false;
        dialogsToShowLock.unlock();
        showAppDialogs();
    }

    public static boolean requestAppDialogShowing() {
        dialogsToShowLock.lock();
        if (isAppDialogShowing) {
            dialogsToShowLock.unlock();
            return false;
        }
        isAppDialogShowing = true;
        dialogsToShowLock.unlock();
        return true;
    }

    public static void showAppDialogs() {
        if (blockAppDialogs) {
            return;
        }
        dialogsToShowLock.lock();
        if (!dialogsToShow.isEmpty()) {
            openAppDialogShower();
        } else if (AppSingleton.getBytesCurrentIdentity() != null) {
            BytesKey bytesKey = new BytesKey(AppSingleton.getBytesCurrentIdentity());
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null && !map.isEmpty()) {
                openAppDialogShower();
            }
        }
        dialogsToShowLock.unlock();
    }

    public static void showAppDialogsForSelectedIdentity(@NonNull byte[] bytesOwnedIdentity) {
        if (blockAppDialogs) {
            return;
        }
        dialogsToShowLock.lock();
        BytesKey bytesKey = new BytesKey(bytesOwnedIdentity);
        LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
        if (map != null && !map.isEmpty()) {
            openAppDialogShower();
        }
        dialogsToShowLock.unlock();
    }

    public static void setAppDialogsBlocked(boolean blocked) {
        if (blocked) {
            blockAppDialogs = true;
        } else {
            blockAppDialogs = false;
            showAppDialogs();
        }
    }

    public static void openAppDialogIdentityDeactivated(OwnedIdentity ownedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_IDENTITY_DEACTIVATED_OWNED_IDENTITY_KEY, ownedIdentity);
        showDialog(ownedIdentity.bytesOwnedIdentity, AppDialogShowActivity.DIALOG_IDENTITY_DEACTIVATED, dialogParameters);
    }

    public static void openAppDialogIdentityActivated(OwnedIdentity ownedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_IDENTITY_ACTIVATED_OWNED_IDENTITY_KEY, ownedIdentity);
        showDialog(ownedIdentity.bytesOwnedIdentity, AppDialogShowActivity.DIALOG_IDENTITY_ACTIVATED, dialogParameters);
    }

    public static void openAppDialogApiKeyPermissionsUpdated(OwnedIdentity ownedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_SUBSCRIPTION_UPDATED_OWNED_IDENTITY_KEY, ownedIdentity);
        showDialog(ownedIdentity.bytesOwnedIdentity, AppDialogShowActivity.DIALOG_SUBSCRIPTION_UPDATED, dialogParameters);
    }

    public static void openAppDialogSubscriptionRequired(byte[] bytesOwnedIdentity, EngineAPI.ApiKeyPermission permission) {
        if (bytesOwnedIdentity == null) {
            return;
        }
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_SUBSCRIPTION_REQUIRED_FEATURE_KEY, permission);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_SUBSCRIPTION_REQUIRED, dialogParameters);
    }

    public static void openAppDialogNewVersionAvailable() {
        showDialog(null, AppDialogShowActivity.DIALOG_NEW_VERSION_AVAILABLE, new HashMap<>());
    }

    public static void openAppDialogOutdatedVersion() {
        showDialog(null, AppDialogShowActivity.DIALOG_OUTDATED_VERSION, new HashMap<>());
    }

    public static void openAppDialogCallInitiationNotSupported(byte[] bytesOwnedIdentity) {
        if (bytesOwnedIdentity == null) {
            return;
        }
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_CALL_INITIATION_NOT_SUPPORTED, new HashMap<>());
    }

    public static void openAppDialogKeycloakAuthenticationRequired(@NonNull byte[] bytesOwnedIdentity, @NonNull String clientId, @Nullable String clientSecret, @NonNull String keycloakServerUrl) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_ID_KEY, clientId);
        if (clientSecret != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_CLIENT_SECRET_KEY, clientSecret);
        }
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED_SERVER_URL_KEY, keycloakServerUrl);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED, dialogParameters);
    }

    public static void openAppDialogKeycloakIdentityReplacement(@NonNull byte[] bytesOwnedIdentity, @NonNull String serverUrl, @Nullable String clientSecret, @NonNull String serializedAuthState) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERVER_URL_KEY, serverUrl);
        if (clientSecret != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_CLIENT_SECRET_KEY, clientSecret);
        }
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_SERIALIZED_AUTH_STATE_KEY, serializedAuthState);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT, dialogParameters);
    }

    public static void openAppDialogKeycloakUserIdChanged(@NonNull byte[] bytesOwnedIdentity, @NonNull String clientId, @Nullable String clientSecret, @NonNull String keycloakServerUrl) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_ID_KEY, clientId);
        if (clientSecret != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_CLIENT_SECRET_KEY, clientSecret);
        }
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED_SERVER_URL_KEY, keycloakServerUrl);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_USER_ID_CHANGED, dialogParameters);
    }

    public static void openAppDialogKeycloakSignatureKeyChanged(@NonNull byte[] bytesOwnedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_SIGNATURE_KEY_CHANGED, dialogParameters);
    }

    public static void openAppDialogKeycloakIdentityReplacementForbidden(@NonNull byte[] bytesOwnedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_REPLACEMENT_FORBIDDEN, dialogParameters);
    }

    public static void openAppDialogKeycloakIdentityRevoked(byte[] bytesOwnedIdentity) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED_BYTES_OWNED_IDENTITY_KEY, bytesOwnedIdentity);
        showDialog(bytesOwnedIdentity, AppDialogShowActivity.DIALOG_KEYCLOAK_IDENTITY_WAS_REVOKED, dialogParameters);
    }

    public static void openAppDialogSdCardRingtoneBuggedAndroid9() {
        showDialog(null, AppDialogShowActivity.DIALOG_SD_CARD_RINGTONE_BUGGED_ANDROID_9, new HashMap<>());
    }

    public static void openAppDialogCertificateChanged(long untrustedCertificateId, @Nullable Long lastTrustedCertificateId) {
        HashMap<String, Object> dialogParameters = new HashMap<>();
        dialogParameters.put(AppDialogShowActivity.DIALOG_CERTIFICATE_CHANGED_UNTRUSTED_CERTIFICATE_ID_KEY, untrustedCertificateId);
        if (lastTrustedCertificateId != null) {
            dialogParameters.put(AppDialogShowActivity.DIALOG_CERTIFICATE_CHANGED_LAST_TRUSTED_CERTIFICATE_ID_KEY, lastTrustedCertificateId);
        }
        // we add a prefix to the dialog name so that multiple dialogs can be opened for multiple new certificates
        showDialog(null, AppDialogShowActivity.DIALOG_CERTIFICATE_CHANGED + "_" + untrustedCertificateId, dialogParameters);
    }

    public static void openAppDialogLowStorageSpace() {
        showDialog(null, AppDialogShowActivity.DIALOG_AVAILABLE_SPACE_LOW, new HashMap<>());
    }

    public static void openAppDialogBackupRequiresDriveSignIn() {
        showDialog(null, AppDialogShowActivity.DIALOG_BACKUP_REQUIRES_DRIVE_SIGN_IN, new HashMap<>());
    }

    public static void openAppDialogConfigureHiddenProfileClosePolicy() {
        showDialog(null, AppDialogShowActivity.DIALOG_CONFIGURE_HIDDEN_PROFILE_CLOSE_POLICY, new HashMap<>());
    }

    public static void openAppDialogIntroducingMultiProfile() {
        showDialog(null, AppDialogShowActivity.DIALOG_INTRODUCING_MULTI_PROFILE, new HashMap<>());
    }


    private static void showDialog(@Nullable byte[] bytesDialogOwnedIdentity, String dialogTag, HashMap<String, Object> dialogParameters) {
        dialogsToShowLock.lock();
        if (bytesDialogOwnedIdentity == null) {
            dialogsToShow.put(dialogTag, dialogParameters);
        } else {
            BytesKey bytesKey = new BytesKey(bytesDialogOwnedIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map == null) {
                map = new LinkedHashMap<>();
                dialogsToShowForOwnedIdentity.put(bytesKey, map);
            }
            map.put(dialogTag, dialogParameters);
        }
        dialogsToShowLock.unlock();
        showAppDialogs();
    }

    public static AppDialogTag getNextDialogTag() {
        dialogsToShowLock.lock();
        // first check if there is an ownedIdentity specific dialog to show
        byte[] currentIdentity = AppSingleton.getBytesCurrentIdentity();
        if (currentIdentity != null) {
            BytesKey bytesKey = new BytesKey(currentIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null && !map.isEmpty()) {
                AppDialogTag dialogTag;
                if (map.containsKey(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED)) {
                    dialogTag = new AppDialogTag(AppDialogShowActivity.DIALOG_KEYCLOAK_AUTHENTICATION_REQUIRED, currentIdentity);
                } else {
                    dialogTag = new AppDialogTag(map.keySet().iterator().next(), currentIdentity);
                }
                dialogsToShowLock.unlock();
                return dialogTag;
            }
        }

        // there is no app specific dialog to show --> check for generic dialogs
        if (!dialogsToShow.isEmpty()) {
            AppDialogTag dialogTag = new AppDialogTag(dialogsToShow.keySet().iterator().next(), null);
            dialogsToShowLock.unlock();
            return dialogTag;
        }

        // there is no dialog to show
        dialogsToShowLock.unlock();
        return null;
    }

    public static HashMap<String, Object> getDialogParameters(AppDialogTag dialogTag) {
        if (dialogTag.bytesDialogOwnedIdentity == null) {
            return dialogsToShow.get(dialogTag.dialogTag);
        } else {
            BytesKey bytesKey = new BytesKey(dialogTag.bytesDialogOwnedIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null) {
                return map.get(dialogTag.dialogTag);
            }
            return null;
        }
    }

    public static void removeDialog(AppDialogTag dialogTag) {
        dialogsToShowLock.lock();
        if (dialogTag.bytesDialogOwnedIdentity == null) {
            dialogsToShow.remove(dialogTag.dialogTag);
        } else {
            BytesKey bytesKey = new BytesKey(dialogTag.bytesDialogOwnedIdentity);
            LinkedHashMap<String, HashMap<String, Object>> map = dialogsToShowForOwnedIdentity.get(bytesKey);
            if (map != null) {
                map.remove(dialogTag.dialogTag);
            }
        }
        dialogsToShowLock.unlock();
    }

    // endregion


    public static void runThread(Runnable runnable) {
        executorService.submit(runnable);
    }

    public static String getInitial(String name) {
        if ((name == null) || (name.length() == 0)) {
            return "";
        }
        BreakIterator breakIterator = BreakIterator.getCharacterInstance();
        breakIterator.setText(name);
        int offset;
        int glue;
        int modifier;
        do {
            offset = breakIterator.next();
            glue = name.charAt(offset-1);
            modifier = (offset < name.length())?name.codePointAt(offset):0;
        } while ((glue == 0x200d) || ((0x1f3fb <= modifier) && (modifier <= 0x1f3ff)));
        return name.substring(0, offset).toUpperCase(Locale.getDefault());
    }

    public static boolean isShortEmojiString(String text, int maxLength) {
        // Alternate method based on EmojiCompat library --> we use the legacy method for now, it still works well :)
//        CharSequence emojiSequence = EmojiCompat.get().process(text, 0, text.length(), maxLength);
//        if (emojiSequence instanceof Spanned) {
//            Spanned spannable = (Spanned) emojiSequence;
//            int regionEnd;
//            for (int regionStart = 0; regionStart < spannable.length(); regionStart = regionEnd) {
//                regionEnd = spannable.nextSpanTransition(regionStart, spannable.length(), EmojiSpan.class);
//
//                EmojiSpan[] spans = spannable.getSpans(regionStart, regionEnd, EmojiSpan.class);
//                if (spans.length == 0) {
//                    return false;
//                }
//            }
//            return true;
//        }
//        return false;

        if (text == null || text.length() == 0) {
            return false;
        }

        BreakIterator breakIterator = BreakIterator.getCharacterInstance();
        breakIterator.setText(text);

        int computedEmojiLength = 0;
        int offset;
        int glue;
        int codePoint = text.codePointAt(0);

        do {
            do {
                if (!isEmojiCodepoint(codePoint)) {
                    return false;
                }
                offset = breakIterator.next();
                glue = text.charAt(offset - 1);
                codePoint = (offset < text.length()) ? text.codePointAt(offset) : 0;
            } while ((glue == 0x200d) || ((0x1f3fb <= codePoint) && (codePoint <= 0x1f3ff)));
            computedEmojiLength++;
            if (offset >= text.length()) {
                break;
            }
        } while (computedEmojiLength <= maxLength);

        return computedEmojiLength <= maxLength;
    }

    private static boolean isEmojiCodepoint(int codePoint) {
        if (codePoint >= 0x1f900 && codePoint <= 0x1faff) {
            return true;
        }
        if (codePoint >= 0x1f680 && codePoint <= 0x1f6ff) {
            return true;
        }
        if (codePoint >= 0x1f300 && codePoint <= 0x1f64f) {
            return true;
        }
        if (codePoint >= 0x1f1e6 && codePoint <= 0x1f1ff) {
            return true;
        }
        if (codePoint >= 0xe0020 && codePoint <= 0xe007f) {
            return true;
        }
        if (codePoint >= 0xfe00 && codePoint <= 0xfe0f) {
            return true;
        }
        if (codePoint >= 0x2194 && codePoint <= 0x2b55) {
            return true;
        }
        if (codePoint >= 0x20d0 && codePoint <= 0x20ff) {
            return true;
        }
        return codePoint == 0x200d;
    }

    public static final Pattern unAccentPattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    public static String unAccent(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return UCharacter.toLowerCase(unAccentPattern.matcher(Normalizer.normalize(source, Normalizer.Form.NFD)).replaceAll(""));
        } else {
            return unAccentPattern.matcher(Normalizer.normalize(source, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.getDefault());
        }
    }

    public static void setQrCodeImage(@NonNull ImageView imageView, @NonNull final String qrCodeData) {
        final WeakReference<ImageView> imageViewWeakReference = new WeakReference<>(imageView);
        App.runThread(() -> {
            try {
                HashMap<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.MARGIN, 0);

                switch (SettingsActivity.getQrCorrectionLevel()) {
                    case "L":
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
                        break;
                    case "Q":
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
                        break;
                    case "H":
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                        break;
                    case "M":
                    default:
                        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                }
                BitMatrix qrcode = new MultiFormatWriter().encode(qrCodeData, BarcodeFormat.QR_CODE, 0, 0, hints);
                int w = qrcode.getWidth();
                int h = qrcode.getHeight();
                int onColor = ContextCompat.getColor(App.getContext(), R.color.black);
                int offColor = Color.TRANSPARENT;

                int[] pixels = new int[h * w];
                int offset = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        pixels[offset++] = qrcode.get(x, y) ? onColor : offColor;
                    }
                }
                final Bitmap smallQrCodeBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                smallQrCodeBitmap.setPixels(pixels, 0, w, 0, 0, w, h);
                DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                final int size = Math.min(height, width);
                final ImageView imageView1 = imageViewWeakReference.get();
                if (imageView1 != null) {
                    new Handler(Looper.getMainLooper()).post(() -> imageView1.setImageBitmap(Bitmap.createScaledBitmap(smallQrCodeBitmap, size, size, false)));
                }
            } catch (Exception e) {
                final ImageView imageView1 = imageViewWeakReference.get();
                if (imageView1 != null) {
                    new Handler(Looper.getMainLooper()).post(() -> imageView1.setImageDrawable(null));
                }
                e.printStackTrace();
            }
        });
    }

    public static void refreshRegisterToPushNotification() {
        String token = AppSingleton.retrieveFirebaseToken();
        for (OwnedIdentity ownedIdentity : AppDatabase.getInstance().ownedIdentityDao().getAll()) {
            for (int i = 0; i < 5; i++) {
                try {
                    AppSingleton.getEngine().registerToPushNotification(ownedIdentity.bytesOwnedIdentity, token, false, false);
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class AppStartupTasks implements Runnable {

        @Override
        public void run() {
            //////////////////////////
            // initialize emoji2
            //////////////////////////
            EmojiCompat.Config emojiConfig = new BundledEmojiCompatConfig(getContext());
            emojiConfig.setReplaceAll(true);
            EmojiCompat.init(emojiConfig);

            //////////////////////////
            // start monitoring network status
            //////////////////////////
            NetworkStateMonitorReceiver.startMonitoringNetwork(getContext());


            //////////////////////////
            // notify Engine whether autobackup is on or not
            //////////////////////////
            AppSingleton.getEngine().setAutoBackupEnabled(SettingsActivity.useAutomaticBackup());


            //////////////////////////
            // register push notifications for all identities
            //////////////////////////
            refreshRegisterToPushNotification();

            //////////////////////////
            // start the MessageExpirationService (in case an alarm was missed)
            //////////////////////////
            Intent expirationIntent = new Intent(getContext(), MessageExpirationService.class);
            expirationIntent.setAction(MessageExpirationService.EXPIRE_MESSAGES_ACTION);
            getContext().sendBroadcast(expirationIntent);


            //////////////////////////
            // schedule all periodic tasks
            //////////////////////////
            PeriodicTasksScheduler.schedulePeriodicTasks(App.getContext());


            //////////////////////////
            // create and maintain dynamic shortcuts
            //////////////////////////
            ShortcutActivity.startPublishingShareTargets(getContext());


            ///////////////////////
            // clean the CAMERA_PICTURE_FOLDER
            ///////////////////////
            File cameraPictureCacheDir = new File(getApplication().getCacheDir(), App.CAMERA_PICTURE_FOLDER);
            //noinspection ResultOfMethodCallIgnored
            cameraPictureCacheDir.mkdirs();
            File[] files = cameraPictureCacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
            }

            ///////////////////////
            // clean the WEBCLIENT_ATTACHMENT_FOLDER
            ///////////////////////
            File attachments = new File(getApplication().getCacheDir(), App.WEBCLIENT_ATTACHMENT_FOLDER);
            //noinspection ResultOfMethodCallIgnored
            attachments.mkdirs();
            files = attachments.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
            }
        }
    }
}
