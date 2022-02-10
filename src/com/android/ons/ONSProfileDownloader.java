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
import android.util.Pair;

import java.util.Random;
import java.util.Stack;

public class ONSProfileDownloader {

    interface IONSProfileDownloaderListener {
        void onDownloadComplete(int primarySubId);
    }

    private static final String TAG = ONSProfileDownloader.class.getName();

    private static final String PARAM_PRIMARY_SUBID = "PrimarySubscriptionID";
    private static final String PARAM_REQUEST_TYPE = "REQUEST";
    private static final int REQUEST_CODE_DOWNLOAD_SUB = 1;
    private static final int REQUEST_CODE_DOWNLOAD_RETRY = 2;
    private static IONSProfileDownloaderListener sListener;
    private static Handler sHandler;

    private final Context mContext;
    private final ONSProfileConfigurator mONSProfileConfig;

    private enum DownloadRetryOperationCode{
        DOWNLOAD_SUCCESSFUL,
        STOP_RETRY_UNTIL_SIM_STATE_CHANGE,
        DELETE_INACTIVE_OPP_ESIM_IF_EXISTS,
        DELETE_EXISTING_PROFILE_AND_RETRY,
        RETRY_AFTER_BACKOFF_TIME,
    };

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
                case REQUEST_CODE_DOWNLOAD_SUB: { //arg1 -> ResultCode
                    Log.d(TAG, "REQUEST_CODE_DOWNLOAD_SUB callback received");
                    int pSIMSubId = ((Intent) msg.obj).getIntExtra(PARAM_PRIMARY_SUBID, 0);
                    int detailedErrCode = ((Intent) msg.obj).getIntExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0);
                    int operationCode = ((Intent) msg.obj).getIntExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE, 0);
                    int errorCode = ((Intent) msg.obj).getIntExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE, 0);

                    Log.d(TAG, "Result Code : " + detailedErrCode);
                    Log.d(TAG, "Operation Code : " + operationCode);
                    Log.d(TAG, "Error Code : " + errorCode);

                    DownloadRetryOperationCode opCode = getOperationCode(msg.arg1, detailedErrCode,
                            operationCode, errorCode);
                    Log.d(TAG, "DownloadRetryOperationCode: " + opCode);

                    switch (opCode) {
                        case DOWNLOAD_SUCCESSFUL:
                            ONSProfileDownloader.sListener.onDownloadComplete(pSIMSubId);
                            break;

                        case STOP_RETRY_UNTIL_SIM_STATE_CHANGE:
                            mONSProfileConfig.setRetryDownloadWhenConnectedFlag(false);
                            Log.e(TAG, "Download ERR: unresolvable error occurred. Detailed"
                                    + "Error Code:" + detailedErrCode);
                            break;

                        case DELETE_INACTIVE_OPP_ESIM_IF_EXISTS:
                            if (!mONSProfileConfig.deleteOpportunisticSubscriptions(pSIMSubId)) {
                                Log.e(TAG, "Unable to free eUICC memory. Stop retry.");
                            } else {
                                retryDownloadAfterBackoffTime(pSIMSubId);
                            }
                            break;

                        case DELETE_EXISTING_PROFILE_AND_RETRY:
                            if (!mONSProfileConfig
                                    .deleteOldOpportunisticESimsOfPSIMOperator(pSIMSubId)) {
                                Log.e(TAG, "Unable to delete existing profile. Stop retry.");
                            } else {
                                retryDownloadAfterBackoffTime(pSIMSubId);
                            }
                            break;

                        case RETRY_AFTER_BACKOFF_TIME:
                            retryDownloadAfterBackoffTime(pSIMSubId);
                            break;
                    }
                }
                break;

                case REQUEST_CODE_DOWNLOAD_RETRY: {
                    Log.d(TAG, "Retrying download");
                    downloadProfile(msg.arg2); //arg1 -> primary SubId
                }
                break;
            }
        }

        private DownloadRetryOperationCode getOperationCode(int resultCode, int detailedErrCode,
                                                            int operationCode, int errorCode) {

            if (operationCode == EuiccManager.OPERATION_DOWNLOAD) {

                //Success Cases
                if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                    return DownloadRetryOperationCode.DOWNLOAD_SUCCESSFUL;
                }

                //Low eUICC memory cases
                if (errorCode == EuiccManager.ERROR_EUICC_INSUFFICIENT_MEMORY) {
                    Log.d(TAG, "Download ERR: EUICC_INSUFFICIENT_MEMORY");
                    return DownloadRetryOperationCode.DELETE_INACTIVE_OPP_ESIM_IF_EXISTS;
                }

                //Temporary download error cases
                if (errorCode == EuiccManager.ERROR_TIME_OUT
                        || errorCode == EuiccManager.ERROR_CONNECTION_ERROR
                        || errorCode == EuiccManager.ERROR_OPERATION_BUSY) {
                    return DownloadRetryOperationCode.RETRY_AFTER_BACKOFF_TIME;
                }

                //Profile installation failure cases
                if (errorCode == EuiccManager.ERROR_INSTALL_PROFILE) {
                    return DownloadRetryOperationCode.DELETE_EXISTING_PROFILE_AND_RETRY;
                }

                //UnResolvable error cases
                return DownloadRetryOperationCode.STOP_RETRY_UNTIL_SIM_STATE_CHANGE;

            } else if (operationCode == EuiccManager.OPERATION_SMDX_SUBJECT_REASON_CODE) {
                //SMDP Error codes handling
                Pair<String, String> errCode = decodeSmdxSubjectAndReasonCode(detailedErrCode);

                //8.1 - eUICC, 4.8 - Insufficient Memory
                // eUICC does not have sufficient space for this Profile.
                if (errCode.equals(Pair.create("8.1", "4.8"))) {
                    return DownloadRetryOperationCode.DELETE_INACTIVE_OPP_ESIM_IF_EXISTS;
                }

                //8.8.5 - Download order, 4.10 - Time to Live Expired
                //The Download order has expired
                if (errCode.equals(Pair.create("8.8.5", "4.10"))) {
                    return DownloadRetryOperationCode.RETRY_AFTER_BACKOFF_TIME;
                }

                //All other errors are unresolvable or retry after SIM State Change
                return DownloadRetryOperationCode.STOP_RETRY_UNTIL_SIM_STATE_CHANGE;

            } else {
                //Ignore if Operation code is not DOWNLOAD or SMDX_SUBJECT_REASON_CODE.
                //Callback is registered only for download requests.
                return DownloadRetryOperationCode.STOP_RETRY_UNTIL_SIM_STATE_CHANGE;
            }
        }

        private void retryDownloadAfterBackoffTime(int pSIMSubId) {
            //retry logic
            mDownloadRetryCount++;
            Log.e(TAG, "Download retry count :" + mDownloadRetryCount);
            if (mDownloadRetryCount >= mONSProfileConfig
                    .getDownloadRetryMaxAttemptsVal(pSIMSubId)) {
                Log.e(TAG, "Max download retry attempted. Stopping retry");
                return;
            }

            int backoffTimerVal = mONSProfileConfig.getDownloadRetryBackOffTimerVal(pSIMSubId);
            int delay = calculateBackoffDelay(mDownloadRetryCount, backoffTimerVal);

            Message retryMsg = new Message();
            retryMsg.what = REQUEST_CODE_DOWNLOAD_RETRY;
            retryMsg.arg2 = pSIMSubId;
            sendMessageDelayed(retryMsg, delay);

            Log.d(TAG, "Download failed. Retry after :" + delay + "MilliSecs");
        }

        private int calculateBackoffDelay(int retryCount, int backoffTimerVal) {
            /**
             * Timer value is calculated using "Exponential Backoff retry" algorithm.
             * When the first download failure occurs, retry download after
             * BACKOFF_TIMER_VALUE [Carrier Configurable] seconds.
             *
             * If download fails again then, retry after either BACKOFF_TIMER_VALUE,
             * 2xBACKOFF_TIMER_VALUE, or 3xBACKOFF_TIMER_VALUE seconds.
             *
             * In general after the cth failed attempt, retry after k *
             * BACKOFF_TIMER_VALUE seconds, where k is a random integer between 1 and
             * 2^c − 1. Max c value is KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT
             * [Carrier configurable]
             */
            //Calculate 2^c − 1
            int maxTime = (int) Math.pow(2, retryCount) - 1;

            //Random value between (1 & 2^c − 1) and convert to millisecond
            return ((mRandom.nextInt(maxTime) + 1)) * backoffTimerVal * 1000;
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
                REQUEST_CODE_DOWNLOAD_SUB, intent, PendingIntent.FLAG_MUTABLE);

        Log.d(TAG, "Download Request sent to EUICC Manager");
        mONSProfileConfig.getEuiccManager().downloadSubscription(
                DownloadableSubscription.forActivationCode(smdpAddr),
                true, callbackIntent);
    }

    /**
     * Given encoded error code described in
     * {@link android.telephony.euicc.EuiccManager#OPERATION_SMDX_SUBJECT_REASON_CODE} decode it
     * into SubjectCode[5.2.6.1] and ReasonCode[5.2.6.2] from GSMA (SGP.22 v2.2)
     *
     * @param resultCode from
     *               {@link android.telephony.euicc.EuiccManager#OPERATION_SMDX_SUBJECT_REASON_CODE}
     *
     * @return a pair containing SubjectCode[5.2.6.1] and ReasonCode[5.2.6.2] from GSMA (SGP.22
     * v2.2)
     */
    private Pair<String, String> decodeSmdxSubjectAndReasonCode(int resultCode) {
        final int numOfSections = 6;
        final int bitsPerSection = 4;
        final int sectionMask = 0xF;

        final Stack<Integer> sections = new Stack<>();

        // Extracting each section of digits backwards.
        for (int i = 0; i < numOfSections; ++i) {
            int sectionDigit = resultCode & sectionMask;
            sections.push(sectionDigit);
            resultCode = resultCode >>> bitsPerSection;
        }

        String subjectCode = sections.pop() + "." + sections.pop() + "." + sections.pop();
        String reasonCode = sections.pop() + "." + sections.pop() + "." + sections.pop();

        // drop the leading zeros, e.g. 0.1 -> 1, 0.0.3 -> 3, 0.5.1 -> 5.1
        subjectCode = subjectCode.replaceAll("^(0\\.)*", "");
        reasonCode = reasonCode.replaceAll("^(0\\.)*", "");

        return Pair.create(subjectCode, reasonCode);
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
        msg.obj = intent;
        sHandler.sendMessage(msg);
    }
}
