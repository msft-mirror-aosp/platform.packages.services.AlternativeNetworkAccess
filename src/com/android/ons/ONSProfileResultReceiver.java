/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.ons;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Receives intent when switch device to multi-SIM mode operation is complete.
 * Intent contains extra field (EXTRA_ACTIVE_SIM_SUPPORTED_COUNT) to indicate count of SIM supported
 * after config update.
 */
public class ONSProfileResultReceiver extends BroadcastReceiver {
    private static final String TAG = ONSProfileResultReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        int simCount = intent.getIntExtra(TelephonyManager.EXTRA_ACTIVE_SIM_SUPPORTED_COUNT, 0);
        Log.d(TAG, "Mutli-SIM configed for " + simCount + "SIMs");
    }
}
