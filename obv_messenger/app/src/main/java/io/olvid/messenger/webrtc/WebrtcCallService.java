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

package io.olvid.messenger.webrtc;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.webrtc.NetworkMonitor;
import org.webrtc.NetworkMonitorAutoDetect;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.engine.engine.types.EngineAPI;
import io.olvid.engine.engine.types.EngineNotificationListener;
import io.olvid.engine.engine.types.EngineNotifications;
import io.olvid.engine.engine.types.ObvOutboundAttachment;
import io.olvid.engine.engine.types.ObvPostMessageOutput;
import io.olvid.engine.engine.types.ObvTurnCredentialsFailedReason;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.notifications.AndroidNotificationManager;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.BytesKey;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.entity.CallLogItem;
import io.olvid.messenger.databases.entity.CallLogItemContactJoin;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Message;

import static io.olvid.messenger.App.getContext;

public class WebrtcCallService extends Service {
    public static final String ACTION_START_CALL = "action_start_call";
    public static final String ACTION_ANSWER_CALL = "action_answer_call";
    public static final String ACTION_REJECT_CALL = "action_reject_call";
    public static final String ACTION_HANG_UP = "action_hang_up";
    public static final String ACTION_MESSAGE = "action_message";

    public static final String BYTES_OWNED_IDENTITY_INTENT_EXTRA = "bytes_owned_identity";
    public static final String BYTES_CONTACT_IDENTITY_INTENT_EXTRA = "bytes_contact_identity";
    public static final String SINGLE_CONTACT_IDENTITY_BUNDLE_KEY = "0";
    public static final String CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA = "contact_identities_bundle";
    public static final String BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA = "bytes_group_owner_and_uid";
    public static final String CALL_IDENTIFIER_INTENT_EXTRA = "call_identifier";
    public static final String MESSAGE_TYPE_INTENT_EXTRA = "message_type";
    public static final String SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA = "serialized_message_payload";

    private static final long CREDENTIALS_TTL = 43_200_000;
    private static final String PREF_KEY_TIMESTAMP = "timestamp";
    private static final String PREF_KEY_USERNAME1 = "username1";
    private static final String PREF_KEY_PASSWORD1 = "password1";
    private static final String PREF_KEY_USERNAME2 = "username2";
    private static final String PREF_KEY_PASSWORD2 = "password2";
    private static final String PREF_KEY_TURN_SERVERS = "turn_servers";


    public static final int SERVICE_ID = 9001;
    public static final int NOT_FOREGROUND_NOTIFICATION_ID = 9086;

    enum Role {
        NONE,
        CALLER,
        RECIPIENT
    }

    public enum State {
        INITIAL,
        WAITING_FOR_AUDIO_PERMISSION,
        GETTING_TURN_CREDENTIALS,
        INITIALIZING_CALL,
        RINGING,
        BUSY,
        CALL_IN_PROGRESS,
        CALL_ENDED,
        FAILED
    }

    public enum PeerState {
        INITIAL,
        ///////
        // the following states are caller-only states --> the recipient stays in INITIAL during this time
        START_CALL_MESSAGE_SENT,
        RINGING,
        BUSY,
        CALL_REJECTED,
        ///////
        CONNECTING_TO_PEER,
        CONNECTED,
        RECONNECTING,
        HANGED_UP,
        KICKED,
        TIMEOUT,
        FAILED
    }

    public enum FailReason {
        NONE,
        CONTACT_NOT_FOUND,
        SERVER_UNREACHABLE,
        PEER_CONNECTION_CREATION_ERROR,
        INTERNAL_ERROR,
        ICE_SERVER_CREDENTIALS_CREATION_ERROR,
        COULD_NOT_SEND,
        PERMISSION_DENIED,
        SERVER_AUTHENTICATION_ERROR,
        ICE_CONNECTION_ERROR,
        CALL_INITIATION_NOT_SUPPORTED,
        TIMEOUT,
        KICKED
    }

    public enum WakeLock {
        ALL,
        WIFI,
        PROXIMITY
    }

    public enum AudioOutput {
        PHONE,
        HEADSET,
        LOUDSPEAKER,
        BLUETOOTH
    }

    public static final int START_CALL_MESSAGE_TYPE = 0;
    public static final int ANSWER_CALL_MESSAGE_TYPE = 1;
    public static final int REJECT_CALL_MESSAGE_TYPE = 2;
    public static final int HANGED_UP_MESSAGE_TYPE = 3;
    public static final int RINGING_MESSAGE_TYPE = 4;
    public static final int BUSY_MESSAGE_TYPE = 5;
    public static final int RECONNECT_CALL_MESSAGE_TYPE = 6;
    public static final int NEW_PARTICIPANT_OFFER_MESSAGE_TYPE = 7;
    public static final int NEW_PARTICIPANT_ANSWER_MESSAGE_TYPE = 8;
    public static final int KICK_MESSAGE_TYPE = 9;

    public static final int MUTED_DATA_MESSAGE_TYPE = 0;
    public static final int UPDATE_PARTICIPANTS_DATA_MESSAGE_TYPE = 1;
    public static final int RELAY_DATA_MESSAGE_TYPE = 2;
    public static final int RELAYED_DATA_MESSAGE_TYPE = 3;
    public static final int HANGED_UP_DATA_MESSAGE_TYPE = 4;

    public static final long CALL_TIMEOUT_MILLIS = 30_000;
    public static final long RINGING_TIMEOUT_MILLIS = 60_000;
    public static final long CALL_CONNECTION_TIMEOUT_MILLIS = 10_000;
    public static final long PEER_CALL_ENDED_WAIT_MILLIS = 3_000;

    private final WebrtcCallServiceBinder webrtcCallServiceBinder = new WebrtcCallServiceBinder();
    private final NetworkMonitorObserver networkMonitorObserver = new NetworkMonitorObserver();
    private final WiredHeadsetReceiver wiredHeadsetReceiver = new WiredHeadsetReceiver();
    private final ObjectMapper objectMapper = AppSingleton.getJsonObjectMapper();

    private Role role = Role.NONE;
    UUID callIdentifier = null;
    byte[] bytesOwnedIdentity = null;
    byte[] bytesGroupOwnerAndUid = null;
    private State state = State.INITIAL;
    private FailReason failReason = FailReason.NONE;
    private final MutableLiveData<State> stateLiveData = new MutableLiveData<>(state);
    boolean microphoneMuted = false;
    private final MutableLiveData<Boolean> microphoneMutedLiveData = new MutableLiveData<>(false);
    AudioOutput selectedAudioOutput = AudioOutput.PHONE;
    private final MutableLiveData<AudioOutput> selectedAudioOutputLiveData = new MutableLiveData<>(selectedAudioOutput);
    boolean bluetoothAutoConnect = true;
    boolean wiredHeadsetConnected = false;
    List<AudioOutput> availableAudioOutputs = Arrays.asList(AudioOutput.PHONE, AudioOutput.LOUDSPEAKER);
    private final MutableLiveData<List<AudioOutput>> availableAudioOutputsLiveData = new MutableLiveData<>(availableAudioOutputs);
    Timer callDurationTimer = null;
    private final MutableLiveData<Integer> callDuration = new MutableLiveData<>(null);
    private final HashMap<BytesKey, JsonNewParticipantOfferMessage> receivedOfferMessages = new HashMap<>();

    private int callParticipantIndex = 0;
    private final Map<BytesKey, Integer> callParticipantIndexes = new HashMap<>();
    private final Map<Integer, CallParticipant> callParticipants = new TreeMap<>();
    private final MutableLiveData<List<CallParticipantPojo>> callParticipantsLiveData = new MutableLiveData<>(new ArrayList<>(0));


    final Timer timeoutTimer = new Timer();
    TimerTask timeoutTimerTask = null;
    PowerManager.WakeLock proximityLock = null;
    WifiManager.WifiLock wifiLock = null;

    private final NoExceptionSingleThreadExecutor executor = new NoExceptionSingleThreadExecutor("WebRTCCallService-Executor");
    boolean initialized = false;
    private int savedAudioManagerMode;
    AudioManager audioManager;
    private IncomingCallRinger incomingCallRinger;
    private OutgoingCallRinger outgoingCallRinger;
    private SoundPool soundPool;
    private int connectSound;
    private int disconnectSound;
    private PhoneCallStateListener phoneCallStateListener;
    private ScreenOffReceiver screenOffReceiver;

    AudioFocusRequestCompat audioFocusRequest;
    private BluetoothHeadsetManager bluetoothHeadsetManager;

    private CallLogItem callLogItem = null;

    private WebrtcMessageReceivedBroadcastReceiver webrtcMessageReceivedBroadcastReceiver = null;
    private EngineTurnCredentialsReceiver engineTurnCredentialsReceiver = null;

    private String turnUserName;
    private String turnPassword;
    private List<String> turnServers;
    private int incomingParticipantCount;

