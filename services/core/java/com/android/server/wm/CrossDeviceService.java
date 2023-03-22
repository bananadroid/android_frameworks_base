/*
 * Copyright (C) 2023 Microsoft Corporation
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

package com.android.server.wm;

import android.app.ActivityManager;
import android.app.CrossDeviceManager;
import android.app.ICrossDeviceService;
import android.app.RemoteTaskConstants;
import android.app.IRemoteTaskHandler;
import android.app.RemoteTaskInfo;
import android.app.SystemTaskContext;
import android.app.RemoteTaskParams;
import android.app.IRemoteTaskInstanceBroker;
import android.os.Bundle;
import android.os.IThermalService;
import android.content.Intent;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Parcel;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Temperature;
import android.os.RemoteCallbackList;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * System service for managing Cross Device features
 */
public class CrossDeviceService extends ICrossDeviceService.Stub {
    private static final String TAG = CrossDeviceService.class.getSimpleName();

    private final Object mServiceLock = new Object();
    private final Set<String> mActivityBackgroundSet = new CopyOnWriteArraySet<>();
    private final Set<Integer> mPermissionGrantedSet = new CopyOnWriteArraySet<>();
    private final ActivityTaskManagerService mActivityTaskManagerService;
    private IThermalService mThermalService;
    private final Context mContext;
    private final PowerManager mPowerManager;
    private final ActivityManager mActivityManager;
    private final IRemoteTaskInstanceBroker mRemoteTaskInstanceBroker;
    private OnClientDiedListener mClientDiedListener;

    private final RemoteCallbackList<IRemoteTaskHandler> mRemoteHandlerList = new RemoteCallbackList<IRemoteTaskHandler>() {
        @Override
        public void onCallbackDied(IRemoteTaskHandler handler) {
            super.onCallbackDied(handler);
            RemoteTaskLogger.i(TAG, "onCallbackDied");
            handleClientDied(handler);
        }
    };

    interface OnClientDiedListener {
        void onClientDied();
    }

    public CrossDeviceService(Context context, ActivityTaskManagerService service) {
        mContext = context;
        mActivityTaskManagerService = service;
        mPowerManager = context.getSystemService(PowerManager.class);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mRemoteTaskInstanceBroker = new RemoteTaskInstanceBroker(context, service);
    }

    public void setClientDiedListener(OnClientDiedListener clientDiedListener) {
        //Make sure ClientDiedListener assignment is thread-safe, note that every place where
        //use ClientDiedListener should wrap int mServiceLock
        synchronized (mServiceLock) {
            mClientDiedListener = clientDiedListener;
        }
    }

    /**
     * Method for handling client die
     *
     * @param handler IRemoteTaskHandler
     */
    private void handleClientDied(IRemoteTaskHandler handler) {
        synchronized (mServiceLock) {
            if (!isAnyClientAliveInService() && mClientDiedListener != null) {
                mClientDiedListener.onClientDied();
            }
        }
    }

    private List<String> getActivityBakcgroundSnapshot() {
        return new ArrayList(mActivityBackgroundSet);
    }

    boolean isAnyClientAliveInService() {
        return mRemoteHandlerList.getRegisteredCallbackCount() > 0;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (!CrossDeviceManager.isCallerAllowed(mContext)) {
            throw new RemoteException("Caller is not allowed");
        }
        return super.onTransact(code, data, reply, flags);
    }

