/*
 * Copyright (C) 2017 Paranoid Android
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

package com.android.dialer;

import android.app.Activity;
import android.app.IThemeCallback;
import android.app.ThemeManager;
import android.content.Context;

public class ThemeController {

    private Activity mActivity;

    private ThemeManager mThemeManager;
    private int mTheme;

    public ThemeController(Activity activity) {
        mActivity = activity;
        mThemeManager = (ThemeManager) activity.getSystemService(Context.THEME_SERVICE);
        if (mThemeManager != null) {
            mThemeManager.addCallback(mThemeCallback);
        }
    }

    public void applyTheme() {
        if (mActivity != null) {
            mActivity.getTheme().applyStyle(mTheme, true);
        }
    }

    private final IThemeCallback mThemeCallback = new IThemeCallback.Stub() {

        @Override
        public void onThemeChanged(int themeMode, int color) {
            onCallbackAdded(themeMode, color);
            if (mActivity != null) {
                mActivity.runOnUiThread(() -> {
                    mActivity.recreate();
                });
            }
        }

        @Override
        public void onCallbackAdded(int themeMode, int color) {
            mTheme = color;
        }
    };
}