    private String recipientTurnUserName;
    private String recipientTurnPassword;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null) {

            initialize();

            switch (intent.getAction()) {
                case ACTION_START_CALL: {
                    if (!intent.hasExtra(CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA) || !intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)) {
                        break;
                    }
                    byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
                    Bundle contactIdentitiesBundle = intent.getBundleExtra(CONTACT_IDENTITIES_BUNDLE_INTENT_EXTRA);
                    if (bytesOwnedIdentity == null || contactIdentitiesBundle == null) {
                        break;
                    }
                    List<byte[]> bytesContactIdentities = new ArrayList<>(contactIdentitiesBundle.size());
                    for (String key : contactIdentitiesBundle.keySet()) {
                        bytesContactIdentities.add(contactIdentitiesBundle.getByteArray(key));
                    }
                    byte[] bytesGroupOwnerAndUid = intent.getByteArrayExtra(BYTES_GROUP_OWNER_AND_UID_INTENT_EXTRA);

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        callerStartCall(bytesOwnedIdentity, bytesContactIdentities, bytesGroupOwnerAndUid);
                    } else {
                        callerWaitForAudioPermission(bytesOwnedIdentity, bytesContactIdentities, bytesGroupOwnerAndUid);
                    }

                    return START_NOT_STICKY;
                }
                case ACTION_MESSAGE: {
                    if (!intent.hasExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA) || !intent.hasExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA)
                            || !intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA) || !intent.hasExtra(MESSAGE_TYPE_INTENT_EXTRA)
                            || !intent.hasExtra(SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA)) {
                        break;
                    }
                    int messageType = intent.getIntExtra(MESSAGE_TYPE_INTENT_EXTRA, -1);
                    if (messageType != START_CALL_MESSAGE_TYPE) {
                        break;
                    }
                    byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
                    byte[] bytesContactIdentity = intent.getByteArrayExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA);
                    UUID callIdentifier = UUID.fromString(intent.getStringExtra(CALL_IDENTIFIER_INTENT_EXTRA));
                    String serializedMessagePayload = intent.getStringExtra(SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA);
                    if (serializedMessagePayload == null || callIdentifier == null) {
                        break;
                    }
                    try {
                        JsonStartCallMessage startCallMessage = objectMapper.readValue(serializedMessagePayload, JsonStartCallMessage.class);

                        recipientReceiveCall(bytesOwnedIdentity, bytesContactIdentity, callIdentifier, startCallMessage.sessionDescriptionType, startCallMessage.gzippedSessionDescription, startCallMessage.turnUserName, startCallMessage.turnPassword, startCallMessage.turnServers, startCallMessage.participantCount, startCallMessage.getBytesGroupOwnerAndUid());

                        return START_NOT_STICKY;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ACTION_ANSWER_CALL: {
                    if (!intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA)) {
                        break;
                    }
                    UUID callIdentifier = UUID.fromString(intent.getStringExtra(CALL_IDENTIFIER_INTENT_EXTRA));

                    boolean audioPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                    recipientAnswerCall(callIdentifier, !audioPermissionGranted);

                    return START_NOT_STICKY;
                }
                case ACTION_REJECT_CALL: {
                    if (!intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA)) {
                        break;
                    }
                    UUID callIdentifier = UUID.fromString(intent.getStringExtra(CALL_IDENTIFIER_INTENT_EXTRA));

                    recipientRejectCall(callIdentifier);

                    return START_NOT_STICKY;
                }
                case ACTION_HANG_UP: {
                    if (!intent.hasExtra(CALL_IDENTIFIER_INTENT_EXTRA)) {
                        break;
                    }
                    UUID callIdentifier = UUID.fromString(intent.getStringExtra(CALL_IDENTIFIER_INTENT_EXTRA));

                    hangUpCall(callIdentifier);

                    return START_NOT_STICKY;
                }
            }
        }
        handleUnknownOrInvalidIntent();
        return START_NOT_STICKY;
    }

    private void handleMessageIntent(Intent intent) {
        executor.execute(() -> {
            byte[] bytesOwnedIdentity = intent.getByteArrayExtra(BYTES_OWNED_IDENTITY_INTENT_EXTRA);
            byte[] bytesContactIdentity = intent.getByteArrayExtra(BYTES_CONTACT_IDENTITY_INTENT_EXTRA);
            UUID callIdentifier = UUID.fromString(intent.getStringExtra(CALL_IDENTIFIER_INTENT_EXTRA));
            // if the message is for another call, ignore it
            if (!Arrays.equals(bytesOwnedIdentity, this.bytesOwnedIdentity) ||
                    !callIdentifier.equals(this.callIdentifier)) {
                return;
            }

            int messageType = intent.getIntExtra(MESSAGE_TYPE_INTENT_EXTRA, -1);
            String serializedMessagePayload = intent.getStringExtra(SERIALIZED_MESSAGE_PAYLOAD_INTENT_EXTRA);
            // if message does not contain a payload, ignore it
            if (serializedMessagePayload == null) {
                return;
            }
            handleMessage(bytesContactIdentity, messageType, serializedMessagePayload);
        });
    }

    private void handleMessage(byte[] bytesContactIdentity, int messageType, String serializedMessagePayload) {
        try {
            switch (messageType) {
                case ANSWER_CALL_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (isCaller() && callParticipant != null) {
                        JsonAnswerCallMessage jsonAnswerCallMessage = objectMapper.readValue(serializedMessagePayload, JsonAnswerCallMessage.class);
                        callerHandleAnswerCallMessage(callParticipant, jsonAnswerCallMessage.getSessionDescriptionType(), jsonAnswerCallMessage.getGzippedSessionDescription());
                    }
                    break;
                }
                case RINGING_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (isCaller() && callParticipant != null) {
                        callerHandleRingingMessage(callParticipant);
                    }
                    break;
                }
                case REJECT_CALL_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (isCaller() && callParticipant != null) {
                        callerHandleRejectCallMessage(callParticipant);
                    }
                    break;
                }
                case HANGED_UP_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (callParticipant != null) {
                        handleHangedUpMessage(callParticipant);
                    }
                    break;
                }
                case BUSY_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (isCaller() && callParticipant != null) {
                        callerHandleBusyMessage(callParticipant);
                    }
                    break;
                }
                case RECONNECT_CALL_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (callParticipant != null) {
                        JsonReconnectCallMessage jsonReconnectCallMessage = objectMapper.readValue(serializedMessagePayload, JsonReconnectCallMessage.class);
                        handleReconnectCallMessage(callParticipant, jsonReconnectCallMessage.getSessionDescriptionType(), jsonReconnectCallMessage.getGzippedSessionDescription(), jsonReconnectCallMessage.reconnectCounter, jsonReconnectCallMessage.peerReconnectCounterToOverride);
                    }
                    break;
                }
                case NEW_PARTICIPANT_OFFER_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    JsonNewParticipantOfferMessage newParticipantOfferMessage = objectMapper.readValue(serializedMessagePayload, JsonNewParticipantOfferMessage.class);
                    if (callParticipant == null) {
                        // put the message in queue as we might simply receive the update call participant message later
                        receivedOfferMessages.put(new BytesKey(bytesContactIdentity), newParticipantOfferMessage);
                    } else {
                        handleNewParticipantOfferMessage(callParticipant, newParticipantOfferMessage.getSessionDescriptionType(), newParticipantOfferMessage.getGzippedSessionDescription());
                    }
                    break;
                }
                case NEW_PARTICIPANT_ANSWER_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (callParticipant != null) {
                        JsonNewParticipantAnswerMessage newParticipantAnswerMessage = objectMapper.readValue(serializedMessagePayload, JsonNewParticipantAnswerMessage.class);
                        handleNewParticipantAnswerMessage(callParticipant, newParticipantAnswerMessage.getSessionDescriptionType(), newParticipantAnswerMessage.getGzippedSessionDescription());
                    }
                    break;
                }
                case KICK_MESSAGE_TYPE: {
                    CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                    if (callParticipant != null && callParticipant.role == Role.CALLER) {
                        handleKickedMessage();
                    }
                    break;
                }
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void stopThisService() {
        executor.shutdownNow();
        stopForeground(true);
        new Handler(Looper.getMainLooper()).postDelayed(this::stopSelf, 300);
    }


    // region Steps

    private void initialize() {
        executor.execute(() -> {
            if (!initialized) {
                initialized = true;
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager == null) {
                    setFailReason(FailReason.INTERNAL_ERROR);
                    setState(State.FAILED);
                    return;
                }
                incomingCallRinger = new IncomingCallRinger(this);
                outgoingCallRinger = new OutgoingCallRinger(this);
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(2)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                                .build())
                        .build();
                connectSound = soundPool.load(this, R.raw.connect, 1);
                disconnectSound = soundPool.load(this, R.raw.disconnect, 1);

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    readCallStatePermissionGranted();
                }

                if ((android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothPermissionGranted();
                }

                audioManager.setSpeakerphoneOn(false);
                //noinspection deprecation
                wiredHeadsetConnected = audioManager.isWiredHeadsetOn();
                updateAvailableAudioOutputsList();

                registerWiredHeadsetReceiver();

                NetworkMonitor networkMonitor = NetworkMonitor.getInstance();
                networkMonitor.startMonitoring(App.getContext());
                networkMonitor.addObserver(networkMonitorObserver);
            }
        });
    }

    private void handleUnknownOrInvalidIntent() {
        executor.execute(() -> {
            if (state == State.INITIAL) {
                // we received an unknown intent and no call has been started
                // --> we can safely stop the service
                stopForeground(true);
                stopSelf();
            }
        });
    }

    private void callerStartCall(@NonNull byte[] bytesOwnedIdentity, @NonNull List<byte[]> bytesContactIdentities, @Nullable byte[] bytesGroupOwnerAndUid) {
        executor.execute(() -> {
            if (state != State.INITIAL) {
                App.toast(R.string.toast_message_already_in_a_call, Toast.LENGTH_SHORT);
                return;
            }

            UUID callIdentifier = UUID.randomUUID();
            boolean allContactsFound = setContactAndRole(bytesOwnedIdentity, bytesContactIdentities, callIdentifier, true);
            this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;

            if (!allContactsFound) {
                setFailReason(FailReason.CONTACT_NOT_FOUND);
                setState(State.FAILED);
                return;
            }

            // show notification
            showOngoingForeground();

            callerStartCallInternal();
        });
    }

    private void callerWaitForAudioPermission(@NonNull byte[] bytesOwnedIdentity, @NonNull List<byte[]> bytesContactIdentities, @Nullable byte[] bytesGroupOwnerAndUid) {
        executor.execute(() -> {
            if (state != State.INITIAL) {
                App.toast(R.string.toast_message_already_in_a_call, Toast.LENGTH_SHORT);
                return;
            }

            UUID callIdentifier = UUID.randomUUID();
            boolean allContactsFound = setContactAndRole(bytesOwnedIdentity, bytesContactIdentities, callIdentifier, true);
            this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;

            if (!allContactsFound) {
                setFailReason(FailReason.CONTACT_NOT_FOUND);
                setState(State.FAILED);
                return;
            }

            setState(State.WAITING_FOR_AUDIO_PERMISSION);
        });
    }

    void audioPermissionGranted() {
        executor.execute(() -> {
            if (state != State.WAITING_FOR_AUDIO_PERMISSION) {
                return;
            }

            if (isCaller()) {
                callerStartCallInternal();
            } else {
                recipientAnswerCallInternal();
            }
        });
    }

    void bluetoothPermissionGranted() {
        executor.execute(() -> {
            if (bluetoothHeadsetManager == null) {
                bluetoothHeadsetManager = new BluetoothHeadsetManager(this);
                bluetoothHeadsetManager.start();
            }
        });
    }

    void readCallStatePermissionGranted() {
        executor.execute(() -> {
            if (phoneCallStateListener == null) {
                new Handler(Looper.getMainLooper()).post(() -> phoneCallStateListener = new PhoneCallStateListener(this, executor));
            }
        });
    }

    private void callerStartCallInternal() {
        // get audio focus
        requestAudioManagerFocus();

        // initialize a peerConnection
        WebrtcPeerConnectionHolder.initializePeerConnectionFactory();

        // check if we have cached some turn credentials:
        SharedPreferences callCredentialsCacheSharedPreference = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_call_credentials_cache), Context.MODE_PRIVATE);
        long credentialTimestamp = callCredentialsCacheSharedPreference.getLong(PREF_KEY_TIMESTAMP, 0);
        if (System.currentTimeMillis() < credentialTimestamp + CREDENTIALS_TTL) {
            String username1 = callCredentialsCacheSharedPreference.getString(PREF_KEY_USERNAME1, null);
            String password1 = callCredentialsCacheSharedPreference.getString(PREF_KEY_PASSWORD1, null);
            String username2 = callCredentialsCacheSharedPreference.getString(PREF_KEY_USERNAME2, null);
            String password2 = callCredentialsCacheSharedPreference.getString(PREF_KEY_PASSWORD2, null);
            Set<String> turnServers = callCredentialsCacheSharedPreference.getStringSet(PREF_KEY_TURN_SERVERS, null);
            if (username1 != null && password1 != null
                    && username2 != null && password2 != null
                    && turnServers != null) {
                Logger.d("☎️ Reusing cached turn credentials");
                setState(State.GETTING_TURN_CREDENTIALS);
                callerSetTurnCredentialsAndInitializeCall(username1, password1, username2, password2, new ArrayList<>(turnServers));
                return;
            }
        }

        Logger.d("☎️ Requesting new turn credentials");
        // request turn credentials
        setState(State.GETTING_TURN_CREDENTIALS);
        AppSingleton.getEngine().getTurnCredentials(bytesOwnedIdentity, callIdentifier, "caller", "recipient");
    }

    void clearCredentialsCache() {
        executor.execute(() -> {
            if (isCaller() && state == State.INITIALIZING_CALL) {
                Logger.d("☎️ Clearing cached turn credentials");
                SharedPreferences callCredentialsCacheSharedPreference = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_call_credentials_cache), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = callCredentialsCacheSharedPreference.edit();
                editor.clear();
                editor.apply();
            }
        });
    }

    private void callerSetTurnCredentialsAndInitializeCall(String callerUsername, String callerPassword, String recipientUsername, String recipientPassword, List<String> turnServers) {
        executor.execute(() -> {
            if (state != State.GETTING_TURN_CREDENTIALS) {
                return;
            }
            this.turnUserName = callerUsername;
            this.turnPassword = callerPassword;
            this.turnServers = turnServers;
            this.recipientTurnUserName = recipientUsername;
            this.recipientTurnPassword = recipientPassword;
            for (CallParticipant callParticipant: callParticipants.values()) {
                callParticipant.peerConnectionHolder.setTurnCredentials(callerUsername, callerPassword, turnServers);
                callParticipant.peerConnectionHolder.createPeerConnection();
                if (callParticipant.peerConnectionHolder.peerConnection == null) {
                    setFailReason(FailReason.PEER_CONNECTION_CREATION_ERROR);
                    setState(State.FAILED);
                    return;
                }
                callParticipant.peerConnectionHolder.createOffer();
            }

            setState(State.INITIALIZING_CALL);
        });
    }

    private void callerFailedTurnCredentials(ObvTurnCredentialsFailedReason rfc) {
        executor.execute(() -> {
            switch (rfc) {
                case BAD_SERVER_SESSION:
                    setFailReason(FailReason.SERVER_AUTHENTICATION_ERROR);
                    break;
                case UNABLE_TO_CONTACT_SERVER:
                    setFailReason(FailReason.SERVER_UNREACHABLE);
                    break;
                case PERMISSION_DENIED:
                    setFailReason(FailReason.PERMISSION_DENIED);
                    App.openAppDialogSubscriptionRequired(bytesOwnedIdentity, EngineAPI.ApiKeyPermission.CALL);
                    break;
                case CALLS_NOT_SUPPORTED_ON_SERVER:
                    setFailReason(FailReason.CALL_INITIATION_NOT_SUPPORTED);
                    App.openAppDialogCallInitiationNotSupported(bytesOwnedIdentity);
                    break;
            }
            setState(State.FAILED);
        });
    }

    void sendLocalDescriptionToPeer(CallParticipant callParticipant, String sdpType, String sdpDescription, int reconnectCounter, int peerReconnectCounterToOverride) {
        executor.execute(() -> {
            if (!callParticipantIndexes.containsKey(new BytesKey(callParticipant.bytesContactIdentity))) {
                return;
            }

            try {
                if (callParticipant.peerState == PeerState.INITIAL) {
                    Logger.d("☎️ Sending peer the following sdp [" + sdpType + "]\n" + sdpDescription);
                    if (isCaller()) {
                        if (sendStartCallMessage(callParticipant, sdpType, sdpDescription, recipientTurnUserName, recipientTurnPassword, turnServers)) {
                            callParticipant.setPeerState(PeerState.START_CALL_MESSAGE_SENT);
                        } else {
                            callParticipant.setPeerState(PeerState.FAILED);
                        }
                    } else {
                        if (callParticipant.role == Role.CALLER) {
                            sendAnswerCallMessage(callParticipant, sdpType, sdpDescription);
                            callParticipant.setPeerState(PeerState.CONNECTING_TO_PEER);
                        } else if (shouldISendTheOfferToCallParticipant(callParticipant)) {
                            sendNewParticipantOfferMessage(callParticipant, sdpType, sdpDescription);
                            callParticipant.setPeerState(PeerState.START_CALL_MESSAGE_SENT);
                        } else {
                            sendNewParticipantAnswerMessage(callParticipant, sdpType, sdpDescription);
                            callParticipant.setPeerState(PeerState.CONNECTING_TO_PEER);
                        }
                    }
                } else if (callParticipant.peerState == PeerState.CONNECTED || callParticipant.peerState == PeerState.RECONNECTING) {
                    Logger.d("☎️ Sending peer the following restart sdp [" + sdpType + "]\n" + sdpDescription);
                    sendReconnectCallMessage(callParticipant, sdpType, sdpDescription, reconnectCounter, peerReconnectCounterToOverride);
                }
            } catch (IOException e) {
                setFailReason(FailReason.INTERNAL_ERROR);
                setState(State.FAILED);
                e.printStackTrace();
            }
        });
    }

    void callerHandleRingingMessage(@NonNull CallParticipant callParticipant) {
        executor.execute(() -> {
            if (callParticipant.peerState != PeerState.START_CALL_MESSAGE_SENT) {
                return;
            }
            callParticipant.setPeerState(PeerState.RINGING);

            if (state == State.INITIALIZING_CALL || state == State.BUSY) {
                outgoingCallRinger.ring(OutgoingCallRinger.Type.RING);
                setState(State.RINGING);
            }
        });
    }

    void callerHandleBusyMessage(@NonNull CallParticipant callParticipant) {
        executor.execute(() -> {
            if (callParticipant.peerState != PeerState.START_CALL_MESSAGE_SENT) {
                return;
            }
            createLogEntry(CallLogItem.STATUS_BUSY);

            callParticipant.setPeerState(PeerState.BUSY);

            if (state == State.INITIALIZING_CALL) {
                outgoingCallRinger.ring(OutgoingCallRinger.Type.BUSY);
                setState(State.BUSY);
            }
        });
    }

    void callerHandleAnswerCallMessage(@NonNull CallParticipant callParticipant, String peerSdpType, byte[] gzippedPeerSdpDescription) {
        executor.execute(() -> {
            if (callParticipant.peerState != PeerState.START_CALL_MESSAGE_SENT && callParticipant.peerState != PeerState.RINGING) {
                return;
            }
            if (state == State.RINGING || state == State.BUSY) {
                outgoingCallRinger.stop();
            }

            String peerSdpDescription;
            try {
                peerSdpDescription = gunzip(gzippedPeerSdpDescription);
            } catch (IOException e) {
                setFailReason(FailReason.INTERNAL_ERROR);
                setState(State.FAILED);
                e.printStackTrace();
                return;
            }

            callParticipant.peerConnectionHolder.setPeerSessionDescription(peerSdpType, peerSdpDescription);
            callParticipant.peerConnectionHolder.finishEstablishingConnection();
            callParticipant.setPeerState(PeerState.CONNECTING_TO_PEER);
        });
    }

    void callerHandleRejectCallMessage(@NonNull CallParticipant callParticipant) {
        executor.execute(() -> {
            if (callParticipant.peerState != PeerState.START_CALL_MESSAGE_SENT && callParticipant.peerState != PeerState.RINGING) {
                return;
            }
            callParticipant.setPeerState(PeerState.CALL_REJECTED);

            updateStateFromPeerStates();
        });
    }

    void handleHangedUpMessage(@NonNull CallParticipant callParticipant) {
        executor.execute(() -> {
            callParticipant.setPeerState(PeerState.HANGED_UP);

            updateStateFromPeerStates();
        });
    }


    void hangUpCall(UUID callIdentifier) {
        executor.execute(() -> {
            if (!this.callIdentifier.equals(callIdentifier)) {
                return;
            }
            hangUpCallInternal();
        });
    }

    void hangUpCall() {
        executor.execute(this::hangUpCallInternal);
    }

    private void hangUpCallInternal() {
        // notify peer that you hung up (it's not just a connection loss)
        sendHangedUpMessage(callParticipants.values());
        if (soundPool != null) {
            soundPool.play(disconnectSound, 1, 1, 0, 0, 1);
        }

        createLogEntry(CallLogItem.STATUS_MISSED);
        setState(State.CALL_ENDED);
        stopThisService();
    }


    void recipientReceiveCall(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, UUID callIdentifier, String peerSdpType, byte[] gzippedPeerSdpDescription, String turnUsername, String turnPassword, @Nullable List<String> turnServers, int participantCount, @Nullable byte[] bytesGroupOwnerAndUid) {
         executor.execute(() -> {
            if (state != State.INITIAL && !callIdentifier.equals(this.callIdentifier)) {
                sendBusyMessage(bytesOwnedIdentity, bytesContactIdentity, callIdentifier, bytesGroupOwnerAndUid);
                return;
            }

            String peerSdpDescription;
            try {
                peerSdpDescription = gunzip(gzippedPeerSdpDescription);
            } catch (IOException e) {
                setFailReason(FailReason.INTERNAL_ERROR);
                setState(State.FAILED);
                e.printStackTrace();
                return;
            }

            setContactAndRole(bytesOwnedIdentity, Collections.singletonList(bytesContactIdentity), callIdentifier, false);
            CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);

            if (callParticipant == null || callParticipant.contact == null) {
                setFailReason(FailReason.CONTACT_NOT_FOUND);
                setState(State.FAILED);
                return;
            }

            this.bytesGroupOwnerAndUid = bytesGroupOwnerAndUid;
            this.turnUserName = turnUsername;
            this.turnPassword = turnPassword;
            this.incomingParticipantCount = participantCount;
            callParticipant.peerConnectionHolder.setPeerSessionDescription(peerSdpType, peerSdpDescription);
            callParticipant.peerConnectionHolder.setTurnCredentials(turnUsername, turnPassword, turnServers);

            showIncomingCallForeground(callParticipant.contact, participantCount);
            sendRingingMessage(callParticipant);
            registerScreenOffReceiver();
            incomingCallRinger.ring(callIdentifier);

            setState(State.RINGING);
        });
    }


    void recipientAnswerCall(UUID callIdentifier, boolean waitForAudioPermission) {
        executor.execute(() -> {
            if (state != State.RINGING || !this.callIdentifier.equals(callIdentifier)) {
                return;
            }

            // stop ringing and listening for power button
            incomingCallRinger.stop();
            unregisterScreenOffReceiver();

            // remove notification in case previous starting foreground failed
            try {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                notificationManager.cancel(WebrtcCallService.NOT_FOREGROUND_NOTIFICATION_ID);
            } catch (Exception e) {
                // do nothing
            }
            showOngoingForeground();

            if (waitForAudioPermission) {
                setState(State.WAITING_FOR_AUDIO_PERMISSION);
            } else {
                recipientAnswerCallInternal();
            }
        });
    }


    private void recipientAnswerCallInternal() {
        // get audio focus
        requestAudioManagerFocus();

        // initialize the peer connection factory
        WebrtcPeerConnectionHolder.initializePeerConnectionFactory();

        CallParticipant callerCallParticipant = getCallerCallParticipant();
        if(callerCallParticipant == null) {
            setFailReason(FailReason.CONTACT_NOT_FOUND);
            setState(State.FAILED);
            return;
        }

        // turn credentials have already been set in recipientReceiveCall step, so we can create the peer connection
        callerCallParticipant.peerConnectionHolder.createPeerConnection();

        if (callerCallParticipant.peerConnectionHolder.peerConnection == null) {
            setFailReason(FailReason.PEER_CONNECTION_CREATION_ERROR);
            setState(State.FAILED);
            return;
        }

        // create the Answer
        callerCallParticipant.peerConnectionHolder.createAnswer();

        setState(State.INITIALIZING_CALL);
    }


    void recipientRejectCall(UUID callIdentifier) {
        executor.execute(() -> {
            if (state != State.RINGING || !this.callIdentifier.equals(callIdentifier)) {
                return;
            }

            rejectCallInternal();
        });
    }

    void recipientRejectCall() {
        executor.execute(() -> {
            if (state != State.RINGING) {
                return;
            }

            rejectCallInternal();
        });
    }

    private void rejectCallInternal() {
        // stop ringing and listening for power button
        incomingCallRinger.stop();
        unregisterScreenOffReceiver();

        CallParticipant callerCallParticipant = getCallerCallParticipant();
        if(callerCallParticipant == null) {
            setFailReason(FailReason.CONTACT_NOT_FOUND);
            setState(State.FAILED);
            return;
        }

        // notify peer of rejected call
        sendRejectCallMessage(callerCallParticipant);

        // create log entry
        createLogEntry(CallLogItem.STATUS_MISSED);

        callerCallParticipant.setPeerState(PeerState.CALL_REJECTED);
        setState(State.CALL_ENDED);
        stopThisService();
    }


    void handleTimeout() {
        executor.execute(() -> {
            if (state == State.RINGING) {
                createLogEntry(CallLogItem.STATUS_MISSED);
                sendHangedUpMessage(callParticipants.values());
                if (soundPool != null) {
                    soundPool.play(disconnectSound, 1, 1, 0, 0, 1);
                }
            }
            setFailReason(FailReason.TIMEOUT);
            setState(State.FAILED);
        });
    }

    void peerConnectionHolderFailed(FailReason failReason) {
        executor.execute(() -> {
            setFailReason(failReason);
            setState(State.FAILED);
        });
    }

    void reconnectAfterConnectionLoss(CallParticipant callParticipant) {
        executor.execute(() -> {
            callParticipant.setPeerState(PeerState.RECONNECTING);
            updateStateFromPeerStates();

            callParticipant.peerConnectionHolder.createRestartOffer();
        });
    }

    void peerConnectionConnected(CallParticipant callParticipant) {
        executor.execute(() -> {
            PeerState oldState = callParticipant.peerState;
            callParticipant.setPeerState(PeerState.CONNECTED);
            if (callParticipant.timeoutTask != null) {
                callParticipant.timeoutTask.cancel();
                callParticipant.timeoutTask = null;
            }

            if (isCaller() &&
                    oldState != PeerState.CONNECTED &&
                    oldState != PeerState.RECONNECTING) {
                JsonUpdateParticipantsInnerMessage message = new JsonUpdateParticipantsInnerMessage(callParticipants.values());
                for (CallParticipant callPart: callParticipants.values()) {
                    if (!callPart.equals(callParticipant)) {
                        sendDataChannelMessage(callPart, message);
                    }
                }
            }


            if (state == State.CALL_IN_PROGRESS) {
                return;
            }
            if (timeoutTimerTask != null) {
                timeoutTimerTask.cancel();
                timeoutTimerTask = null;
            }


            acquireWakeLock(WakeLock.WIFI);
            createLogEntry(CallLogItem.STATUS_SUCCESSFUL);
            if (soundPool != null) {
                soundPool.play(connectSound, 1, 1, 0, 0, 1);
            }

            if (callDurationTimer != null) {
                callDurationTimer = null;
            }
            callDuration.postValue(0);
            callDurationTimer = new Timer();
            callDurationTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Integer duration = callDuration.getValue();
                    if (duration == null) {
                        duration = 0;
                    }
                    callDuration.postValue(duration + 1);
                }
            }, 0, 1000);

            setState(State.CALL_IN_PROGRESS);
        });
    }

    public void callerAddCallParticipants(List<Contact> contactsToAdd) {
        executor.execute(() -> {
            if (!isCaller()) {
                return;
            }
            List<CallParticipant> newCallParticipants = new ArrayList<>();
            for (Contact contact: contactsToAdd) {
                if (!Arrays.equals(contact.bytesOwnedIdentity, bytesOwnedIdentity)) {
                    Logger.w("☎️ Trying to add contact to call for a different ownedIdentity");
                    continue;
                }
                if (getCallParticipant(contact.bytesContactIdentity) != null) {
                    Logger.w("☎️ Trying to add contact to call which is already in the call");
                    continue;
                }
                CallParticipant callParticipant = new CallParticipant(contact, Role.RECIPIENT);

                newCallParticipants.add(callParticipant);
                if (state != State.INITIAL
                        && state != State.WAITING_FOR_AUDIO_PERMISSION
                        && state != State.GETTING_TURN_CREDENTIALS) { // only create the peer if the turn credentials were already retrieved
                    callParticipant.peerConnectionHolder.setTurnCredentials(this.turnUserName, this.turnPassword, this.turnServers);
                    callParticipant.peerConnectionHolder.createPeerConnection();
                    if (callParticipant.peerConnectionHolder.peerConnection == null) {
                        setFailReason(FailReason.PEER_CONNECTION_CREATION_ERROR);
                        setState(State.FAILED);
                        return;
                    }
                    callParticipant.peerConnectionHolder.createOffer();
                }

                callParticipantIndexes.put(new BytesKey(callParticipant.bytesContactIdentity), callParticipantIndex);
                callParticipants.put(callParticipantIndex, callParticipant);
                callParticipantIndex++;
            }
            notifyCallParticipantsChanged();

            updateLogEntry(newCallParticipants);
        });
    }

    void callerKickParticipant(byte[] bytesContactIdentity) {
        executor.execute(() -> {
            if (!isCaller()) {
                return;
            }
            CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
            if (callParticipant != null) {
                internalRemoveCallParticipant(callParticipant);

                sendKickMessage(callParticipant);

                JsonUpdateParticipantsInnerMessage message = new JsonUpdateParticipantsInnerMessage(callParticipants.values());
                for (CallParticipant callPart : callParticipants.values()) {
                    sendDataChannelMessage(callPart, message);
                }
            }
        });
    }

    private void internalRemoveCallParticipant(@NonNull CallParticipant callParticipant) {
        callParticipant.peerConnectionHolder.cleanUp();
        Integer index = callParticipantIndexes.remove(new BytesKey(callParticipant.bytesContactIdentity));
        if (index != null) {
            callParticipants.remove(index);
        } else {
            Logger.w("☎️ Calling removeCallParticipant for participant not in the call");
        }
        notifyCallParticipantsChanged();
    }

    void handleReconnectCallMessage(@NonNull CallParticipant callParticipant, String peerSdpType, byte[] gzippedPeerSdpDescription, int reconnectCounter, int peerReconnectCounterToOverride) {
        executor.execute(() -> {
            String peerSdpDescription;
            try {
                peerSdpDescription = gunzip(gzippedPeerSdpDescription);
            } catch (IOException e) {
                setFailReason(FailReason.INTERNAL_ERROR);
                setState(State.FAILED);
                e.printStackTrace();
                return;
            }

            callParticipant.peerConnectionHolder.handleReceivedRestartSdp(peerSdpType, peerSdpDescription, reconnectCounter, peerReconnectCounterToOverride);
        });
    }

    private void handleNewParticipantOfferMessage(CallParticipant callParticipant, String sessionDescriptionType, byte[] gzippedPeerSdpDescription) {
        executor.execute(() -> {
            if (callParticipant.role != Role.RECIPIENT || shouldISendTheOfferToCallParticipant(callParticipant)) {
                return;
            }
            String peerSdpDescription;
            try {
                peerSdpDescription = gunzip(gzippedPeerSdpDescription);
            } catch (IOException e) {
                callParticipant.setPeerState(PeerState.HANGED_UP);
                e.printStackTrace();
                return;
            }
            callParticipant.peerConnectionHolder.setPeerSessionDescription(sessionDescriptionType, peerSdpDescription);
            callParticipant.peerConnectionHolder.setTurnCredentials(turnUserName, turnPassword, turnServers);

            callParticipant.peerConnectionHolder.createPeerConnection();
            if (callParticipant.peerConnectionHolder.peerConnection == null) {
                setFailReason(FailReason.PEER_CONNECTION_CREATION_ERROR);
                setState(State.FAILED);
                return;
            }

            callParticipant.peerConnectionHolder.createAnswer();
        });
    }


    private void handleNewParticipantAnswerMessage(CallParticipant callParticipant, String sessionDescriptionType, byte[] gzippedPeerSdpDescription) {
        executor.execute(() -> {
            if (callParticipant.role != Role.RECIPIENT || !shouldISendTheOfferToCallParticipant(callParticipant)) {
                return;
            }

            String peerSdpDescription;
            try {
                peerSdpDescription = gunzip(gzippedPeerSdpDescription);
            } catch (IOException e) {
                callParticipant.setPeerState(PeerState.HANGED_UP);
                e.printStackTrace();
                return;
            }

            callParticipant.peerConnectionHolder.setPeerSessionDescription(sessionDescriptionType, peerSdpDescription);
            callParticipant.peerConnectionHolder.finishEstablishingConnection();
            callParticipant.setPeerState(PeerState.CONNECTING_TO_PEER);
        });
    }

    private void handleKickedMessage() {
        executor.execute(() -> {
            for (CallParticipant callParticipant : callParticipants.values()) {
                callParticipant.setPeerState(PeerState.HANGED_UP);
            }
            setFailReason(FailReason.KICKED);
            setState(State.FAILED);
        });
    }


    void handleNetworkConnectionChange() {
        executor.execute(() -> {
            if (state != State.CALL_IN_PROGRESS) {
                return;
            }
            for (CallParticipant callParticipant : callParticipants.values()) {
                if (callParticipant.peerState == PeerState.CONNECTING_TO_PEER ||
                        callParticipant.peerState == PeerState.CONNECTED ||
                        callParticipant.peerState == PeerState.RECONNECTING) {
                    callParticipant.peerConnectionHolder.createRestartOffer();
                }
            }
        });
    }


    private void handleUpdateCallParticipantsMessage(JsonUpdateParticipantsInnerMessage jsonUpdateParticipantsInnerMessage) {
        executor.execute(() -> {
            Set<BytesKey> participantsToRemove = new HashSet<>(callParticipantIndexes.keySet());
            List<CallParticipant> newCallParticipants = new ArrayList<>();
            for (JsonUpdateParticipantsInnerMessage.JsonContactBytesAndName jsonContactBytesAndName : jsonUpdateParticipantsInnerMessage.callParticipants) {
                if (Arrays.equals(jsonContactBytesAndName.bytesContactIdentity, bytesOwnedIdentity)) {
                    // the received array contains the user himself
                    continue;
                }
                BytesKey bytesKey = new BytesKey(jsonContactBytesAndName.bytesContactIdentity);
                if (participantsToRemove.contains(bytesKey)) {
                    participantsToRemove.remove(bytesKey);
                } else {
                    // call participant not already in the call --> we add him
                    CallParticipant callParticipant = new CallParticipant(bytesOwnedIdentity, jsonContactBytesAndName.bytesContactIdentity, jsonContactBytesAndName.displayName, Role.RECIPIENT);
                    if (callParticipant.contact == null) {
                        // contact not found --> we use the name pushed by the caller
                        callParticipant.displayName = jsonContactBytesAndName.displayName;
                    } else {
                        newCallParticipants.add(callParticipant);
                    }
                    callParticipantIndexes.put(bytesKey, callParticipantIndex);
                    callParticipants.put(callParticipantIndex, callParticipant);
                    callParticipantIndex++;

                    if (shouldISendTheOfferToCallParticipant(callParticipant)) {
                        callParticipant.peerConnectionHolder.setTurnCredentials(turnUserName, turnPassword, turnServers);
                        callParticipant.peerConnectionHolder.createPeerConnection();
                        if (callParticipant.peerConnectionHolder.peerConnection == null) {
                            setState(State.FAILED);
                            return;
                        }
                        callParticipant.peerConnectionHolder.createOffer();
                    } else {
                        // check if we already received the offer the CallParticipant is supposed to send us
                        JsonNewParticipantOfferMessage newParticipantOfferMessage = receivedOfferMessages.remove(new BytesKey(callParticipant.bytesContactIdentity));
                        if (newParticipantOfferMessage != null) {
                            handleNewParticipantOfferMessage(callParticipant, newParticipantOfferMessage.sessionDescriptionType, newParticipantOfferMessage.gzippedSessionDescription);
                        }
                    }
                }
            }

            for (BytesKey bytesKeyToRemove: participantsToRemove) {
                Integer index = callParticipantIndexes.get(bytesKeyToRemove);
                if (index == null) {
                    continue;
                }
                CallParticipant callParticipant = callParticipants.get(index);
                if (callParticipant == null || callParticipant.role == Role.CALLER) {
                    continue;
                }
                callParticipant.peerConnectionHolder.cleanUp();
                callParticipant.setPeerState(PeerState.KICKED);
                callParticipants.remove(index);
                callParticipantIndexes.remove(bytesKeyToRemove);
            }

            updateLogEntry(newCallParticipants);
            notifyCallParticipantsChanged();
        });
    }

    // endregion


    // region Setters and Getters

    private void setState(State state) {
        if (this.state == State.FAILED) {
            // we cannot come back from FAILED state
            return;
        }

        this.state = state;
        this.stateLiveData.postValue(state);

        // handle special state change hooks
        switch (state) {
            case FAILED: {
                // create the log entry --> this will only create one if one was not already created
                createLogEntry(CallLogItem.STATUS_FAILED);
                stopThisService();
                break;
            }
            case GETTING_TURN_CREDENTIALS:
            case INITIALIZING_CALL:
            case BUSY:
            case RINGING: {
                createStateTimeout(state);
                break;
            }
            case INITIAL:
            case CALL_IN_PROGRESS:
            case WAITING_FOR_AUDIO_PERMISSION:
            case CALL_ENDED: {
                break;
            }
        }
    }

    public LiveData<State> getState() {
        return stateLiveData;
    }

    public void setFailReason(FailReason failReason) {
        if (this.failReason == FailReason.NONE) {
            this.failReason = failReason;
        }
    }

    public FailReason getFailReason() {
        return failReason;
    }

    public boolean setContactAndRole(@NonNull byte[] bytesOwnedIdentity, @NonNull List<byte[]> bytesContactIdentities, @NonNull UUID callIdentifier, boolean isCaller) {
        this.bytesOwnedIdentity = bytesOwnedIdentity;
        this.callIdentifier = callIdentifier;
        role = isCaller ? Role.CALLER : Role.RECIPIENT;

        boolean allContactsFound = true;
        for (byte[] bytesContactIdentity: bytesContactIdentities) {
            CallParticipant callParticipant = new CallParticipant(bytesOwnedIdentity, bytesContactIdentity, null, isCaller ? Role.RECIPIENT : Role.CALLER);
            allContactsFound &= callParticipant.contact != null;

            callParticipantIndexes.put(new BytesKey(bytesContactIdentity), callParticipantIndex);
            callParticipants.put(callParticipantIndex, callParticipant);
            callParticipantIndex++;
        }

        notifyCallParticipantsChanged();
        return allContactsFound;
    }

    public LiveData<List<CallParticipantPojo>> getCallParticipantsLiveData() {
        return callParticipantsLiveData;
    }

    public boolean isCaller() {
        return role == Role.CALLER;
    }


    public int getIncomingParticipantCount() {
        return incomingParticipantCount;
    }

    void toggleMuteMicrophone() {
        executor.execute(() -> {
            microphoneMuted = !microphoneMuted;
            JsonMutedInnerMessage jsonMutedInnerMessage = new JsonMutedInnerMessage(microphoneMuted);
            for (CallParticipant callParticipant: callParticipants.values()) {
                callParticipant.peerConnectionHolder.setAudioEnabled(!microphoneMuted);
                sendDataChannelMessage(callParticipant, jsonMutedInnerMessage);
            }
            microphoneMutedLiveData.postValue(microphoneMuted);
        });
    }

    public LiveData<Boolean> getMicrophoneMuted() {
        return microphoneMutedLiveData;
    }

    void bluetoothDisconnected() {
        executor.execute(() -> {
            if (selectedAudioOutput != AudioOutput.BLUETOOTH) {
                return;
            }
            if (availableAudioOutputs != null) {
                selectAudioOutput(availableAudioOutputs.get(0));
            }
        });
    }

    void selectAudioOutput(AudioOutput audioOutput) {
        executor.execute(() -> {
            if (!availableAudioOutputs.contains(audioOutput)) {
                return;
            }
            if (audioOutput == selectedAudioOutput) {
                return;
            }

            if (selectedAudioOutput == AudioOutput.BLUETOOTH && bluetoothHeadsetManager != null) {
                bluetoothHeadsetManager.disconnectAudio();
            }

            switch (audioOutput) {
                case PHONE:
                case HEADSET:
                    audioManager.setSpeakerphoneOn(false);
                    break;
                case LOUDSPEAKER:
                    audioManager.setSpeakerphoneOn(true);
                    break;
                case BLUETOOTH:
                    if (bluetoothHeadsetManager != null) {
                        bluetoothHeadsetManager.connectAudio();
                    } else {
                        return;
                    }
                    break;
            }

            selectedAudioOutput = audioOutput;
            selectedAudioOutputLiveData.postValue(selectedAudioOutput);
        });
    }

    public LiveData<AudioOutput> getSelectedAudioOutput() {
        return selectedAudioOutputLiveData;
    }

    public LiveData<List<AudioOutput>> getAvailableAudioOutputs() {
        return availableAudioOutputsLiveData;
    }

    public LiveData<Integer> getCallDuration() {
        return callDuration;
    }

    // endregion


    // region Helper methods

    boolean shouldISendTheOfferToCallParticipant(@NonNull CallParticipant callParticipant) {
        return new BytesKey(bytesOwnedIdentity).compareTo(new BytesKey(callParticipant.bytesContactIdentity)) > 0;
    }

    @Nullable
    private CallParticipant getCallParticipant(byte[] bytesContactIdentity) {
        Integer index = callParticipantIndexes.get(new BytesKey(bytesContactIdentity));
        if (index == null) {
            return null;
        }
        return callParticipants.get(index);
    }

    @Nullable
    private CallParticipant getCallerCallParticipant() {
        for (CallParticipant callParticipant: callParticipants.values()) {
            if (callParticipant.role == Role.CALLER) {
                return callParticipant;
            }
        }
        return null;
    }

    private void notifyCallParticipantsChanged() {
        List<CallParticipantPojo> pojos = new ArrayList<>(callParticipants.size());
        for (CallParticipant callParticipant: callParticipants.values()) {
            pojos.add(new CallParticipantPojo(callParticipant));
        }
        callParticipantsLiveData.postValue(pojos);
    }

    private void updateStateFromPeerStates() {
        boolean allPeersAreInFinalState = true;
        for (CallParticipant callParticipant: callParticipants.values()) {
            switch (callParticipant.peerState) {
                case INITIAL:
                case START_CALL_MESSAGE_SENT:
                case RINGING:
                case BUSY:
                case CONNECTING_TO_PEER:
                case CONNECTED:
                case RECONNECTING: {
                    allPeersAreInFinalState = false;
                    break;
                }
                case CALL_REJECTED:
                case HANGED_UP:
                case KICKED:
                case FAILED:
                case TIMEOUT: {
                    break;
                }
            }
        }
        if (allPeersAreInFinalState) {
            createLogEntry(CallLogItem.STATUS_MISSED); // this only create the log if it was not yet created

            if (soundPool != null) {
                soundPool.play(disconnectSound, 1, 1, 0, 0, 1);
            }

            setState(State.CALL_ENDED);
            stopThisService();
        }
    }

    void updateAvailableAudioOutputsList() {
        executor.execute(() -> {
            availableAudioOutputs = new ArrayList<>();
            if (wiredHeadsetConnected) {
                availableAudioOutputs.add(AudioOutput.HEADSET);
            } else {
                availableAudioOutputs.add(AudioOutput.PHONE);
            }
            availableAudioOutputs.add(AudioOutput.LOUDSPEAKER);
            if (bluetoothHeadsetManager != null) {
                if (bluetoothHeadsetManager.state != BluetoothHeadsetManager.State.HEADSET_UNAVAILABLE) {
                    availableAudioOutputs.add(AudioOutput.BLUETOOTH);
                    if (bluetoothAutoConnect) {
                        bluetoothAutoConnect = false;
                        selectAudioOutput(AudioOutput.BLUETOOTH);
                    }
                }
            }

            if (!availableAudioOutputs.contains(selectedAudioOutput)) {
                selectAudioOutput(availableAudioOutputs.get(0));
            }
            availableAudioOutputsLiveData.postValue(new ArrayList<>(availableAudioOutputs));
        });
    }

    private void sendDataChannelMessage(CallParticipant callParticipant, JsonDataChannelInnerMessage jsonDataChannelInnerMessage) {
        try {
            JsonDataChannelMessage jsonDataChannelMessage = new JsonDataChannelMessage();
            jsonDataChannelMessage.setMessageType(jsonDataChannelInnerMessage.getMessageType());
            jsonDataChannelMessage.setSerializedMessage(objectMapper.writeValueAsString(jsonDataChannelInnerMessage));
            callParticipant.peerConnectionHolder.sendDataChannelMessage(objectMapper.writeValueAsString(jsonDataChannelMessage));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void createStateTimeout(State state) {
        if (timeoutTimerTask != null) {
            timeoutTimerTask.cancel();
            timeoutTimerTask = null;
        }
        timeoutTimerTask = new TimerTask() {
            @Override
            public void run() {
                handleTimeout();
            }
        };
        try {
            timeoutTimer.schedule(timeoutTimerTask, state == State.RINGING ? RINGING_TIMEOUT_MILLIS : CALL_TIMEOUT_MILLIS);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


    private void createLogEntry(int callLogItemStatus) {
        if (callLogItem != null) {
            // a call log entry was already created, don't create a new one
            return;
        }
        if (callParticipants.size() == 0) {
            return;
        }
        final CallParticipant[] callParticipants = this.callParticipants.values().toArray(new CallParticipant[0]);

        int type = isCaller() ? CallLogItem.TYPE_OUTGOING : CallLogItem.TYPE_INCOMING;
        CallLogItem callLogItem = null;
        switch (callLogItemStatus) {
            case CallLogItem.STATUS_SUCCESSFUL:
            case CallLogItem.STATUS_MISSED:
            case CallLogItem.STATUS_BUSY:
            case CallLogItem.STATUS_FAILED:
                callLogItem = new CallLogItem(bytesOwnedIdentity, bytesGroupOwnerAndUid, type, callLogItemStatus);
                break;
        }
        if (callLogItem != null) {
            this.callLogItem = callLogItem;
            App.runThread(() -> {
                this.callLogItem.id = AppDatabase.getInstance().callLogItemDao().insert(this.callLogItem);
                CallLogItemContactJoin[] callLogItemContactJoins = new CallLogItemContactJoin[callParticipants.length];
                for (int i = 0; i < callParticipants.length; i++) {
                    callLogItemContactJoins[i] = new CallLogItemContactJoin(this.callLogItem.id, bytesOwnedIdentity, callParticipants[i].bytesContactIdentity);
                }
                AppDatabase.getInstance().callLogItemDao().insert(callLogItemContactJoins);

                if (this.callLogItem.callType == CallLogItem.TYPE_INCOMING
                        && (this.callLogItem.callStatus == CallLogItem.STATUS_MISSED
                        || this.callLogItem.callStatus == CallLogItem.STATUS_FAILED
                        || this.callLogItem.callStatus == CallLogItem.STATUS_BUSY)) {
                    for (CallParticipant callParticipant : callParticipants) {
                        if (callParticipant.role == Role.CALLER) {
                            AndroidNotificationManager.displayMissedCallNotification(bytesOwnedIdentity, callParticipant.bytesContactIdentity);
                            break;
                        }
                    }
                }

                if (this.callLogItem.callType == CallLogItem.TYPE_OUTGOING) {
                    if (this.callLogItem.bytesGroupOwnerAndUid != null) {
                        // group discussion
                        Discussion discussion = AppDatabase.getInstance().discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, this.callLogItem.bytesGroupOwnerAndUid);
                        if (discussion != null) {
                            Message callMessage = Message.createPhoneCallMessage(discussion.id, bytesOwnedIdentity, this.callLogItem);
                            AppDatabase.getInstance().messageDao().insert(callMessage);
                            if (discussion.updateLastMessageTimestamp(callMessage.timestamp)) {
                                AppDatabase.getInstance().discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                            }
                        }
                    } else if (callParticipants.length == 1) {
                        // one-to-one discussion
                        Discussion discussion = AppDatabase.getInstance().discussionDao().getByContact(bytesOwnedIdentity, callParticipants[0].bytesContactIdentity);
                        if (discussion != null) {
                            Message callMessage = Message.createPhoneCallMessage(discussion.id, callParticipants[0].bytesContactIdentity, this.callLogItem);
                            AppDatabase.getInstance().messageDao().insert(callMessage);
                            if (discussion.updateLastMessageTimestamp(callMessage.timestamp)) {
                                AppDatabase.getInstance().discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                            }
                        }
                    }
                } else {
                    // find the caller, then insert either in a group discussion, or in his one-to-one discussion
                    for (CallParticipant callParticipant : callParticipants) {
                        if (callParticipant.role == Role.CALLER) {
                            Discussion discussion = null;
                            if (this.callLogItem.bytesGroupOwnerAndUid != null) {
                                discussion = AppDatabase.getInstance().discussionDao().getByGroupOwnerAndUid(bytesOwnedIdentity, this.callLogItem.bytesGroupOwnerAndUid);
                            }
                            if (discussion == null) {
                                discussion = AppDatabase.getInstance().discussionDao().getByContact(bytesOwnedIdentity, callParticipant.bytesContactIdentity);
                            }
                            if (discussion != null) {
                                Message callMessage = Message.createPhoneCallMessage(discussion.id, callParticipant.bytesContactIdentity, this.callLogItem);
                                AppDatabase.getInstance().messageDao().insert(callMessage);
                                if (discussion.updateLastMessageTimestamp(callMessage.timestamp)) {
                                    AppDatabase.getInstance().discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                                }
                            }
                            break;
                        }
                    }
                }
            });
        }
    }

    private void updateLogEntry(@NonNull List<CallParticipant> newCallParticipants) {
        if (callLogItem == null || newCallParticipants.size() == 0) {
            return;
        }
        App.runThread(() -> {
            CallLogItemContactJoin[] callLogItemContactJoins = new CallLogItemContactJoin[newCallParticipants.size()];
            for (int i = 0; i < newCallParticipants.size(); i++) {
                callLogItemContactJoins[i] = new CallLogItemContactJoin(this.callLogItem.id, bytesOwnedIdentity, newCallParticipants.get(i).bytesContactIdentity);
            }
            AppDatabase.getInstance().callLogItemDao().insert(callLogItemContactJoins);
        });
    }

    private void requestAudioManagerFocus() {
        audioFocusRequest = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributesCompat.Builder()
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributesCompat.FLAG_AUDIBILITY_ENFORCED)
                        .setUsage(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION)
                        .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                        .build())
                .setOnAudioFocusChangeListener(focusChange -> Logger.d("☎️ Audio focus changed: " + focusChange))
                .build();
        int result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Logger.d("☎️ Audio focus granted");
        } else {
            Logger.e("☎️ Audio focus denied");
        }
        savedAudioManagerMode = audioManager.getMode();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    private void showOngoingForeground() {
        if (callParticipants.isEmpty()) {
            stopForeground(true);
            return;
        }

        Intent endCallIntent = new Intent(this, WebrtcCallService.class);
        endCallIntent.setAction(ACTION_HANG_UP);
        endCallIntent.putExtra(CALL_IDENTIFIER_INTENT_EXTRA, callIdentifier.toString());
        PendingIntent endCallPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            endCallPendingIntent = PendingIntent.getService(getContext(), 0, endCallIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            endCallPendingIntent = PendingIntent.getService(getContext(), 0, endCallIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        Intent callActivityIntent = new Intent(this, WebrtcCallActivity.class);
        PendingIntent callActivityPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            callActivityPendingIntent = PendingIntent.getActivity(getContext(), 0, callActivityIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            callActivityPendingIntent = PendingIntent.getActivity(getContext(), 0, callActivityIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        InitialView initialView = new InitialView(getContext());
        String notificationName;

        if (callParticipants.size() > 1 && bytesGroupOwnerAndUid != null) {
            Group group = AppDatabase.getInstance().groupDao().get(bytesOwnedIdentity, bytesGroupOwnerAndUid);
            if (group != null && group.getCustomPhotoUrl() != null) {
                initialView.setPhotoUrl(bytesGroupOwnerAndUid, group.getCustomPhotoUrl());
                notificationName = getString(R.string.text_count_contacts_from_group, callParticipants.size(), group.getCustomName());
            } else {
                initialView.setGroup(bytesGroupOwnerAndUid);
                notificationName = getString(R.string.text_count_contacts, callParticipants.size());
            }
        } else {
            CallParticipant callParticipant = callParticipants.values().iterator().next();

            if (callParticipant.contact != null) {
                initialView.setKeycloakCertified(callParticipant.contact.keycloakManaged);
                initialView.setInactive(!callParticipant.contact.active);
                if (callParticipant.contact.getCustomPhotoUrl() != null) {
                    initialView.setPhotoUrl(callParticipant.bytesContactIdentity, callParticipant.contact.getCustomPhotoUrl());
                } else {
                    initialView.setInitial(callParticipant.bytesContactIdentity, App.getInitial(callParticipant.contact.getCustomDisplayName()));
                }
                notificationName = callParticipant.contact.getCustomDisplayName();
            } else {
                initialView.setInitial(callParticipant.bytesContactIdentity, App.getInitial(callParticipant.displayName));
                notificationName = callParticipant.displayName;
            }
        }
        int size = getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
        initialView.setSize(size, size);
        Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        initialView.drawOnCanvas(new Canvas(largeIcon));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person caller = new Person.Builder()
                    .setName(notificationName)
                    .setIcon(Icon.createWithBitmap(largeIcon))
                    .setImportant(false)
                    .build();

            Notification.Builder builder = new Notification.Builder(this, AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID)
                    .setStyle(Notification.CallStyle.forOngoingCall(caller, endCallPendingIntent))
                    .setSmallIcon(R.drawable.ic_phone_animated)
                    .setOngoing(true)
                    .setGroup("silent")
                    .setGroupSummary(false)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setContentIntent(callActivityPendingIntent);

            startForeground(SERVICE_ID, builder.build());
        } else {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID);
            builder.setContentTitle(getString(R.string.notification_title_webrtc_call))
                    .setContentText(notificationName)
                    .setSmallIcon(R.drawable.ic_phone_animated)
                    .setSilent(true)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setLargeIcon(largeIcon)
                    .setContentIntent(callActivityPendingIntent);

            builder.addAction(R.drawable.ic_end_call, getString(R.string.notification_action_end_call), endCallPendingIntent);

            startForeground(SERVICE_ID, builder.build());
        }
    }

    private void showIncomingCallForeground(Contact contact, int participantCount) {
        Intent rejectCallIntent = new Intent(this, WebrtcCallService.class);
        rejectCallIntent.setAction(ACTION_REJECT_CALL);
        rejectCallIntent.putExtra(CALL_IDENTIFIER_INTENT_EXTRA, callIdentifier.toString());
        PendingIntent rejectCallPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rejectCallPendingIntent = PendingIntent.getService(getContext(), 0, rejectCallIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            rejectCallPendingIntent = PendingIntent.getService(getContext(), 0, rejectCallIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        Intent answerCallIntent = new Intent(this, WebrtcCallActivity.class);
        answerCallIntent.setAction(WebrtcCallActivity.ANSWER_CALL_ACTION);
        answerCallIntent.putExtra(WebrtcCallActivity.ANSWER_CALL_EXTRA_CALL_IDENTIFIER, callIdentifier.toString());
        answerCallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent answerCallPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            answerCallPendingIntent = PendingIntent.getActivity(getContext(), 0, answerCallIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            answerCallPendingIntent = PendingIntent.getActivity(getContext(), 0, answerCallIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        Intent fullScreenIntent = new Intent(this, WebrtcIncomingCallActivity.class);
        PendingIntent fullScreenPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fullScreenPendingIntent = PendingIntent.getActivity(getContext(), 0, fullScreenIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            fullScreenPendingIntent = PendingIntent.getActivity(getContext(), 0, fullScreenIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        }

        InitialView initialView = new InitialView(getContext());
        initialView.setKeycloakCertified(contact.keycloakManaged);
        initialView.setInactive(!contact.active);
        if (contact.getCustomPhotoUrl() != null) {
            initialView.setPhotoUrl(contact.bytesContactIdentity, contact.getCustomPhotoUrl());
        } else {
            initialView.setInitial(contact.bytesContactIdentity, App.getInitial(contact.getCustomDisplayName()));
        }
        int size = getContext().getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
        initialView.setSize(size, size);
        Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        initialView.drawOnCanvas(new Canvas(largeIcon));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person caller = new Person.Builder()
                    .setName(contact.getCustomDisplayName())
                    .setIcon(Icon.createWithBitmap(largeIcon))
                    .setImportant(true)
                    .build();

            Notification.CallStyle callStyle = Notification.CallStyle
                    .forIncomingCall(caller, rejectCallPendingIntent, answerCallPendingIntent)
                    .setIsVideo(false);

            if (participantCount > 1) {
                callStyle.setVerificationText(getResources().getQuantityString(R.plurals.notification_text_incoming_call_participant_count, participantCount - 1, participantCount - 1));
            }

            Notification.Builder publicBuilder = new Notification.Builder(this, AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_phone_animated)
                    .setContentTitle(getContext().getString(R.string.notification_public_title_incoming_webrtc_call));

            Notification.Builder builder = new Notification.Builder(this, AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID)
                    .setPublicVersion(publicBuilder.build())
                    .setSmallIcon(R.drawable.ic_phone_animated)
                    .setStyle(callStyle)
                    .addPerson(caller)
                    .setContentIntent(fullScreenPendingIntent)
                    .setDeleteIntent(rejectCallPendingIntent)
                    .setFullScreenIntent(fullScreenPendingIntent, true);

            try {
                startForeground(SERVICE_ID, builder.build());
            } catch (Exception e) {
                e.printStackTrace();

                // failed to start foreground service --> only show a notification and hope for the best!
                // we make it dismissible
                builder.setOngoing(false);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
                notificationManager.notify(NOT_FOREGROUND_NOTIFICATION_ID, builder.build());
            }
        } else {

            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(this, AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_phone_animated)
                    .setContentTitle(getContext().getString(R.string.notification_public_title_incoming_webrtc_call));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AndroidNotificationManager.WEBRTC_CALL_SERVICE_NOTIFICATION_CHANNEL_ID);
            builder.setContentTitle(getString(R.string.notification_title_incoming_webrtc_call, contact.getCustomDisplayName()))
                    .setPublicVersion(publicBuilder.build())
                    .setSmallIcon(R.drawable.ic_phone_animated)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setContentIntent(fullScreenPendingIntent)
                    .setDeleteIntent(rejectCallPendingIntent)
                    .setFullScreenIntent(fullScreenPendingIntent, true);

            if (participantCount > 1) {
                builder.setContentText(getResources().getQuantityString(R.plurals.notification_text_incoming_call_participant_count, participantCount - 1, participantCount - 1));
            }

            builder.setLargeIcon(largeIcon);

            SpannableString redReject = new SpannableString(getString(R.string.notification_action_reject));
            redReject.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.red)), 0, redReject.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.addAction(R.drawable.ic_end_call, redReject, rejectCallPendingIntent);

            SpannableString greenAccept = new SpannableString(getString(R.string.notification_action_accept));
            greenAccept.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.green)), 0, greenAccept.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.addAction(R.drawable.ic_answer_call, greenAccept, answerCallPendingIntent);

            try {
                startForeground(SERVICE_ID, builder.build());
            } catch (Exception e) {
                e.printStackTrace();

                // failed to start foreground service --> only show a notification and hope for the best!
                // we make it dismissible
                builder.setOngoing(false);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
                notificationManager.notify(NOT_FOREGROUND_NOTIFICATION_ID, builder.build());
            }
        }
    }

    private byte[] gzip(String sdp) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream deflater = new DeflaterOutputStream(baos, new Deflater(5, true));
        deflater.write(sdp.getBytes(StandardCharsets.UTF_8));
        deflater.close();
        byte[] gzipped = baos.toByteArray();
        baos.close();
        return gzipped;
    }

    private String gunzip(byte[] gzipped) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(gzipped);
             InflaterInputStream inflater = new InflaterInputStream(bais, new Inflater(true));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int c;
            while ((c = inflater.read(buffer)) != -1) {
                baos.write(buffer, 0, c);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // endregion


    // region Service lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        webrtcMessageReceivedBroadcastReceiver = new WebrtcMessageReceivedBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(webrtcMessageReceivedBroadcastReceiver, new IntentFilter(ACTION_MESSAGE));
        engineTurnCredentialsReceiver = new EngineTurnCredentialsReceiver();
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.TURN_CREDENTIALS_RECEIVED, engineTurnCredentialsReceiver);
        AppSingleton.getEngine().addNotificationListener(EngineNotifications.TURN_CREDENTIALS_FAILED, engineTurnCredentialsReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (callLogItem != null && callLogItem.callStatus == CallLogItem.STATUS_SUCCESSFUL && callDuration.getValue() != null) {
            callLogItem.duration = callDuration.getValue();
            App.runThread(() -> AppDatabase.getInstance().callLogItemDao().update(callLogItem));
        }

        if (outgoingCallRinger != null) {
            outgoingCallRinger.stop();
        }
        if (incomingCallRinger != null) {
            incomingCallRinger.stop();
        }
        if (soundPool != null) {
            soundPool.release();
        }

        unregisterScreenOffReceiver();
        unregisterWiredHeadsetReceiver();

        timeoutTimer.cancel();
        releaseWakeLocks(WakeLock.ALL);

        NetworkMonitor.getInstance().stopMonitoring();

        for (CallParticipant callParticipant: callParticipants.values()) {
            callParticipant.peerConnectionHolder.cleanUp();
        }
        callParticipantIndexes.clear();
        callParticipants.clear();

        WebrtcPeerConnectionHolder.globalCleanup();

        if (webrtcMessageReceivedBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(webrtcMessageReceivedBroadcastReceiver);
            webrtcMessageReceivedBroadcastReceiver = null;
        }
        if (audioManager != null && audioFocusRequest != null) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest);
            audioManager.setMode(savedAudioManagerMode);
        }
        if (engineTurnCredentialsReceiver != null) {
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.TURN_CREDENTIALS_RECEIVED, engineTurnCredentialsReceiver);
            AppSingleton.getEngine().removeNotificationListener(EngineNotifications.TURN_CREDENTIALS_FAILED, engineTurnCredentialsReceiver);
        }
        if (phoneCallStateListener != null) {
            phoneCallStateListener.unregister();
        }
        if (callDurationTimer != null) {
            callDurationTimer.cancel();
        }
        if (bluetoothHeadsetManager != null) {
            bluetoothHeadsetManager.disconnectAudio();
            bluetoothHeadsetManager.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return webrtcCallServiceBinder;
    }

    // endregion



    // region Listeners

    private class NetworkMonitorObserver implements NetworkMonitor.NetworkObserver {
        @Override
        public void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
            handleNetworkConnectionChange();
        }
    }

    public class WebrtcCallServiceBinder extends Binder {
        WebrtcCallService getService() {
            return WebrtcCallService.this;
        }
    }

    private class WebrtcMessageReceivedBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null || !ACTION_MESSAGE.equals(intent.getAction())) {
                return;
            }
            handleMessageIntent(intent);
        }
    }

    private class ScreenOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            incomingCallRinger.stop();
        }
    }

    private class WiredHeadsetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null || !AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                return;
            }
            wiredHeadsetConnected = (1 == intent.getIntExtra("state", 0));
            updateAvailableAudioOutputsList();
        }
    }

    private void registerScreenOffReceiver() {
        if (screenOffReceiver == null) {
            screenOffReceiver = new ScreenOffReceiver();
            registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }
    }

    private void unregisterScreenOffReceiver() {
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver);
            screenOffReceiver = null;
        }
    }

    private void registerWiredHeadsetReceiver() {
        registerReceiver(wiredHeadsetReceiver, new IntentFilter(AudioManager.ACTION_HEADSET_PLUG));
    }

    private void unregisterWiredHeadsetReceiver() {
        unregisterReceiver(wiredHeadsetReceiver);
    }

    private class EngineTurnCredentialsReceiver implements EngineNotificationListener {
        private Long registrationNumber = null;

        @Override
        public void callback(String notificationName, HashMap<String, Object> userInfo) {
            switch (notificationName) {
                case EngineNotifications.TURN_CREDENTIALS_RECEIVED: {
                    byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_OWNED_IDENTITY_KEY);
                    UUID callUuid = (UUID) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_CALL_UUID_KEY);
                    // ignore notifications from another call...
                    if (Arrays.equals(bytesOwnedIdentity, WebrtcCallService.this.bytesOwnedIdentity) && WebrtcCallService.this.callIdentifier.equals(callUuid)) {
                        String callerUsername = (String) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_1_KEY);
                        String callerPassword = (String) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_1_KEY);
                        String recipientUsername = (String) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_USERNAME_2_KEY);
                        String recipientPassword = (String) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_PASSWORD_2_KEY);
                        //noinspection unchecked
                        List<String> turnServers = (List<String>) userInfo.get(EngineNotifications.TURN_CREDENTIALS_RECEIVED_SERVERS_KEY);
                        if (callerUsername == null || callerPassword == null || recipientUsername == null || recipientPassword == null || turnServers == null) {
                            callerFailedTurnCredentials(ObvTurnCredentialsFailedReason.UNABLE_TO_CONTACT_SERVER);
                        } else {
                            callerSetTurnCredentialsAndInitializeCall(callerUsername, callerPassword, recipientUsername, recipientPassword, turnServers);

                            Logger.d("☎️ Caching received turn credentials for reuse");
                            SharedPreferences callCredentialsCacheSharedPreference = App.getContext().getSharedPreferences(App.getContext().getString(R.string.preference_filename_call_credentials_cache), Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = callCredentialsCacheSharedPreference.edit();
                            editor.clear();
                            editor.putLong(PREF_KEY_TIMESTAMP, System.currentTimeMillis());
                            editor.putString(PREF_KEY_USERNAME1, callerUsername);
                            editor.putString(PREF_KEY_PASSWORD1, callerPassword);
                            editor.putString(PREF_KEY_USERNAME2, recipientUsername);
                            editor.putString(PREF_KEY_PASSWORD2, recipientPassword);
                            editor.putStringSet(PREF_KEY_TURN_SERVERS, new HashSet<>(turnServers));
                            editor.apply();
                        }
                    }
                    break;
                }
                case EngineNotifications.TURN_CREDENTIALS_FAILED: {
                    byte[] bytesOwnedIdentity = (byte[]) userInfo.get(EngineNotifications.TURN_CREDENTIALS_FAILED_OWNED_IDENTITY_KEY);
                    UUID callUuid = (UUID) userInfo.get(EngineNotifications.TURN_CREDENTIALS_FAILED_CALL_UUID_KEY);
                    // ignore notifications from another call...
                    if (Arrays.equals(bytesOwnedIdentity, WebrtcCallService.this.bytesOwnedIdentity) && WebrtcCallService.this.callIdentifier.equals(callUuid)) {
                        ObvTurnCredentialsFailedReason rfc = (ObvTurnCredentialsFailedReason) userInfo.get(EngineNotifications.TURN_CREDENTIALS_FAILED_REASON_KEY);
                        callerFailedTurnCredentials(rfc);
                    }
                    break;
                }
            }
        }

        @Override
        public void setEngineNotificationListenerRegistrationNumber(long registrationNumber) {
            this.registrationNumber = registrationNumber;
        }

        @Override
        public long getEngineNotificationListenerRegistrationNumber() {
            return registrationNumber == null ? 0 : registrationNumber;
        }

        @Override
        public boolean hasEngineNotificationListenerRegistrationNumber() {
            return registrationNumber != null;
        }
    }

    @SuppressLint("WakelockTimeout")
    void acquireWakeLock(WakeLock wakeLock) {
        boolean lockProximity = false;
        boolean lockWifi = false;
        switch (wakeLock) {
            case ALL:
                lockProximity = true;
                lockWifi = true;
                break;
            case WIFI:
                lockWifi = true;
                break;
            case PROXIMITY:
                lockProximity = true;
                break;
        }

        if (lockProximity) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    proximityLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "io.olvid:proximity_lock");
                    proximityLock.acquire();
                } else {
                    proximityLock = null;
                }
            }
        }

        if (lockWifi) {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "io.olvid:wifi_lock");
                wifiLock.acquire();
            } else {
                wifiLock = null;
            }
        }
    }

    void releaseWakeLocks(WakeLock wakeLock) {
        boolean unlockProximity = false;
        boolean unlockWifi = false;
        switch (wakeLock) {
            case ALL:
                unlockProximity = true;
                unlockWifi = true;
                break;
            case WIFI:
                unlockWifi = true;
                break;
            case PROXIMITY:
                unlockProximity = true;
                break;
        }

        if (unlockProximity && proximityLock != null) {
            proximityLock.release();
            proximityLock = null;
        }
        if (unlockWifi && wifiLock != null) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    // endregion


    // region Send messages via Oblivious channel

    public boolean sendStartCallMessage(CallParticipant callParticipant, String sessionDescriptionType, String sessionDescription, String turnUserName, String turnPassword, List<String> turnServers) throws IOException {
        final JsonStartCallMessage startCallMessage;
        if (bytesGroupOwnerAndUid != null && AppDatabase.getInstance().contactGroupJoinDao().isGroupMember(bytesOwnedIdentity, callParticipant.bytesContactIdentity, bytesGroupOwnerAndUid)) {
            startCallMessage = new JsonStartCallMessage(sessionDescriptionType, gzip(sessionDescription), turnUserName, turnPassword, turnServers, callParticipants.size(), bytesGroupOwnerAndUid);
        } else {
            startCallMessage = new JsonStartCallMessage(sessionDescriptionType, gzip(sessionDescription), turnUserName, turnPassword, turnServers, callParticipants.size(), null);
        }
        return postMessage(Collections.singletonList(callParticipant), startCallMessage);
    }


    public void sendAnswerCallMessage(CallParticipant callParticipant, String sessionDescriptionType, String sessionDescription) throws IOException {
        JsonAnswerCallMessage answerCallMessage = new JsonAnswerCallMessage(sessionDescriptionType, gzip(sessionDescription));
        postMessage(Collections.singletonList(callParticipant), answerCallMessage);
    }

    public void sendRingingMessage(CallParticipant callParticipant) {
        postMessage(Collections.singletonList(callParticipant), new JsonRingingMessage());
    }

    public void sendRejectCallMessage(CallParticipant callParticipant) {
        postMessage(Collections.singletonList(callParticipant), new JsonRejectCallMessage());
    }

    public void sendBusyMessage(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, UUID callIdentifier, @Nullable byte[] bytesGroupOwnerAndUid) {
        App.runThread(() -> {
            CallLogItem callLogItem = new CallLogItem(bytesOwnedIdentity, bytesGroupOwnerAndUid, CallLogItem.TYPE_INCOMING, CallLogItem.STATUS_BUSY);
            callLogItem.id = AppDatabase.getInstance().callLogItemDao().insert(callLogItem);
            CallLogItemContactJoin callLogItemContactJoin = new CallLogItemContactJoin(callLogItem.id, bytesOwnedIdentity, bytesContactIdentity);
            AppDatabase.getInstance().callLogItemDao().insert(callLogItemContactJoin);

            AndroidNotificationManager.displayMissedCallNotification(bytesOwnedIdentity, bytesContactIdentity);
            postMessage(new JsonBusyMessage(), bytesOwnedIdentity, Collections.singletonList(bytesContactIdentity), callIdentifier);

            Discussion discussion = AppDatabase.getInstance().discussionDao().getByContact(bytesOwnedIdentity, bytesContactIdentity);
            if (discussion != null) {
                Message busyCallMessage = Message.createPhoneCallMessage(discussion.id, bytesContactIdentity, callLogItem);
                AppDatabase.getInstance().messageDao().insert(busyCallMessage);
                if (discussion.updateLastMessageTimestamp(busyCallMessage.timestamp)) {
                    AppDatabase.getInstance().discussionDao().updateLastMessageTimestamp(discussion.id, discussion.lastMessageTimestamp);
                }
            }
        });
    }

    public void sendReconnectCallMessage(CallParticipant callParticipant, String sessionDescriptionType, String sessionDescription, int reconnectCounter, int peerReconnectCounterToOverride) throws IOException {
        JsonReconnectCallMessage reconnectCallMessage = new JsonReconnectCallMessage(sessionDescriptionType, gzip(sessionDescription), reconnectCounter, peerReconnectCounterToOverride);
        if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
            postMessage(Collections.singletonList(callParticipant), reconnectCallMessage);
        } else {
            CallParticipant caller = getCallerCallParticipant();
            if (caller != null) {
                sendDataChannelMessage(caller, new JsonRelayInnerMessage(callParticipant.bytesContactIdentity, reconnectCallMessage.getMessageType(), objectMapper.writeValueAsString(reconnectCallMessage)));
            }
        }
    }

    public void sendHangedUpMessage(Collection<CallParticipant> callParticipants) {
        List<CallParticipant> callParticipantsWithContact = new ArrayList<>(callParticipants.size());
        JsonHangedUpMessage jsonHangedUpMessage = new JsonHangedUpMessage();
        for (CallParticipant callParticipant: callParticipants) {
            sendDataChannelMessage(callParticipant, new JsonHangedUpInnerMessage());
            if (callParticipant.contact == null || callParticipant.contact.establishedChannelCount == 0) {
                try {
                    CallParticipant caller = getCallerCallParticipant();
                    if (caller != null) {
                        sendDataChannelMessage(caller, new JsonRelayInnerMessage(callParticipant.bytesContactIdentity, jsonHangedUpMessage.getMessageType(), objectMapper.writeValueAsString(jsonHangedUpMessage)));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                callParticipantsWithContact.add(callParticipant);
            }
        }
        postMessage(callParticipantsWithContact, jsonHangedUpMessage);
    }

    public void sendKickMessage(CallParticipant callParticipant) {
        if (!isCaller()) {
            return;
        }
        postMessage(Collections.singletonList(callParticipant), new JsonKickMessage());
    }

    public void sendNewParticipantOfferMessage(CallParticipant callParticipant, String sessionDescriptionType, String sessionDescription) throws IOException {
        JsonNewParticipantOfferMessage newParticipantOfferMessage = new JsonNewParticipantOfferMessage(sessionDescriptionType, gzip(sessionDescription));
        if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
            postMessage(Collections.singletonList(callParticipant), newParticipantOfferMessage);
        } else {
            CallParticipant caller = getCallerCallParticipant();
            if (caller != null) {
                sendDataChannelMessage(caller, new JsonRelayInnerMessage(callParticipant.bytesContactIdentity, newParticipantOfferMessage.getMessageType(), objectMapper.writeValueAsString(newParticipantOfferMessage)));
            }
        }
    }

    public void sendNewParticipantAnswerMessage(CallParticipant callParticipant, String sessionDescriptionType, String sessionDescription) throws IOException {
        JsonNewParticipantAnswerMessage newParticipantAnswerMessage = new JsonNewParticipantAnswerMessage(sessionDescriptionType, gzip(sessionDescription));
        if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
            postMessage(Collections.singletonList(callParticipant), newParticipantAnswerMessage);
        } else {
            CallParticipant caller = getCallerCallParticipant();
            if (caller != null) {
                sendDataChannelMessage(caller, new JsonRelayInnerMessage(callParticipant.bytesContactIdentity, newParticipantAnswerMessage.getMessageType(), objectMapper.writeValueAsString(newParticipantAnswerMessage)));
            }
        }
    }


    private boolean postMessage(Collection<CallParticipant> callParticipants, JsonWebrtcProtocolMessage protocolMessage) {
        List<byte[]> bytesContactIdentities = new ArrayList<>(callParticipants.size());
        for (CallParticipant callParticipant: callParticipants) {
            if (callParticipant.contact != null && callParticipant.contact.establishedChannelCount > 0) {
                bytesContactIdentities.add(callParticipant.bytesContactIdentity);
            }
        }
        if (bytesContactIdentities.size() > 0) {
            return postMessage(protocolMessage, bytesOwnedIdentity, bytesContactIdentities, callIdentifier);
        }
        return false;
    }

    private boolean postMessage(JsonWebrtcProtocolMessage protocolMessage, byte[] bytesOwnedIdentity, List<byte[]> bytesContactIdentities, UUID callIdentifier) {
        try {
            Message.JsonWebrtcMessage jsonWebrtcMessage = new Message.JsonWebrtcMessage();
            jsonWebrtcMessage.setMessageType(protocolMessage.getMessageType());
            jsonWebrtcMessage.setCallIdentifier(callIdentifier);
            jsonWebrtcMessage.setSerializedMessagePayload(objectMapper.writeValueAsString(protocolMessage));

            Message.JsonPayload jsonPayload = new Message.JsonPayload();
            jsonPayload.setJsonWebrtcMessage(jsonWebrtcMessage);

            // only mark START_CALL_MESSAGE_TYPE messages as voip
            boolean tagAsVoipMessage = protocolMessage.getMessageType() == START_CALL_MESSAGE_TYPE;

            byte[] messagePayload = AppSingleton.getJsonObjectMapper().writeValueAsBytes(jsonPayload);
            ObvPostMessageOutput obvPostMessageOutput = AppSingleton.getEngine().post(messagePayload, null, new ObvOutboundAttachment[0], bytesContactIdentities, bytesOwnedIdentity, tagAsVoipMessage, tagAsVoipMessage);
            return obvPostMessageOutput.isMessageSent();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            setFailReason(FailReason.INTERNAL_ERROR);
            setState(State.FAILED);
            return false;
        }
    }

    // endregion



    // region JsonDataChannelMessages

    private class DataChannelListener implements WebrtcPeerConnectionHolder.DataChannelMessageListener {
        private final CallParticipant callParticipant;

        public DataChannelListener(CallParticipant callParticipant) {
            this.callParticipant = callParticipant;
        }

        @Override
        public void onConnect() {
            executor.execute(() -> {
                sendDataChannelMessage(callParticipant, new JsonMutedInnerMessage(microphoneMuted));
                if (isCaller()) {
                    sendDataChannelMessage(callParticipant, new JsonUpdateParticipantsInnerMessage(callParticipants.values()));
                }
            });
        }

        @Override
        public void onMessage(ByteBuffer byteBuffer) {
            byte[] bytes = new byte[byteBuffer.limit()];
            byteBuffer.get(bytes);
            try {
                JsonDataChannelMessage jsonDataChannelMessage = objectMapper.readValue(bytes, JsonDataChannelMessage.class);
                switch (jsonDataChannelMessage.messageType) {
                    case MUTED_DATA_MESSAGE_TYPE: {
                        JsonMutedInnerMessage jsonMutedInnerMessage = objectMapper.readValue(jsonDataChannelMessage.serializedMessage, JsonMutedInnerMessage.class);
                        executor.execute(() -> callParticipant.setPeerIsMuted(jsonMutedInnerMessage.muted));
                        break;
                    }
                    case UPDATE_PARTICIPANTS_DATA_MESSAGE_TYPE: {
                        if (callParticipant.role != Role.CALLER) {
                            break;
                        }
                        JsonUpdateParticipantsInnerMessage jsonUpdateParticipantsInnerMessage = objectMapper.readValue(jsonDataChannelMessage.serializedMessage, JsonUpdateParticipantsInnerMessage.class);
                        handleUpdateCallParticipantsMessage(jsonUpdateParticipantsInnerMessage);
                        break;
                    }
                    case RELAY_DATA_MESSAGE_TYPE: {
                        if (!isCaller()) {
                            break;
                        }
                        JsonRelayInnerMessage jsonRelayInnerMessage = objectMapper.readValue(jsonDataChannelMessage.serializedMessage, JsonRelayInnerMessage.class);
                        byte[] bytesContactIdentity = jsonRelayInnerMessage.to;
                        int messageType = jsonRelayInnerMessage.relayedMessageType;
                        String serializedMessagePayload = jsonRelayInnerMessage.serializedMessagePayload;

                        executor.execute(() -> {
                            CallParticipant callParticipant = getCallParticipant(bytesContactIdentity);
                            if (callParticipant != null) {
                                sendDataChannelMessage(callParticipant, new JsonRelayedInnerMessage(this.callParticipant.bytesContactIdentity, messageType, serializedMessagePayload));
                            }
                        });
                        break;
                    }
                    case RELAYED_DATA_MESSAGE_TYPE: {
                        if (isCaller() || callParticipant.role != Role.CALLER) {
                            break;
                        }
                        JsonRelayedInnerMessage jsonRelayedInnerMessage = objectMapper.readValue(jsonDataChannelMessage.serializedMessage, JsonRelayedInnerMessage.class);
                        byte[] bytesContactIdentity = jsonRelayedInnerMessage.from;
                        int messageType = jsonRelayedInnerMessage.relayedMessageType;
                        String serializedMessagePayload = jsonRelayedInnerMessage.serializedMessagePayload;

                        executor.execute(() -> handleMessage(bytesContactIdentity, messageType, serializedMessagePayload));
                        break;
                    }
                    case HANGED_UP_DATA_MESSAGE_TYPE: {
                        handleHangedUpMessage(callParticipant);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonDataChannelMessage {
        int messageType;
        String serializedMessage;

        public JsonDataChannelMessage() {
        }

        @JsonProperty("t")
        public int getMessageType() {
            return messageType;
        }

        @JsonProperty("t")
        public void setMessageType(int messageType) {
            this.messageType = messageType;
        }

        @JsonProperty("m")
        public String getSerializedMessage() {
            return serializedMessage;
        }

        @JsonProperty("m")
        public void setSerializedMessage(String serializedMessage) {
            this.serializedMessage = serializedMessage;
        }
    }


    private static abstract class JsonDataChannelInnerMessage {
        abstract int getMessageType();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonMutedInnerMessage extends JsonDataChannelInnerMessage {
        boolean muted;

        @SuppressWarnings("unused")
        public JsonMutedInnerMessage() {
        }

        public JsonMutedInnerMessage(boolean muted) {
            this.muted = muted;
        }

        @JsonProperty("muted")
        public boolean isMuted() {
            return muted;
        }

        @JsonProperty("muted")
        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return MUTED_DATA_MESSAGE_TYPE;
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonUpdateParticipantsInnerMessage extends JsonDataChannelInnerMessage {
        List<JsonContactBytesAndName> callParticipants;

        public JsonUpdateParticipantsInnerMessage() {
        }

        public JsonUpdateParticipantsInnerMessage(@NonNull Collection<CallParticipant> callParticipants) {
            this.callParticipants = new ArrayList<>(callParticipants.size()+1);
            for (CallParticipant callParticipant: callParticipants) {
                if (callParticipant.peerState == PeerState.CONNECTED ||
                        callParticipant.peerState == PeerState.RECONNECTING) {
                    // only add participants that are indeed part of the call
                    //noinspection ConstantConditions --> we know callParticipant.contact is non null as this message can only be sent by the caller
                    this.callParticipants.add(new JsonContactBytesAndName(callParticipant.bytesContactIdentity, callParticipant.contact.displayName));
                }
            }
        }

        @JsonProperty("cp")
        public List<JsonContactBytesAndName> getCallParticipants() {
            return callParticipants;
        }

        @JsonProperty("cp")
        public void setCallParticipants(List<JsonContactBytesAndName> callParticipants) {
            this.callParticipants = callParticipants;
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return UPDATE_PARTICIPANTS_DATA_MESSAGE_TYPE;
        }

        @SuppressWarnings("unused")
        @JsonIgnoreProperties(ignoreUnknown = true)
        private static final class JsonContactBytesAndName {
            byte[] bytesContactIdentity;
            String displayName;

            public JsonContactBytesAndName() {
            }

            public JsonContactBytesAndName(byte[] bytesContactIdentity, String displayName) {
                this.bytesContactIdentity = bytesContactIdentity;
                this.displayName = displayName;
            }

            @JsonProperty("id")
            public byte[] getBytesContactIdentity() {
                return bytesContactIdentity;
            }

            @JsonProperty("id")
            public void setBytesContactIdentity(byte[] bytesContactIdentity) {
                this.bytesContactIdentity = bytesContactIdentity;
            }

            @JsonProperty("name")
            public String getDisplayName() {
                return displayName;
            }

            @JsonProperty("name")
            public void setDisplayName(String displayName) {
                this.displayName = displayName;
            }
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonRelayInnerMessage extends JsonDataChannelInnerMessage {
        byte[] to;
        int relayedMessageType;
        String serializedMessagePayload;

        public JsonRelayInnerMessage() {
        }

        public JsonRelayInnerMessage(byte[] to, int relayedMessageType, String serializedMessagePayload) {
            this.to = to;
            this.relayedMessageType = relayedMessageType;
            this.serializedMessagePayload = serializedMessagePayload;
        }

        public byte[] getTo() {
            return to;
        }

        public void setTo(byte[] to) {
            this.to = to;
        }

        @JsonProperty("mt")
        public void setMessageType(int messageType) {
            this.relayedMessageType = messageType;
        }

        @JsonProperty("mt")
        public int getRelayedMessageType() {
            return relayedMessageType;
        }

        @JsonProperty("smp")
        public String getSerializedMessagePayload() {
            return serializedMessagePayload;
        }

        @JsonProperty("smp")
        public void setSerializedMessagePayload(String serializedMessagePayload) {
            this.serializedMessagePayload = serializedMessagePayload;
        }

        @Override
        int getMessageType() {
            return RELAY_DATA_MESSAGE_TYPE;
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonRelayedInnerMessage extends JsonDataChannelInnerMessage {
        byte[] from;
        int relayedMessageType;
        String serializedMessagePayload;

        public JsonRelayedInnerMessage() {
        }

        public JsonRelayedInnerMessage(byte[] from, int relayedMessageType, String serializedMessagePayload) {
            this.from = from;
            this.relayedMessageType = relayedMessageType;
            this.serializedMessagePayload = serializedMessagePayload;
        }

        public byte[] getFrom() {
            return from;
        }

        public void setFrom(byte[] from) {
            this.from = from;
        }

        @JsonProperty("mt")
        public void setMessageType(int messageType) {
            this.relayedMessageType = messageType;
        }

        @JsonProperty("mt")
        public int getRelayedMessageType() {
            return relayedMessageType;
        }

        @JsonProperty("smp")
        public String getSerializedMessagePayload() {
            return serializedMessagePayload;
        }

        @JsonProperty("smp")
        public void setSerializedMessagePayload(String serializedMessagePayload) {
            this.serializedMessagePayload = serializedMessagePayload;
        }

        @Override
        int getMessageType() {
            return RELAYED_DATA_MESSAGE_TYPE;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonHangedUpInnerMessage extends JsonDataChannelInnerMessage {
        public JsonHangedUpInnerMessage() {
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return HANGED_UP_DATA_MESSAGE_TYPE;
        }
    }
    // endregion



    // region JsonWebrtcProtocolMessages

    private static abstract class JsonWebrtcProtocolMessage {
        abstract int getMessageType();
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonStartCallMessage extends JsonWebrtcProtocolMessage {
        String sessionDescriptionType;
        byte[] gzippedSessionDescription;
        String turnUserName;
        String turnPassword;
        List<String> turnServers;
        int participantCount;
        byte[] bytesGroupOwner;
        byte[] groupId;

        @SuppressWarnings("unused")
        public JsonStartCallMessage() {
        }

        public JsonStartCallMessage(String sessionDescriptionType, byte[] gzippedSessionDescription, String turnUserName, String turnPassword, List<String> turnServers, int participantCount, byte[] bytesGroupOwnerAndUid) {
            this.sessionDescriptionType = sessionDescriptionType;
            this.gzippedSessionDescription = gzippedSessionDescription;
            this.turnUserName = turnUserName;
            this.turnPassword = turnPassword;
            this.turnServers = turnServers;
            this.participantCount = participantCount;
            this.setBytesGroupOwnerAndUid(bytesGroupOwnerAndUid);
        }

        @JsonProperty("sdt")
        public String getSessionDescriptionType() {
            return sessionDescriptionType;
        }

        @JsonProperty("sdt")
        public void setSessionDescriptionType(String sessionDescriptionType) {
            this.sessionDescriptionType = sessionDescriptionType;
        }

        @JsonProperty("sd")
        public byte[] getGzippedSessionDescription() {
            return gzippedSessionDescription;
        }

        @JsonProperty("sd")
        public void setGzippedSessionDescription(byte[] gzippedSessionDescription) {
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @JsonProperty("tu")
        public String getTurnUserName() {
            return turnUserName;
        }

        @JsonProperty("tu")
        public void setTurnUserName(String turnUserName) {
            this.turnUserName = turnUserName;
        }

        @JsonProperty("tp")
        public String getTurnPassword() {
            return turnPassword;
        }

        @JsonProperty("tp")
        public void setTurnPassword(String turnPassword) {
            this.turnPassword = turnPassword;
        }

        @JsonProperty("ts")
        public List<String> getTurnServers() {
            return turnServers;
        }

        @JsonProperty("ts")
        public void setTurnServers(List<String> turnServers) {
            this.turnServers = turnServers;
        }

        @JsonProperty("c")
        public int getParticipantCount() {
            return participantCount;
        }

        @JsonProperty("c")
        public void setParticipantCount(int participantCount) {
            this.participantCount = participantCount;
        }

        @JsonProperty("go")
        public byte[] getBytesGroupOwner() {
            return bytesGroupOwner;
        }

        @JsonProperty("go")
        public void setBytesGroupOwner(byte[] bytesGroupOwner) {
            this.bytesGroupOwner = bytesGroupOwner;
        }

        @JsonProperty("gi")
        public byte[] getGroupId() {
            return groupId;
        }

        @JsonProperty("gi")
        public void setGroupId(byte[] groupId) {
            this.groupId = groupId;
        }

        @JsonIgnore
        public byte[] getBytesGroupOwnerAndUid() {
            if (this.bytesGroupOwner == null || this.groupId == null) {
                return null;
            }
            byte[] bytesGroupOwnerAndUid = new byte[bytesGroupOwner.length + groupId.length];
            System.arraycopy(this.bytesGroupOwner, 0, bytesGroupOwnerAndUid, 0, this.bytesGroupOwner.length);
            System.arraycopy(this.groupId, 0, bytesGroupOwnerAndUid, this.bytesGroupOwner.length, this.groupId.length);
            return bytesGroupOwnerAndUid;
        }

        @JsonIgnore
        public void setBytesGroupOwnerAndUid(byte[] bytesGroupOwnerAndUid) {
            if (bytesGroupOwnerAndUid == null || bytesGroupOwnerAndUid.length < 32) {
                this.bytesGroupOwner = null;
                this.groupId = null;
            } else {
                this.bytesGroupOwner = Arrays.copyOfRange(bytesGroupOwnerAndUid, 0, bytesGroupOwnerAndUid.length - 32);
                this.groupId = Arrays.copyOfRange(bytesGroupOwnerAndUid, bytesGroupOwnerAndUid.length - 32, bytesGroupOwnerAndUid.length);
            }
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return START_CALL_MESSAGE_TYPE;
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonAnswerCallMessage extends JsonWebrtcProtocolMessage {
        String sessionDescriptionType;
        byte[] gzippedSessionDescription;

        @SuppressWarnings("unused")
        public JsonAnswerCallMessage() {
        }

        public JsonAnswerCallMessage(String sessionDescriptionType, byte[] gzippedSessionDescription) {
            this.sessionDescriptionType = sessionDescriptionType;
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @JsonProperty("sdt")
        public String getSessionDescriptionType() {
            return sessionDescriptionType;
        }

        @JsonProperty("sdt")
        public void setSessionDescriptionType(String sessionDescriptionType) {
            this.sessionDescriptionType = sessionDescriptionType;
        }

        @JsonProperty("sd")
        public byte[] getGzippedSessionDescription() {
            return gzippedSessionDescription;
        }

        @JsonProperty("sd")
        public void setGzippedSessionDescription(byte[] gzippedSessionDescription) {
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return ANSWER_CALL_MESSAGE_TYPE;
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonReconnectCallMessage extends JsonWebrtcProtocolMessage {
        String sessionDescriptionType;
        byte[] gzippedSessionDescription;
        int reconnectCounter;
        int peerReconnectCounterToOverride; // when sending a restart OFFER, this is the counter for the latest ANSWER received

        @SuppressWarnings("unused")
        public JsonReconnectCallMessage() {
        }

        public JsonReconnectCallMessage(String sessionDescriptionType, byte[] gzippedSessionDescription, int reconnectCounter, int peerReconnectCounterToOverride) {
            this.sessionDescriptionType = sessionDescriptionType;
            this.gzippedSessionDescription = gzippedSessionDescription;
            this.reconnectCounter = reconnectCounter;
            this.peerReconnectCounterToOverride = peerReconnectCounterToOverride;
        }

        @JsonProperty("sdt")
        public String getSessionDescriptionType() {
            return sessionDescriptionType;
        }

        @JsonProperty("sdt")
        public void setSessionDescriptionType(String sessionDescriptionType) {
            this.sessionDescriptionType = sessionDescriptionType;
        }

        @JsonProperty("sd")
        public byte[] getGzippedSessionDescription() {
            return gzippedSessionDescription;
        }

        @JsonProperty("sd")
        public void setGzippedSessionDescription(byte[] gzippedSessionDescription) {
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @JsonProperty("rc")
        public int getReconnectCounter() {
            return reconnectCounter;
        }

        @JsonProperty("rc")
        public void setReconnectCounter(int reconnectCounter) {
            this.reconnectCounter = reconnectCounter;
        }

        @JsonProperty("prco")
        public int getPeerReconnectCounterToOverride() {
            return peerReconnectCounterToOverride;
        }

        @JsonProperty("prco")
        public void setPeerReconnectCounterToOverride(int peerReconnectCounterToOverride) {
            this.peerReconnectCounterToOverride = peerReconnectCounterToOverride;
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return RECONNECT_CALL_MESSAGE_TYPE;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonRejectCallMessage extends JsonWebrtcProtocolMessage {
        @Override
        @JsonIgnore
        int getMessageType() {
            return REJECT_CALL_MESSAGE_TYPE;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonHangedUpMessage extends JsonWebrtcProtocolMessage {
        @Override
        @JsonIgnore
        int getMessageType() {
            return HANGED_UP_MESSAGE_TYPE;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonRingingMessage extends JsonWebrtcProtocolMessage {
        @Override
        @JsonIgnore
        int getMessageType() {
            return RINGING_MESSAGE_TYPE;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonBusyMessage extends JsonWebrtcProtocolMessage {
        @Override
        @JsonIgnore
        int getMessageType() {
            return BUSY_MESSAGE_TYPE;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonKickMessage extends JsonWebrtcProtocolMessage {
        @Override
        @JsonIgnore
        int getMessageType() {
            return KICK_MESSAGE_TYPE;
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonNewParticipantOfferMessage extends JsonWebrtcProtocolMessage {
        String sessionDescriptionType;
        byte[] gzippedSessionDescription;

        public JsonNewParticipantOfferMessage() {
        }

        public JsonNewParticipantOfferMessage(String sessionDescriptionType, byte[] gzippedSessionDescription) {
            this.sessionDescriptionType = sessionDescriptionType;
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @JsonProperty("sdt")
        public String getSessionDescriptionType() {
            return sessionDescriptionType;
        }

        @JsonProperty("sdt")
        public void setSessionDescriptionType(String sessionDescriptionType) {
            this.sessionDescriptionType = sessionDescriptionType;
        }

        @JsonProperty("sd")
        public byte[] getGzippedSessionDescription() {
            return gzippedSessionDescription;
        }

        @JsonProperty("sd")
        public void setGzippedSessionDescription(byte[] gzippedSessionDescription) {
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return NEW_PARTICIPANT_OFFER_MESSAGE_TYPE;
        }
    }


    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class JsonNewParticipantAnswerMessage extends JsonWebrtcProtocolMessage {
        String sessionDescriptionType;
        byte[] gzippedSessionDescription;

        @SuppressWarnings("unused")
        public JsonNewParticipantAnswerMessage() {
        }

        public JsonNewParticipantAnswerMessage(String sessionDescriptionType, byte[] gzippedSessionDescription) {
            this.sessionDescriptionType = sessionDescriptionType;
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @JsonProperty("sdt")
        public String getSessionDescriptionType() {
            return sessionDescriptionType;
        }

        @JsonProperty("sdt")
        public void setSessionDescriptionType(String sessionDescriptionType) {
            this.sessionDescriptionType = sessionDescriptionType;
        }

        @JsonProperty("sd")
        public byte[] getGzippedSessionDescription() {
            return gzippedSessionDescription;
        }

        @JsonProperty("sd")
        public void setGzippedSessionDescription(byte[] gzippedSessionDescription) {
            this.gzippedSessionDescription = gzippedSessionDescription;
        }

        @Override
        @JsonIgnore
        int getMessageType() {
            return NEW_PARTICIPANT_ANSWER_MESSAGE_TYPE;
        }
    }


    // endregion

    public class CallParticipant {
        private final Role role;
        private final byte[] bytesContactIdentity;
        @Nullable private final Contact contact;
        private String displayName;
        private final WebrtcPeerConnectionHolder peerConnectionHolder;
        private final WebrtcPeerConnectionHolder.DataChannelMessageListener dataChannelMessageListener;
        private boolean peerIsMuted;
        private PeerState peerState;
        private boolean markedForRemoval;
        private TimerTask timeoutTask;

        private CallParticipant(byte[] bytesOwnedIdentity, byte[] bytesContactIdentity, String displayName, Role role) {
            this.role = role;
            this.bytesContactIdentity = bytesContactIdentity;
            this.contact = AppDatabase.getInstance().contactDao().get(bytesOwnedIdentity, bytesContactIdentity);
            if (contact != null) {
                this.displayName = contact.getCustomDisplayName();
            } else {
                this.displayName = displayName;
            }
            this.peerConnectionHolder = new WebrtcPeerConnectionHolder(WebrtcCallService.this, this);
            this.dataChannelMessageListener = new DataChannelListener(this);
            this.peerConnectionHolder.setDataChannelMessageListener(this.dataChannelMessageListener);
            this.peerIsMuted = false;
            this.peerState = PeerState.INITIAL;
            this.markedForRemoval = false;
            this.timeoutTask = null;
        }

        private CallParticipant(Contact contact, Role role) {
            this.role = role;
            this.bytesContactIdentity = contact.bytesContactIdentity;
            this.contact = contact;
            this.displayName = contact.getCustomDisplayName();
            this.peerConnectionHolder = new WebrtcPeerConnectionHolder(WebrtcCallService.this, this);
            this.dataChannelMessageListener = new DataChannelListener(this);
            this.peerConnectionHolder.setDataChannelMessageListener(this.dataChannelMessageListener);
            this.peerIsMuted = false;
            this.peerState = PeerState.INITIAL;
            this.markedForRemoval = false;
            this.timeoutTask = null;
        }

        private void setPeerState(PeerState peerState) {
            this.peerState = peerState;
            notifyCallParticipantsChanged();
            switch (peerState) {
                case INITIAL:
                case CONNECTED:
                    break;
                case START_CALL_MESSAGE_SENT:
                case BUSY:
                case RINGING: {
                    createPeerStateTimeout(peerState == PeerState.RINGING);
                    break;
                }
                case RECONNECTING:
                case CONNECTING_TO_PEER: {
                    createPeerStateConnectionTimeout();
                    break;
                }
                case CALL_REJECTED:
                case HANGED_UP:
                case KICKED:
                case FAILED:
                case TIMEOUT: {
                    createRemovePeerTimeout();
                    break;
                }
            }
        }

        private void createRemovePeerTimeout() {
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
            markedForRemoval = true;
            timeoutTask = new TimerTask() {
                @Override
                public void run() {
                    executor.execute(() -> internalRemoveCallParticipant(CallParticipant.this));
                }
            };
            try {
                timeoutTimer.schedule(timeoutTask, PEER_CALL_ENDED_WAIT_MILLIS);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        private void createPeerStateTimeout(boolean ringing) {
            if (markedForRemoval) {
                return;
            }
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
            timeoutTask = new TimerTask() {
                @Override
                public void run() {
                    executor.execute(() -> {
                        setPeerState(PeerState.TIMEOUT);
                        updateStateFromPeerStates();
                    });
                }
            };
            try {
                timeoutTimer.schedule(timeoutTask, ringing ? RINGING_TIMEOUT_MILLIS : CALL_TIMEOUT_MILLIS);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        private void createPeerStateConnectionTimeout() {
            if (markedForRemoval) {
                return;
            }
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
            timeoutTask = new TimerTask() {
                @Override
                public void run() {
                    executor.execute(() -> {
                        if (callParticipants.size() > 1) {
                            reconnectAfterConnectionLoss(CallParticipant.this);
                        } else {
                            createPeerStateTimeout(false);
                        }
                    });
                }
            };
            try {
                timeoutTimer.schedule(timeoutTask, (long) (CALL_CONNECTION_TIMEOUT_MILLIS * (1 + new Random().nextFloat())));
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }


        private void setPeerIsMuted(boolean peerIsMuted) {
            this.peerIsMuted = peerIsMuted;
            notifyCallParticipantsChanged();
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytesContactIdentity);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof CallParticipant)) {
                return false;
            }
            return Arrays.equals(bytesContactIdentity, ((CallParticipant) obj).bytesContactIdentity);
        }
    }

    public static class CallParticipantPojo {
        public final byte[] bytesContactIdentity;
        @Nullable public final Contact contact;
        public final String displayName;
        public final boolean peerIsMuted;
        public final PeerState peerState;

        public CallParticipantPojo(CallParticipant callParticipant) {
            this.bytesContactIdentity = callParticipant.bytesContactIdentity;
            this.contact = callParticipant.contact;
            this.displayName = callParticipant.displayName;
            this.peerIsMuted = callParticipant.peerIsMuted;
            this.peerState = callParticipant.peerState;
        }
    }
}