    /**
     * Inject input event
     *
     * @param event Input event
     * @param injectInputEventModeAsync event mode async
     */
    @Override
    public void injectInputEvent(InputEvent event, int injectInputEventModeAsync) {
        final long token = Binder.clearCallingIdentity();
        try {
            InputManager.getInstance().injectInputEvent(event, injectInputEventModeAsync);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Get top running package name which will be used by input injection
     *
     * @return top running package name
     */
    @Override
    public String getTopRunningPackageName() {
        final long token = Binder.clearCallingIdentity();
        try {
            return mActivityManager.getRunningTasks(1).get(0).topActivity.getPackageName();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Wake up phone when injecting input from main display
     *
     * @param time Wake up time
     */
    @Override
    public void wakeUp(long time) {
        final long token = Binder.clearCallingIdentity();
        try {
            mPowerManager.wakeUp(time, 0, "");
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * get device temperature
     */
    @Override
    public Bundle getDeviceTemperature() {
        final long token = Binder.clearCallingIdentity();
        try {
            Bundle res = new Bundle();
            try {
                if (mThermalService == null) {
                    mThermalService = IThermalService.Stub.asInterface(
                            ServiceManager.getService(Context.THERMAL_SERVICE));
                }
                if (mThermalService != null) {
                    final Temperature[] temps = mThermalService.getCurrentTemperaturesWithType(Temperature.TYPE_BATTERY);
                    if (temps != null && temps.length > 0) {
                        RemoteTaskLogger.d(TAG, "getCurrentTemperaturesWithType, size = " + temps.length);
                        float sum = 0.0f;
                        for (Temperature temp : temps) {
                            sum += temp.getValue();
                        }
                        res.putFloat(RemoteTaskConstants.DEVICE_TEMPERATURE, sum / temps.length);
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            RemoteTaskLogger.d(TAG, "getDeviceTemperatureBundle, res = " + res);
            return res;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * get the threshold of overheat
     */
    @Override
    public Bundle getOverheatThreshold() {
        Bundle temp = new Bundle();
        // if you want to set the OverheatThreshold by yourself, please activate the next line and modify expect_value to your value.
        // temp.putFloat(RemoteTaskConstants.OVERHEAT_THRESHOLD, expect_value);
        return temp;
    }

    /**
     * get the threshold of resume overheat
     */
    @Override
    public Bundle getResumeOverheatThreshold() {
        Bundle temp = new Bundle();
        // if you want to set the OverheatThreshold by yourself, please activate the next line and modify expect_value to your value.
        // temp.putFloat(RemoteTaskConstants.RESUME_OVERHEAT_THRESHOLD, expect_value);
        return temp;
    }

    /**
     * get the maximum app count of Suspend
     */
    @Override
    public Bundle getMaximumAppCount() {
        Bundle temp = new Bundle();
        // if you want to set the MAXIMUM_RUNNING_APP_COUNT by yourself, please activate the next line and modify expect_value to your value.
        // temp.putFloat(RemoteTaskConstants.MAXIMUM_RUNNING_APP_COUNT, expect_value);
        return temp;
    }

    /**
     * Update white list for this service
     *
     * @param activityList List<String>
     */
    @Override
    public void updateBackgroundActivityList(List<String> activityList) throws RemoteException {
        RemoteTaskLogger.i(TAG, "updateBackgroundActivityList");
        mActivityBackgroundSet.addAll(activityList);
    }

    /**
     * Check if package is from background white list with current binder caller
     *
     * @return true if activity is from background white list, false otherwise
     */
    @Override
    public boolean isFromBackgroundWhiteList() {
        return isFromBackgroundWhiteListByUid(Binder.getCallingUid());
    }

    /**
     * Check if package is from background white list
     *
     * @param uid caller uid
     * @return true if activity is from background white list, false otherwise
     */
    @Override
    public boolean isFromBackgroundWhiteListByUid(int uid) {
        String pkgName = mActivityTaskManagerService.
                            getPackageManagerInternalLocked().getNameForUid(uid);
        if (TextUtils.isEmpty(pkgName)) {
            return false;
        }

        List<String> backGroundList = getActivityBakcgroundSnapshot();
        for (String value : backGroundList) {
            if (TextUtils.equals(pkgName, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if touch is required
     * @Param packageName app package name
     * @return true if touch is required
     */
    @Override
    public boolean isTouchRequired(String packageName) throws RemoteException {
        try {
            PackageManager packageManager = mActivityTaskManagerService.mContext.getPackageManager();
            FeatureInfo[] features = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_CONFIGURATIONS).reqFeatures;
            if (features != null) {
                for (int i = 0; i < features.length; i++) {
                    if (TextUtils.equals(features[i].name, PackageManager.FEATURE_TOUCHSCREEN)
                            && features[i].flags == 1) {
                        // android.hardware.touchscreen flag == 1 means this app required touchscreen
                        RemoteTaskLogger.d(TAG, "isTouchRequired(): "
                                + packageName + " : true");
                        return true;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        RemoteTaskLogger.d(TAG, "isTouchRequired(): " + packageName + " : false");
        return false;
    }

    /**
     * Get {@link #android.app.IRemoteTaskInstanceBroker}
     * @return IRemoteTaskInstanceBroker stub
     */
    @Override
    public IRemoteTaskInstanceBroker getRemoteTaskInstanceBroker() {
        RemoteTaskLogger.i(TAG, "getRemoteTaskInstanceBroker");
        return mRemoteTaskInstanceBroker;
    }

    /**
     * Each client who wants to handle remote task lifecycle event should register a {@link IRemoteTaskHandler}
     *
     * @param handler IRemoteTaskHandler
     * @throws RemoteException
     */
    @Override
    public void registerRemoteTaskHandler(IRemoteTaskHandler handler) throws RemoteException {
        RemoteTaskLogger.i(TAG, "registerRemoteTaskHandler");
        mRemoteHandlerList.register(handler);
    }

    /**
     * Unregister remote task handler
     *
     * @param handler IRemoteTaskHandler
     * @throws RemoteException
     */
    @Override
    public void unRegisterRemoteTaskHandler(IRemoteTaskHandler handler) throws RemoteException {
        RemoteTaskLogger.i(TAG, "unRegisterRemoteTaskHandler");
        mRemoteHandlerList.unregister(handler);
        handleClientDied(handler);
    }

    /**
     * Set media projection permission granted
     * @param uid user id to be grant permission
     */
    @Override
    public void setPermissionGranted(int uid) {
        if (uid > 0) {
            mPermissionGrantedSet.add(uid);
        }
    }

    /**
     * Get media projection permission granted
     * @param uid user id to get permission grant status
     * @return true if permission granted, false otherwise
     */
    @Override
    public boolean getPermissionGranted(int uid) {
        return mPermissionGrantedSet.contains(uid);
    }

    /**
     * Call remote task handler to verify remote task context
     *
     * @param taskContext System Task context
     * @return Remote Task launch options
     */
    RemoteTaskParams verifyRemoteTask(SystemTaskContext taskContext) {
        RemoteTaskLogger.i(TAG, "verifyRemoteTask");
        RemoteTaskParams params = null;
        synchronized (mServiceLock) {
            try {
                int count = mRemoteHandlerList.beginBroadcast();
                if (count > 0) {
                    IRemoteTaskHandler handler = mRemoteHandlerList.getBroadcastItem(0);
                    params = handler.verifyRemoteTask(taskContext);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mRemoteHandlerList.finishBroadcast();
            }
        }

        if (params == null) {
            params = RemoteTaskParams.create(taskContext.getUuid(),
                    RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_COMMON, taskContext.getDisplayId());
        }
        return params;
    }

    /**
     * Call service to start a new remote task
     *
     * @param taskInfo remote task informations
     */
    void activateRemoteTask(RemoteTaskInfo taskInfo) {
        RemoteTaskLogger.i(TAG, "activateRemoteTask");
        synchronized (mServiceLock) {
            try {
                int count = mRemoteHandlerList.beginBroadcast();
                for (int i = 0; i < count; i++) {
                    IRemoteTaskHandler handler = mRemoteHandlerList.getBroadcastItem(i);
                    handler.activateRemoteTask(taskInfo);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mRemoteHandlerList.finishBroadcast();
            }
        }
    }

    /**
     * Call remote task handler to get remote task info list
     *
     * @return Remote task info list
     */
    List getRemoteTaskInfoList() {
        RemoteTaskLogger.i(TAG, "getRemoteTaskInfoList");
        List taskList = null;
        synchronized (mServiceLock) {
            try {
                int count = mRemoteHandlerList.beginBroadcast();
                if (count > 0) {
                    IRemoteTaskHandler handler = mRemoteHandlerList.getBroadcastItem(0);
                    taskList = handler.getRemoteTaskInfoList();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mRemoteHandlerList.finishBroadcast();
            }
        }

        if (taskList == null) {
            taskList = new ArrayList<String>();
        }
        return taskList;
    }

    /**
     * Notify service the remote task has been removed
     *
     * @param taskId remote task id to be removed
     */
    void notifyRemoteTaskRemoved(int taskId) {
        RemoteTaskLogger.i(TAG, "notifyRemoteTaskRemoved=" + taskId);
        synchronized (mServiceLock) {
            try {
                int count = mRemoteHandlerList.beginBroadcast();
                for (int i = 0; i < count; i++) {
                    IRemoteTaskHandler handler = mRemoteHandlerList.getBroadcastItem(i);
                    handler.notifyRemoteTaskRemoved(taskId);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mRemoteHandlerList.finishBroadcast();
            }
        }
    }

    /**
     * Notify service an empty UUID remote task detected
     *
     * @param intent the intent of remote task
     */
    void notifyRemoteTaskEmptyUUIDetected(Intent intent) {
        RemoteTaskLogger.i(TAG, "notifyRemoteTaskEmptyUUIDetected");
        synchronized (mServiceLock) {
            try {
                int count = mRemoteHandlerList.beginBroadcast();
                for (int i = 0; i < count; i++) {
                    IRemoteTaskHandler handler = mRemoteHandlerList.getBroadcastItem(i);
                    handler.notifyRemoteTaskEmptyUUIDetected(intent);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mRemoteHandlerList.finishBroadcast();
            }
        }
    }

    /**
     * Notify service the display of remote task is switched
     *
     * @param displayId current display id
     */
    void notifyDisplaySwitched(int displayId) {
        RemoteTaskLogger.i(TAG, "notifyDisplaySwitched");
        synchronized (mServiceLock) {
            try {
                int count = mRemoteHandlerList.beginBroadcast();
                for (int i = 0; i < count; i++) {
                    IRemoteTaskHandler handler = mRemoteHandlerList.getBroadcastItem(i);
                    handler.notifyDisplaySwitched(displayId);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mRemoteHandlerList.finishBroadcast();
            }
        }
    }

    /**
     * Notify remote task service to handle showing secure content changed
     *
     * @param taskId                  remote task id
     * @param isShowingSecuredContent if remote task is showing secure content
     */
    void notifyRemoteShowingSecuredContentChanged(int taskId, boolean isShowingSecuredContent) {
        RemoteTaskLogger.i(TAG, "notifyRemoteShowingSecuredContentChanged");
        synchronized (mServiceLock) {
            try {
                int count = mRemoteHandlerList.beginBroadcast();
                for (int i = 0; i < count; i++) {
                    IRemoteTaskHandler handler = mRemoteHandlerList.getBroadcastItem(i);
                    handler.notifyRemoteShowingSecuredContentChanged(taskId, isShowingSecuredContent);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } finally {
                mRemoteHandlerList.finishBroadcast();
            }
        }
    }
}
