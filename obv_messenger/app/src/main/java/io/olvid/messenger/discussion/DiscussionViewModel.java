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

package io.olvid.messenger.discussion;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.FyleMessageJoinWithStatusDao;
import io.olvid.messenger.databases.dao.MessageDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.DiscussionCustomization;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.Message;


public class DiscussionViewModel extends ViewModel {
    private final AppDatabase db;
    private boolean selectingForDeletion;
    @NonNull private final MutableLiveData<Long> discussionIdLiveData;
    @NonNull private final MutableLiveData<List<Long>> selectedMessageIds;


    @NonNull private final LiveData<Discussion> discussionLiveData;
    @NonNull private final LiveData<List<Message>> messages;
    @NonNull private final LiveData<List<Contact>> discussionContacts;
    @NonNull private final LiveData<MessageDao.UnreadCountAndFirstMessage> unreadCountAndFirstMessage;
    @NonNull private final LiveData<DiscussionCustomization> discussionCustomization;
    @NonNull private final LiveData<Integer>  newDetailsUpdate;

    public DiscussionViewModel() {
        db = AppDatabase.getInstance();
        selectingForDeletion = false;
        discussionIdLiveData = new MutableLiveData<>();
        selectedMessageIds = new MutableLiveData<>();

        messages = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.messageDao().getDiscussionMessages(discussionId);
        });

        discussionLiveData = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionDao().getByIdAsync(discussionId);
        });

        discussionContacts = Transformations.switchMap(discussionLiveData, discussion -> {
            if (discussion == null) {
                return null;
            }
            if (discussion.bytesGroupOwnerAndUid != null) {
                return db.contactGroupJoinDao().getGroupContacts(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid);
            } else if (discussion.bytesContactIdentity != null) {
                return db.contactDao().getAsList(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity);
            } else {
                return null;
            }
        });

        unreadCountAndFirstMessage = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.messageDao().getUnreadCountAndFirstMessage(discussionId);
        });

        discussionCustomization = Transformations.switchMap(discussionIdLiveData, discussionId -> {
            if (discussionId == null) {
                return null;
            }
            return db.discussionCustomizationDao().getLiveData(discussionId);
        });

        newDetailsUpdate = Transformations.switchMap(discussionLiveData, discussion -> {
            if (discussion != null && discussion.bytesGroupOwnerAndUid != null) {
                return Transformations.map(AppDatabase.getInstance().groupDao().getLiveData(discussion.bytesOwnedIdentity, discussion.bytesGroupOwnerAndUid), (Group group) -> {
                    if (group != null) {
                        return group.newPublishedDetails;
                    }
                    return null;
                });
            } else if (discussion != null && discussion.bytesContactIdentity != null) {
                return Transformations.map(AppDatabase.getInstance().contactDao().getAsync(discussion.bytesOwnedIdentity, discussion.bytesContactIdentity), (Contact contact) -> {
                    if (contact != null) {
                        return contact.newPublishedDetails;
                    }
                    return null;
                });
            } else {
                return new MutableLiveData<>(Contact.PUBLISHED_DETAILS_NOTHING_NEW);
            }
        });
    }


    public void setDiscussionId(Long discussionId) {
        discussionIdLiveData.postValue(discussionId);
    }

    public Long getDiscussionId() {
        return discussionIdLiveData.getValue();
    }

    @NonNull
    public LiveData<Discussion> getDiscussion() {
        return discussionLiveData;
    }

    @NonNull
    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    @NonNull
    public LiveData<List<Contact>> getDiscussionContacts() {
        return discussionContacts;
    }

    @NonNull
    public LiveData<MessageDao.UnreadCountAndFirstMessage> getUnreadCountAndFirstMessage() {
        return unreadCountAndFirstMessage;
    }

    @NonNull
    public LiveData<DiscussionCustomization> getDiscussionCustomization() {
        return discussionCustomization;
    }

    @NonNull
    public LiveData<Integer> getNewDetailsUpdate() {
        return newDetailsUpdate;
    }

    // region select for deletion
    public boolean isSelectingForDeletion() {
        return selectingForDeletion;
    }

    public void selectMessageId(long messageId) {
        List<Long> ids;
        if (selectedMessageIds.getValue() == null) {
            ids = new ArrayList<>();
        } else {
            ids = new ArrayList<>(selectedMessageIds.getValue().size());
            ids.addAll(selectedMessageIds.getValue());
        }
        if (ids.contains(messageId)) {
            ids.remove(messageId);
            if (ids.size() == 0) {
                selectingForDeletion = false;
            }
        } else {
            ids.add(messageId);
            selectingForDeletion = true;
        }
        selectedMessageIds.postValue(ids);
    }

    public void unselectMessageId(long messageId) {
        List<Long> ids = selectedMessageIds.getValue();
        if (ids != null) {
            ids.remove(messageId);
            selectedMessageIds.postValue(ids);
        }
    }


    public LiveData<List<Long>> getSelectedMessageIds() {
        return selectedMessageIds;
    }

    public void deselectAll() {
        selectingForDeletion = false;
        selectedMessageIds.postValue(new ArrayList<>());
    }

    public LiveData<Long> getDiscussionIdLiveData() {
        return discussionIdLiveData;
    }
    // endregion

}
