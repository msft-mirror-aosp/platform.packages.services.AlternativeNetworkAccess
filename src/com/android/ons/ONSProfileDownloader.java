/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.telephony.SubscriptionInfo;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import java.util.Random;

public class ONSProfileDownloader {

    interface IONSProfileDownloaderListener {
        void onDownloadComplete(int primarySubId);
    }

    private static final String TAG = ONSProfileDownloader.class.getName();
    private static final int KILO_BYTES = 1024;
    private static final String PARAM_PRIMARY_SUBID = "PrimarySubscriptionID";
    private static final String PARAM_REQUEST_TYPE = "REQUEST";
    private static final int REQUEST_CODE_DOWNLOAD_SUB = 1;
    private static final int REQUEST_CODE_DOWNLOAD_RETRY = 2;
    private static IONSProfileDownloaderListener sListener;
    private static Handler sHandler;

    private final Context mContext;
    private final ONSProfileConfigurator mONSProfileConfig;

    public ONSProfileDownloader(Context context, ONSProfileConfigurator onsProfileConfigurator,
                                IONSProfileDownloaderListener listener) {
        mContext = context;
        sListener = listener;
        mONSProfileConfig = onsProfileConfigurator;
        sHandler = new DownloadHandler();
    }

    class DownloadHandler extends Handler {
        private int mDownloadRetryCount = 0;
        private final Random mRandom;

        DownloadHandler() {
            mRandom = new Random();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_CODE_DOWNLOAD_SUB: { //(arg1 -> ResultCode, arg2->Primary SubId)
                    Log.d(TAG, "REQUEST_CODE_DOWNLOAD_SUB callback received");
                    if (msg.arg1 == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                        Log.d(TAG, "Download successful");
                        ONSProfileDownloader.sListener.onDownloadComplete(msg.arg2);
                    } else if (msg.arg1 == EuiccManager.ERROR_EUICC_INSUFFICIENT_MEMORY) {
                        Log.d(TAG, "Download ERR: EUICC_INSUFFICIENT_MEMORY");
                        //arg2-pSIM SubId
                        if (!mONSProfileConfig.deleteOpportunisticSubscriptions(msg.arg2)) {
                            Log.e(TAG, "Unable to free eUICC memory for new eSIM download.");
                        }
                    } else {
                        //retry logic
                        mDownloadRetryCount++;
                        Log.e(TAG, "Download retry count :" + mDownloadRetryCount);
                        if (mDownloadRetryCount >= mONSProfileConfig
                                .getDownloadRetryMaxAttemptsVal(msg.arg2)) {
                            Log.e(TAG, "Max download retry attempted. Stopping retry");
                            return;
                        }
                        //Calculate next retry time delay.
                        int maxTime = (int) Math.pow(2, mDownloadRetryCount * mONSProfileConfig
                                .getDownloadRetryBackOffTimerVal(msg.arg2));

                        //Random select delay, add +1 for non-zero value and convert to millisecond
                        int delay = (mRandom.nextInt(maxTime) + 1) * 1000;

                        Message retryMsg = new Message();
                        retryMsg.what = REQUEST_CODE_DOWNLOAD_RETRY;
                        retryMsg.arg2 = msg.arg2; //arg2 -> primary SubId
                        sendMessageDelayed(msg, delay);

                        Log.d(TAG, "Download failed. Retry after :" + delay + "Secs");
                    }
                }
                break;

                case REQUEST_CODE_DOWNLOAD_RETRY: {
                    Log.d(TAG, "Retrying download");
                    downloadProfile(msg.arg2); //arg2 -> primary SubId
                }
                break;
            }
        }
    }

    /**
     * Finds the eSIM profile of the given CBRS carrier. Returns null if eSIM profile is not found.
     *
     * @param primaryCBRSSubInfo SubscriptionInfo of a CBRS Carrier of which corresponding eSIM info
     *                           is required.
     * @return
     */
    public void downloadOpportunisticESIM(SubscriptionInfo primaryCBRSSubInfo) {
        //Delete old eSIM (if exists) from the same operator as current pSIM.
        /*mONSProfileConfig.deleteOldOpportunisticESimsOfPSIMOperator(
                primaryCBRSSubInfo.getSubscriptionId());*/
        downloadProfile(primaryCBRSSubInfo.getSubscriptionId());
        return;
    }

    private void downloadProfile(int primarySubId) {
        Log.d(TAG, "downloadProfile");
        if (!mONSProfileConfig.isInternetConnectionAvailable()) {
            Log.d(TAG, "No internet connection. Download will be attempted when "
                    + "connection is restored");
            mONSProfileConfig.setRetryDownloadWhenConnectedFlag(true);
            return;
        }

        //Get SMDP address from carrier configuration
        String smdpAddr = mONSProfileConfig.getSMDPServerAddress(primarySubId);
        if (smdpAddr == null || smdpAddr.length() <= 0) {
            return;
        }

        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
        intent.putExtra(PARAM_REQUEST_TYPE, REQUEST_CODE_DOWNLOAD_SUB);
        intent.putExtra(PARAM_PRIMARY_SUBID, primarySubId);
        PendingIntent callbackIntent = PendingIntent.getBroadcast(mContext,
                REQUEST_CODE_DOWNLOAD_SUB, intent, PendingIntent.FLAG_IMMUTABLE);

        Log.d(TAG, "Download Request sent to EUICC Manager");
        mONSProfileConfig.getEuiccManager().downloadSubscription(
                DownloadableSubscription.forActivationCode(smdpAddr),
                true, callbackIntent);
    }

    /**
     * Callback to receive result for subscription activate request and process the same.
     * @param intent
     * @param resultCode
     */
    public static void onCallbackIntentReceived(Intent intent, int resultCode) {
        Message msg = new Message();
        msg.what = REQUEST_CODE_DOWNLOAD_SUB;
        msg.arg1 = resultCode;
        msg.arg2 = intent.getIntExtra(PARAM_PRIMARY_SUBID, 0);
        sHandler.sendMessage(msg);
    }
}
