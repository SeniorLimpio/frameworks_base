/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 CyanogenMod Project
 * Copyright (C) 2013 The SlimRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class UserTile extends QuickSettingsTile {

    private static final String TAG = "UserTile";
    private Handler mHandler;
    private Drawable userAvatar;
    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;

    public UserTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc, R.layout.quick_settings_tile_user);
        mHandler = handler;
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mQsc.mBar.collapseAllPanels(true);
                final UserManager um = UserManager.get(mContext);
                if (um.getUsers(true).size() > 1) {
                    // Since keyguard and systemui were merged into the same process to save
                    // memory, they share the same Looper and graphics context.  As a result,
                    // there's no way to allow concurrent animation while keyguard inflates.
                    // The workaround is to add a slight delay to allow the animation to finish.
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                WindowManagerGlobal.getWindowManagerService().lockNow(null);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Couldn't show user switcher", e);
                            }
                        }
                    }, 400); // TODO: ideally this would be tied to the collapse of the panel
                } else {
                    final Cursor cursor = mContext.getContentResolver().query(
                            Profile.CONTENT_URI, null, null, null, null);
                    if (cursor.moveToNext() && !cursor.isNull(0)) {
                        Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                                mContext, v, ContactsContract.Profile.CONTENT_URI,
                                ContactsContract.QuickContact.MODE_LARGE, null);
                        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                    } else {
                        Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                        intent.putExtra(INTENT_EXTRA_NEW_LOCAL_PROFILE, true);
                        startSettingsActivity(intent);
                    }
                    cursor.close();
                if (isFlipTilesEnabled()) {
                    flipTile(0);
                }
            }
        };
        qsc.registerAction(Intent.ACTION_USER_SWITCHED, this);
        qsc.registerAction(ContactsContract.Intents.ACTION_PROFILE_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        queryForUserInformation();
    }

    @Override
    void onPostCreate() {
        queryForUserInformation();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        queryForUserInformation();
    }

    void updateQuickSettings(Drawable userAvatar) {
        ImageView iv = (ImageView) mTile.findViewById(R.id.user_imageview);
        TextView tv = (TextView) mTile.findViewById(R.id.user_textview);
        if (tv != null) {
            if(mLabel != null){
                tv.setText(mLabel);
            }
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            if (mTileTextColor != -2) {
                tv.setTextColor(mTileTextColor);
            }
        }
        if(iv != null){
            iv.setImageDrawable(userAvatar);
        }
    }

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                final UserManager um = UserManager.get(mContext);

                // Fall back to the UserManager nickname if we can't read the name from the local
                // profile below.
                String name = userName;
                Drawable avatar = null;
                Bitmap rawAvatar = um.getUserIcon(userId);
                if (rawAvatar != null) {
                    avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                } else {
                    avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                }

                // If it's a single-user device, get the profile name, since the nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {Phone._ID, Phone.DISPLAY_NAME},
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                setUserTileInfo(result.first, result.second);
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
    }

    void setUserTileInfo(String name, Drawable avatar) {
        mLabel = name;
        userAvatar = avatar;
        updateQuickSettings(userAvatar);
    }

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
    }

}
