/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2021-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.util.custom;

import android.app.Application;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static volatile boolean sIsGms = false;
    public static final String PACKAGE_GMS = "com.google.android.gms";
    private static final Map<String, Object> propsToChangePixel5;
    private static final Map<String, Object> propsToChangePixel7Pro;
    private static final Map<String, Object> propsToChangePixelXL;
    private static final Map<String, ArrayList<String>> propsToKeep;
    private static final String[] extraPackagesToChange = {
        "com.android.chrome",
        "com.android.vending",
        "com.breel.wallpapers20",
        "com.nhs.online.nhsonline"
    };

    private static final String[] packagesToChangePixel7Pro = {
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.privacy.wildlife",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.inputmethod.latin"
    };

    private static final String[] packagesToChangePixelXL = {
            "com.google.android.apps.photos",
            "com.samsung.accessory",
            "com.samsung.accessory.fridaymgr",
            "com.samsung.accessory.berrymgr",
            "com.samsung.accessory.neobeanmgr",
            "com.samsung.android.app.watchmanager",
            "com.samsung.android.geargplugin",
            "com.samsung.android.gearnplugin",
            "com.samsung.android.modenplugin",
            "com.samsung.android.neatplugin",
            "com.samsung.android.waterplugin"
    };

    private static final String[] packagesToKeep = {
            "com.google.android.GoogleCamera",
            "com.google.android.GoogleCamera.Cameight",
            "com.google.android.GoogleCamera.Go",
            "com.google.android.GoogleCamera.Urnyx",
            "com.google.android.GoogleCameraAsp",
            "com.google.android.GoogleCameraCVM",
            "com.google.android.GoogleCameraEng",
            "com.google.android.GoogleCameraEng2",
            "com.google.android.GoogleCameraGood",
            "com.google.android.MTCL83",
            "com.google.android.UltraCVM",
            "com.google.android.apps.cameralite",
            "com.google.android.dialer",
            "com.google.ar.core",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.recorder",
            "com.google.android.apps.wearables.maestro.companion"
    };

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put("com.google.android.settings.intelligence", new ArrayList<>(Collections.singletonList("FINGERPRINT")));
        propsToChangePixel7Pro = new HashMap<>();
        propsToChangePixel7Pro.put("BRAND", "google");
        propsToChangePixel7Pro.put("MANUFACTURER", "Google");
        propsToChangePixel7Pro.put("DEVICE", "cheetah");
        propsToChangePixel7Pro.put("PRODUCT", "cheetah");
        propsToChangePixel7Pro.put("MODEL", "Pixel 7 Pro");
        propsToChangePixel7Pro.put("FINGERPRINT", "google/cheetah/cheetah:13/TD1A.221105.003/9229241:user/release-keys");
        propsToChangePixel5 = new HashMap<>();
        propsToChangePixel5.put("BRAND", "google");
        propsToChangePixel5.put("MANUFACTURER", "Google");
        propsToChangePixel5.put("DEVICE", "redfin");
        propsToChangePixel5.put("PRODUCT", "redfin");
        propsToChangePixel5.put("MODEL", "Pixel 5");
        propsToChangePixel5.put("FINGERPRINT", "google/redfin/redfin:13/TP1A.221105.002/9080065:user/release-keys");
        propsToChangePixelXL = new HashMap<>();
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
    }

    public static void setProps(String packageName) {
        if (packageName == null) {
            return;
        }
        if (packageName.equals(PACKAGE_GMS)) {
                final String processName = Application.getProcessName();
                if (processName.equals("com.google.android.gms.unstable")) {
                    sIsGms = true;
                    setPropValue("FINGERPRINT", "google/angler/angler:6.0/MDB08L/2343525:user/release-keys");
                    setPropValue("MODEL", "angler");
                }
                return;
        }
        if (Arrays.asList(packagesToKeep).contains(packageName)) {
            return;
        }
        if (packageName.startsWith("com.google.")
                || Arrays.asList(extraPackagesToChange).contains(packageName)) {

            if (packageName.equals("com.google.android.apps.photos")) {
                if (!SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", true))
                    return;
            }

            Map<String, Object> propsToChange = propsToChangePixel5;

            if (Arrays.asList(packagesToChangePixel7Pro).contains(packageName)) {
                propsToChange = propsToChangePixel7Pro;
            }

            if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
            for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                if (propsToKeep.containsKey(packageName) && propsToKeep.get(packageName).contains(key)) {
                    if (DEBUG) Log.d(TAG, "Not defining " + key + " prop for: " + packageName);
                    continue;
                }
                if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + packageName);
                setPropValue(key, value);
                }
            }
        if (Arrays.asList(packagesToChangePixelXL).contains(packageName)){
            if (DEBUG){
                Log.d(TAG, "Defining props for: " + packageName);
            }
            for (Map.Entry<String, Object> prop : propsToChangePixelXL.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                setPropValue(key, value);
            }
        }

       // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")){
            setPropValue("FINGERPRINT", Build.DATE);
        }
    }

    private static void setPropValue(String key, Object value){
        try {
            if (DEBUG) Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet
        if (sIsGms && isCallerSafetyNet()) {
            throw new UnsupportedOperationException();
        }
    }
}
