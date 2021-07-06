/*
 * Copyright (C) 2018 crDroid Android Project
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

package com.android.systemui.statusbar.logo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.DarkIconDispatcher;

public class LogoImageView extends ImageView {

    private Context mContext;

    private boolean mAttached;
    private boolean mCustomLogo;
    private int mCustomLogoPosition;
    private int mCustomLogoStyle;
    private int mTintColor = Color.WHITE;
    private final Handler mHandler = new Handler();
    private ContentResolver mContentResolver;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_POSITION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_STYLE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    public LogoImageView(Context context) {
        this(context, null);
    }

    public LogoImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogoImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        mContext = context;
        mSettingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached) {
            return;
        }
        mAttached = true;
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached) {
            return;
        }
        mAttached = false;
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mCustomLogo && mCustomLogoPosition == 0) {
            updateCustomLogo();
        }
    }

    public void updateCustomLogo() {
        Drawable drawable = null;

        if (!mCustomLogo || mCustomLogoPosition == 1) {
            setImageDrawable(null);
            setVisibility(View.GONE);
            return;
        } else {
            setVisibility(View.VISIBLE);
        }

        if (mCustomLogoStyle == 0) {
            drawable = mContext.getDrawable(R.drawable.ic_bananagear_logo);
        } else if (mCustomLogoStyle == 1) {
            drawable = mContext.getDrawable(R.drawable.ic_bananadroid_logo);
        } else if (mCustomLogoStyle == 2) {
            drawable = mContext.getDrawable(R.drawable.ic_adidas);
        } else if (mCustomLogoStyle == 3) {
            drawable = mContext.getDrawable(R.drawable.ic_airjordan);
        } else if (mCustomLogoStyle == 4) {
            drawable = mContext.getDrawable(R.drawable.ic_apple_logo);
        } else if (mCustomLogoStyle == 5) {
            drawable = mContext.getDrawable(R.drawable.ic_avengers);
        } else if (mCustomLogoStyle == 6) {
            drawable = mContext.getDrawable(R.drawable.ic_batman);
        } else if (mCustomLogoStyle == 7) {
            drawable = mContext.getDrawable(R.drawable.ic_batman_tdk);
        } else if (mCustomLogoStyle == 8) {
            drawable = mContext.getDrawable(R.drawable.ic_beats);
        } else if (mCustomLogoStyle == 9) {
            drawable = mContext.getDrawable(R.drawable.ic_biohazard);
        } else if (mCustomLogoStyle == 10) {
            drawable = mContext.getDrawable(R.drawable.ic_blackberry);
        } else if (mCustomLogoStyle == 11) {
            drawable = mContext.getDrawable(R.drawable.ic_cannabis);
        } else if (mCustomLogoStyle == 12) {
            drawable = mContext.getDrawable(R.drawable.ic_fire);
        } else if (mCustomLogoStyle == 13) {
            drawable = mContext.getDrawable(R.drawable.ic_nike);
        } else if (mCustomLogoStyle == 14) {
            drawable = mContext.getDrawable(R.drawable.ic_pac_man);
        } else if (mCustomLogoStyle == 15) {
            drawable = mContext.getDrawable(R.drawable.ic_puma);
        } else if (mCustomLogoStyle == 16) {
            drawable = mContext.getDrawable(R.drawable.ic_rog);
        } else if (mCustomLogoStyle == 17) {
            drawable = mContext.getDrawable(R.drawable.ic_superman);
        } else if (mCustomLogoStyle == 18) {
            drawable = mContext.getDrawable(R.drawable.ic_windows);
        } else if (mCustomLogoStyle == 19) {
            drawable = mContext.getDrawable(R.drawable.ic_xbox);
        }

        setImageDrawable(null);

        clearColorFilter();

        drawable.setTint(mTintColor);
        setImageDrawable(drawable);
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mCustomLogo = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_LOGO, 0) == 1;
        mCustomLogoPosition = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_LOGO_POSITION, 0);
        mCustomLogoStyle = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_LOGO_STYLE, 0);
        updateCustomLogo();
    }
}
