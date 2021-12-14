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

import android.annotation.TestApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * @class ONSProfileConfigurator
 * @brief Helper class to support ONSProfileActivator to read and update profile, operator and CBRS
 * configurations.
 */
public class ONSProfileConfigurator {

    private static final String TAG = ONSProfileConfigurator.class.getName();
    private static final String PARAM_SUB_ID = "SUB_ID";
    private static final String PARAM_REQUEST_TYPE = "REQUEST_TYPE";
    private static final int REQUEST_CODE_ACTIVATE_SUB = 1;
    private static final int REQUEST_CODE_DELETE_SUB = 2;
    private static final String PREF_NAME = "ONSProvisioning";
    private static final String PREF_RETRY_DOWNLOAD_WHEN_CONNECTED = "RetryDownloadAfterReconnect";

    private final Context mContext;
    private SubscriptionManager mSubManager;
    private EuiccManager mEuiccManager;
    private TelephonyManager mTelephonyManager;
    private CarrierConfigManager mCarrierConfigMgr = null;
    private static Handler sDeleteSubscriptionCallbackHandler = null;
    private ONSProfConfigListener mONSProfConfigListener = null;
    private boolean mRetryDownloadWhenNWConnected = false;
    private boolean mIsInternetConnAvailable = false;

    public ONSProfileConfigurator(Context context, ONSProfConfigListener listener) {
        mContext = context;
        mONSProfConfigListener = listener;
        mSubManager = mContext.getSystemService(SubscriptionManager.class);
        mEuiccManager = mContext.getSystemService(EuiccManager.class);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mCarrierConfigMgr = mContext.getSystemService(CarrierConfigManager.class);

        //Monitor internet connection.
        final ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
        connMgr.registerNetworkCallback(request, new NetworkCallback());

        //Delete Subscription response handler.
        if (sDeleteSubscriptionCallbackHandler == null) {
            sDeleteSubscriptionCallbackHandler = new Handler(mContext.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == REQUEST_CODE_DELETE_SUB) {
                        if (mONSProfConfigListener != null) {
                            mONSProfConfigListener.onOppSubscriptionDeleted(msg.arg1);
                        }
                    }
                }
            };
        }
    }

    @TestApi
    ONSProfileConfigurator(Context mockContext, SubscriptionManager mockSubManager,
                           EuiccManager mockEuiccMangr, TelephonyManager mockTelephonyMangr) {
        mContext = mockContext;
        mSubManager = mockSubManager;
        mEuiccManager = mockEuiccMangr;
        mTelephonyManager = mockTelephonyMangr;
    }

    /**
     * Callback to receive result for subscription activate request and process the same.
     *
     * @param context
     * @param intent
     * @param resultCode
     */
    public static void onCallbackIntentReceived(Context context, Intent intent, int resultCode) {
        int reqCode = intent.getIntExtra(PARAM_REQUEST_TYPE, 0);
        switch (reqCode) {
            case REQUEST_CODE_ACTIVATE_SUB: {
                /*int subId = intent.getIntExtra(ACTIVATE_SUB_ID, 0);*/
                Log.d(TAG, "Opportunistic subscription activated successfully. SubId:"
                        + intent.getIntExtra(PARAM_SUB_ID, 0));
                Log.d(TAG, "Detailed result code: " + intent.getIntExtra(
                        EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0));
            }
            break;

            case REQUEST_CODE_DELETE_SUB: {
                if (resultCode != EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
                    Log.e(TAG, "Error removing euicc opportunistic profile."
                            + "Detailed error code = " + intent.getIntExtra(
                                    EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0));
                } else {
                    Message msg = new Message();
                    msg.what = REQUEST_CODE_DELETE_SUB;
                    msg.arg1 = intent.getIntExtra(PARAM_SUB_ID, 0);
                    sDeleteSubscriptionCallbackHandler.sendMessage(msg);
                    Log.d(TAG, "Opportunistic subscription deleted successfully. Id:" + msg.arg1);
                }
            }
            break;
        }
    }

    /**
     * Returns instance of Subscription Manager system service.
     */
    public SubscriptionManager getSubscriptionManager() {
        return mSubManager;
    }

    /**
     * Returns instance of EUICC Manager system service.
     */
    public EuiccManager getEuiccManager() {
        return mEuiccManager;
    }

    /**
     * Returns instance of Telephony Manager system service.
     */
    public TelephonyManager getTelephonyManager() {
        return mTelephonyManager;
    }

    /**
     * Returns instance of carrier configuration Manager system service.
     */
    public CarrierConfigManager getCarrierConfigManager() {
        return mCarrierConfigMgr;
    }

    /**
     * Checks if device supports eSIM.
     */
    public boolean isESIMSupported() {
        return (mEuiccManager != null && mEuiccManager.isEnabled());
    }

    /**
     * Check if device support multiple active SIMs
     */
    public boolean isMultiSIMPhone() {
        return (mTelephonyManager.getSupportedModemCount() >= 2);
    }

    /**
     * Fetches ONS auto provisioning enable flag from device configuration.
     * ONS auto provisioning feature executes only when the flag is set to true in device
     * configuration.
     */
    public boolean isONSAutoProvisioningEnabled() {
        return mContext.getResources().getBoolean(R.bool.enable_ons_auto_provisioning);
    }

    /**
     * Check if the given subscription is a CBRS supported carrier.
     */
    public boolean isOppDataAutoProvisioningSupported(int pSIMSubId) {
        PersistableBundle config = getCarrierConfigManager().getConfigForSubId(pSIMSubId);
        return config.getBoolean(
                CarrierConfigManager.KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL);
    }

    /**
     * Adds downloaded subscription to the group, activates and enables opportunistic data.
     *
     * @param opportunisticESIM
     * @param groupUuid
     */
    public void groupWithPSIMAndSetOpportunistic(
            SubscriptionInfo opportunisticESIM, ParcelUuid groupUuid) {
        Log.d(TAG, "Grouping opportunistc eSIM and CBRS pSIM");
        ArrayList<Integer> subList = new ArrayList<>();
        subList.add(opportunisticESIM.getSubscriptionId());
        mSubManager.addSubscriptionsIntoGroup(subList, groupUuid);
        if (!opportunisticESIM.isOpportunistic()) {
            Log.d(TAG, "set Opportunistic to TRUE");
            mSubManager.setOpportunistic(true, opportunisticESIM.getSubscriptionId());
        }
        //activateSubscription(opportunisticESIM);// -> activate after download flag is passed as
        //true in download request so no need of explicit activation.
    }

    /**
     * Activates provided subscription. Result is received in method onCallbackIntentReceived.
     */
    public void activateSubscription(int subId) {
        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileConfigurator.class.getName());
        intent.putExtra(PARAM_REQUEST_TYPE, REQUEST_CODE_ACTIVATE_SUB);
        intent.putExtra(PARAM_SUB_ID, subId);
        PendingIntent callbackIntent = PendingIntent.getBroadcast(mContext,
                REQUEST_CODE_ACTIVATE_SUB, intent, PendingIntent.FLAG_IMMUTABLE);
        Log.d(TAG, "Activate oppSub request sent to SubManager");
        mSubManager.switchToSubscription(subId, callbackIntent);
    }

    /**
     * Deletes inactive opportunistic subscriptions irrespective of the CBRS operator.
     * Called when sufficient memory is not available before downloading new profile.
     */
    public boolean deleteOpportunisticSubscriptions(int pSIMId) {
        Log.d(TAG, "deleteInactiveOpportunisticSubscriptions");

        //First delete old opportunistic eSIM from same operator.
        //If there are no such eSIM to delete then delete other
        //opportunistic eSIMs.
        if (deleteOldOpportunisticESimsOfPSIMOperator(pSIMId)) {
            return true;
        }

        List<SubscriptionInfo> subList = mSubManager.getOpportunisticSubscriptions();
        if (subList == null || subList.size() <= 0) {
            return false;
        }

        for (SubscriptionInfo subInfo : subList) {
            int subId = subInfo.getSubscriptionId();
            if (!mSubManager.isActiveSubscriptionId(subId)) {
                deleteSubscription(subId);
                return true;
            }
        }

        return false;
    }

    /**
     * Deletes previously downloaded opportunistic eSIM associated with pSIM CBRS operator.
     * Helpful to cleanup before downloading new opportunistic eSIM from the same CBRS operator.
     *
     * @return true - If an eSIM is delete request is sent.
     *          false - If no suitable eSIM is found for delete.
     */
    private boolean deleteOldOpportunisticESimsOfPSIMOperator(int pSIMSubId) {
        Log.d(TAG, "deleteOldOpportunisticESimsOfPSIMOperator");
        //1.Get the list of all opportunistic carrier-ids of newly inserted pSIM from carrier config
        PersistableBundle config = getCarrierConfigManager().getConfigForSubId(pSIMSubId);
        int[] oppCarrierIdArr = config.getIntArray(
                CarrierConfigManager.KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY);
        if (oppCarrierIdArr == null || oppCarrierIdArr.length <= 0) {
            return false;
        }

        //2. Get list of all subscriptions
        List<SubscriptionInfo> oppSubList = mSubManager.getAvailableSubscriptionInfoList();
        for (SubscriptionInfo subInfo : oppSubList) {
            for (int oppCarrierId : oppCarrierIdArr) {
                //Carrier-id of opportunistic eSIM matches with one of thecarrier-ids in carrier
                // config of pSIM
                if (subInfo.isEmbedded() && oppCarrierId == subInfo.getCarrierId()) {
                    //3.if carrier-id of eSIM matches with one of the pSIM opportunistic carrier-ids
                    //and eSIM's pSIM carrier-id matches with new pSIM then delete the subscription
                    deleteSubscription(subInfo.getSubscriptionId());
                    return true;
                }
            }
        }

        return false;
    }

    private void deleteSubscription(int subId) {
        Log.d(TAG, "deleting subscription. SubId: " + subId);
        Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
        intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileConfigurator.class.getName());
        intent.putExtra(PARAM_REQUEST_TYPE, REQUEST_CODE_DELETE_SUB);
        intent.putExtra(PARAM_SUB_ID, subId);
        PendingIntent callbackIntent = PendingIntent.getBroadcast(mContext,
                REQUEST_CODE_DELETE_SUB, intent, PendingIntent.FLAG_IMMUTABLE);
        mEuiccManager.deleteSubscription(subId, callbackIntent);
    }

    /**
     * Creates Subscription Group for PSIM if it doesn't exist or returns existing group-id.
     */
    public ParcelUuid getPSIMGroupId(SubscriptionInfo primaryCBRSSubInfo) {
        ParcelUuid groupId = primaryCBRSSubInfo.getGroupUuid();
        if (groupId != null) {
            return groupId;
        }

        Log.d(TAG, "Creating Group for Primary SIM");
        List<Integer> pSubList = new ArrayList<>();
        pSubList.add(primaryCBRSSubInfo.getSubscriptionId());
        return mSubManager.createSubscriptionGroup(pSubList);
    }

    /**
     * Searches for opportunistic profile in all available subscriptions using carrier-ids
     * from carrier configuration and returns opportunistic subscription.
     */
    public SubscriptionInfo findOpportunisticSubscription(int pSIMId) {
        Log.d(TAG, "findOpportunisticSubscription. PSIM Id : " + pSIMId);

        //Get the list of active subscriptions
        List<SubscriptionInfo> availSubInfoList = mSubManager.getAvailableSubscriptionInfoList();
        Log.d(TAG, "Available subscriptions: " + availSubInfoList.size());

        //Get the list of opportunistic carrier-ids list from carrier config.
        PersistableBundle config = mCarrierConfigMgr.getConfigForSubId(pSIMId);
        int[] oppCarrierIdArr = config.getIntArray(
                CarrierConfigManager.KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY);
        if (oppCarrierIdArr == null || oppCarrierIdArr.length <= 0) {
            Log.e(TAG, "Opportunistic carrier-ids list is empty in carrier config");
            return null;
        }

        ParcelUuid pSIMSubGroupId = mSubManager.getActiveSubscriptionInfo(pSIMId).getGroupUuid();
        for (SubscriptionInfo subInfo : availSubInfoList) {
            if (subInfo.getSubscriptionId() != pSIMId) {
                for (int carrId : oppCarrierIdArr) {
                    if (subInfo.isEmbedded() && carrId == subInfo.getCarrierId()) {
                        //An active eSIM whose carrier-id is listed as opportunistic carrier in
                        // carrier config is newly downloaded opportunistic eSIM.

                        ParcelUuid oppSubGroupId = subInfo.getGroupUuid();
                        if (oppSubGroupId == null || oppSubGroupId.equals(pSIMSubGroupId)) {
                            Log.d(TAG, "Opp subscription found:" + subInfo.getSubscriptionId());
                            return subInfo;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Retrieves SMDP+ server address of the given subscription from carrier configuration.
     *
     * @param subscriptionId subscription Id of the primary SIM.
     * @return FQDN of SMDP+ server.
     */
    public String getSMDPServerAddress(int subscriptionId) {
        PersistableBundle config = mCarrierConfigMgr.getConfigForSubId(subscriptionId);
        return config.getString(CarrierConfigManager.KEY_SMDP_SERVER_ADDRESS_STRING);
    }

    /**
     * Retrieves backoff timer value (in seconds) from carrier configuration. Value is used to
     * calculate delay before retrying profile download.
     *
     * @param subscriptionId subscription Id of the primary SIM.
     * @return Backoff timer value in seconds.
     */
    public int getDownloadRetryBackOffTimerVal(int subscriptionId) {
        PersistableBundle config = getCarrierConfigManager().getConfigForSubId(subscriptionId);
        return config.getInt(CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT);
    }

    /**
     * Retrieves maximum retry attempts from carrier configuration. After maximum attempts, further
     * attempts will not be made until next device reboot.
     *
     * @param subscriptionId subscription Id of the primary SIM.
     * @return
     */
    public int getDownloadRetryMaxAttemptsVal(int subscriptionId) {
        PersistableBundle config = getCarrierConfigManager().getConfigForSubId(subscriptionId);
        return config.getInt(CarrierConfigManager.KEY_ESIM_MAX_DOWNLOAD_RETRY_ATTEMPTS_INT);
    }

    /**
     * Checks if device is in single SIM mode.
     */
    public boolean isDeviceInSingleSIMMode() {
        return (mTelephonyManager.getActiveModemCount() <= 1);
    }

    /**
     * Switches device to multi SIM mode. Checks if reboot is required before switching and
     * configuration is triggered only if reboot not required.
     */
    public boolean switchToMultiSIMMode() {
        if (!mTelephonyManager.doesSwitchMultiSimConfigTriggerReboot()) {
            mTelephonyManager.switchMultiSimConfig(2);
            return true;
        }

        return false;
    }

    /**
     * Saves flag to retry download when internet connection is restored.
     *
     * @param enable       - true - retry download when connected.
     *                     false - No retry required.
     */
    public void setRetryDownloadWhenConnectedFlag(boolean enable) {
        mRetryDownloadWhenNWConnected = enable;
    }

    /**
     * Retrieves flag to retry download when internet connection is restored.
     */
    public boolean getRetryDownloadWhenConnectedFlag() {
        return mRetryDownloadWhenNWConnected;
    }

    public boolean isInternetConnectionAvailable() {
        return mIsInternetConnAvailable;
    }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.d(TAG, "Internet connection available");
            mIsInternetConnAvailable = true;
            if (mONSProfConfigListener != null) {
                mONSProfConfigListener.onConnectionChanged(true);
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.d(TAG, "Internet connection lost");
            mIsInternetConnAvailable = false;
            if (mONSProfConfigListener != null) {
                mONSProfConfigListener.onConnectionChanged(false);
            }
        }
    }

    /**
     * Listener interface to notify delete subscription operation and internet connection status
     * change.
     */
    public interface ONSProfConfigListener {

        /**
         * Change in connection is used to decide whether to send download request or differ.
         * When connection is available, previously pending download is resumed.
         */
        void onConnectionChanged(boolean bConnected);

        /**
         * Called when the delete subscription request is processed successfully.
         */
        void onOppSubscriptionDeleted(int pSIMId);
    }
}
