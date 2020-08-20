/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.util.banana;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.UiModeManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

public class ThemesUtils {

    public static final String TAG = "ThemesUtils";

    // Switch themes
    private static final String[] SWITCH_THEMES = {
        "com.android.system.switch.oneplus", // 0
        "com.android.system.switch.md2", // 1
        "com.android.system.switch.telegram", // 2
        "com.android.system.switch.narrow", // 3
        "com.android.system.switch.contained", // 4
        "com.android.system.switch.retro", // 5
        "com.android.system.switch.stockish", // 6
        "com.android.system.switch.fluid", // 7
        "com.android.system.switch.android_s", // 8
    };

    public static final String[] NAVBAR_STYLES = {
            "com.android.theme.navbar.android",
            "com.android.theme.navbar.asus",
            "com.android.theme.navbar.moto",
            "com.android.theme.navbar.nexus",
            "com.android.theme.navbar.old",
            "com.android.theme.navbar.oneplus",
            "com.android.theme.navbar.oneui",
            "com.android.theme.navbar.sammy",
            "com.android.theme.navbar.tecno",
    };

    // Statusbar Signal icons
    private static final String[] SIGNAL_BAR = {
            "com.custom.systemui.signalbar_a",
            "com.custom.systemui.signalbar_b",
            "com.custom.systemui.signalbar_c",
            "com.custom.systemui.signalbar_d",
            "com.custom.systemui.signalbar_e",
            "com.custom.systemui.signalbar_f",
            "com.custom.systemui.signalbar_g",
            "com.custom.systemui.signalbar_h",
    };

    // Statusbar Wifi icons
    private static final String[] WIFI_BAR = {
            "com.custom.systemui.wifibar_a",
            "com.custom.systemui.wifibar_b",
            "com.custom.systemui.wifibar_c",
            "com.custom.systemui.wifibar_d",
            "com.custom.systemui.wifibar_e",
            "com.custom.systemui.wifibar_f",
            "com.custom.systemui.wifibar_g",
            "com.custom.systemui.wifibar_h",
    };

    // QS Tile Styles
    public static final String[] QS_TILE_THEMES = {
        "com.android.systemui.qstile.default", // 0
        "com.bootleggers.qstile.trim", // 1
        "com.bootleggers.qstile.dualtone", // 2
        "com.bootleggers.qstile.dualtonetrim", // 3
        "com.android.systemui.qstile.wavey", // 4
        "com.android.systemui.qstile.ninja", // 5
        "com.android.systemui.qstile.dottedcircle", // 6
        "com.android.systemui.qstile.attemptmountain", // 7
        "com.android.systemui.qstile.squaremedo", // 8
        "com.android.systemui.qstile.inkdrop", // 9
        "com.android.systemui.qstile.cookie", // 10
        "com.android.systemui.qstile.circleoutline", // 11
        "com.bootleggers.qstile.cosmos", // 12
        "com.bootleggers.qstile.divided", // 13
        "com.bootleggers.qstile.neonlike", // 14
        "com.bootleggers.qstile.triangles", // 15
    };

    public static void updateSwitchStyle(IOverlayManager om, int userId, int switchStyle) {
        if (switchStyle == 0) {
            stockSwitchStyle(om, userId);
        } else {
            try {
                om.setEnabled(SWITCH_THEMES[switchStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change switch theme", e);
            }
        }
    }

    public static void stockSwitchStyle(IOverlayManager om, int userId) {
        for (int i = 1; i < SWITCH_THEMES.length; i++) {
            String switchtheme = SWITCH_THEMES[i];
            try {
                om.setEnabled(switchtheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // Switches qs tile style to user selected.
    public static void updateNewTileStyle(IOverlayManager om, int userId, int qsTileStyle) {
        if (qsTileStyle == 0) {
            stockNewTileStyle(om, userId);
        } else {
            try {
                om.setEnabled(QS_TILE_THEMES[qsTileStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs tile style", e);
            }
        }
    }

    // Switches qs tile style back to stock.
    public static void stockNewTileStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 1; i < QS_TILE_THEMES.length; i++) {
            String qstiletheme = QS_TILE_THEMES[i];
            try {
                om.setEnabled(qstiletheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
