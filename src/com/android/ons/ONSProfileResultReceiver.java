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
 * ONSProfileResultReceiver triggered when an async requests such as download subscription,
 * activate subscription, delete subscription and switch to multi-SIM mode are processed.
 *
 * Received intent is forwarded to either {@link ONSProfileConfigurator} or
 * {@link ONSProfileDownloader} based on component name param stored in Intent.
 *
 * BroadcastReceiver.goAsync and PendingResult.finish() methods are used to asynchronously process
 * the received intent.
 */
public class ONSProfileResultReceiver extends BroadcastReceiver {

    private static final String TAG = ONSProfileResultReceiver.class.getName();

    public static final String ACTION_ONS_RESULT_CALLBACK =
            "com.android.ons.ONSProfileResultReceiver.CALLBACK";

    @Override
    public void onReceive(Context context, Intent intent) {
        int resultCode = getResultCode();
        callbackIntentHandler(intent, resultCode);
    }

    protected void callbackIntentHandler(Intent intent, int resultCode) {
        String action = intent.getAction();
        String compName = intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME);

        if (action.equals(ACTION_ONS_RESULT_CALLBACK)) {
            if (compName.equals(ONSProfileConfigurator.class.getName())) {
                WorkerThread workerThread = new WorkerThread(goAsync(),
                        () -> ONSProfileConfigurator.onCallbackIntentReceived(intent, resultCode));
                workerThread.start();
            } else if (compName.equals(ONSProfileDownloader.class.getName())) {
                WorkerThread workerThread = new WorkerThread(goAsync(),
                        () -> ONSProfileDownloader.onCallbackIntentReceived(intent, resultCode));
                workerThread.start();
            }
        } else if (action.equals(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED)) {
            int simCount = intent.getIntExtra(TelephonyManager.EXTRA_ACTIVE_SIM_SUPPORTED_COUNT, 0);
            Log.d(TAG, "Mutli-SIM configed for " + simCount + "SIMs");
        }
    }

    private class WorkerThread extends Thread {
        private final PendingResult mAsyncResult;
        private final Runnable mRunnable;
        WorkerThread(PendingResult asyncResult, Runnable runnable) {
            mAsyncResult = asyncResult;
            mRunnable = runnable;
        }

        @Override
        public void run() {
            super.run();
            mRunnable.run();
            mAsyncResult.finish();
        }
    }
}
