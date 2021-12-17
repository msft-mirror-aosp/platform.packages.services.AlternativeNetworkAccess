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

import android.annotation.TestApi;
import android.content.Context;
import android.os.ParcelUuid;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import java.util.List;

/**
 * @class ONSProfileActivator
 * @brief ONSProfileActivator makes sure that the CBRS profile is downloaded, activated and grouped
 * when an opportunistic data enabled pSIM is inserted.
 */
public class ONSProfileActivator implements ONSProfileConfigurator.ONSProfConfigListener {
    private static final String TAG = ONSProfileActivator.class.getName();
    private final Context mContext;
    private final ONSProfileConfigurator mONSProfileConfig;
    private final ONSProfileDownloader mONSProfileDownloader;

    public ONSProfileActivator(Context context) {
        mContext = context;

        mONSProfileConfig = new ONSProfileConfigurator(mContext, this);
        mONSProfileDownloader = new ONSProfileDownloader(mContext, mONSProfileConfig,
                primarySubId -> onDownloadComplete(primarySubId));
    }

    /**
     * This constructor is only for JUnit testing
     */
    @TestApi
    ONSProfileActivator(Context mockContext, ONSProfileConfigurator mockONSProfileConfigurator,
                        ONSProfileDownloader mockONSProfileDownloader) {
        mContext = mockContext;
        mONSProfileConfig = mockONSProfileConfigurator;
        mONSProfileDownloader = mockONSProfileDownloader;
    }

    /**
     * Called when SIM state changes. Triggers CBRS Auto provisioning.
     */
    public Result handleSimStateChange() {
        Result res = provisionCBRS();
        Log.d(TAG, res.toString());
        return res;
    }

    @Override
    public void onConnectionChanged(boolean bConnected) {
        if (bConnected && mONSProfileConfig != null
                && mONSProfileConfig.getRetryDownloadWhenConnectedFlag()) {
            Log.d(TAG, "Internet connection restored. Retrying CBRS auto provisioning");
            Result res = provisionCBRS();
            Log.d(TAG, res.toString());
        }
    }

    @Override
    public void onOppSubscriptionDeleted(int pSIMId) {
        provisionCBRS();
    }

    /**
     * Checks if AutoProvisioning is enabled, MultiSIM and eSIM support, cbrs pSIM is inserted and
     * makes sure device is in muti-SIM mode before triggering download of opportunistic eSIM.
     * Once downloaded, groups with pSIM, sets opportunistic and activates.
     */
    private Result provisionCBRS() {

        if (!mONSProfileConfig.isONSAutoProvisioningEnabled()) {
            return Result.ERR_AUTO_PROVISIONING_DISABLED;
        }

        //Check if device supports eSIM
        if (!mONSProfileConfig.isESIMSupported()) {
            return Result.ERR_ESIM_NOT_SUPPORTED;
        }

        //Check if it's a multi SIM Phone. CBRS is not supported on Single SIM phone.
        if (!mONSProfileConfig.isMultiSIMPhone()) {
            return Result.ERR_MULTISIM_NOT_SUPPORTED;
        }

        //Check the number of active subscriptions.
        SubscriptionManager subManager = mONSProfileConfig.getSubscriptionManager();
        List<SubscriptionInfo> activeSubInfos = subManager.getActiveSubscriptionInfoList();
        int activeSubCount = activeSubInfos.size();
        Log.d(TAG, "Active subscription count:" + activeSubCount);

        if (activeSubCount <= 0) {
            return Result.ERR_NO_SIM_INSERTED;
        } else if (activeSubCount == 1) {
            SubscriptionInfo pSubInfo = activeSubInfos.get(0);
            if (pSubInfo.isOpportunistic()) {
                //Only one SIM is active and its opportunistic SIM.
                //Opportunistic eSIM shouldn't be used without pSIM.
                return Result.ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM;
            }

            //if pSIM is not a CBRS carrier
            if (!mONSProfileConfig.isOppDataAutoProvisioningSupported(
                    pSubInfo.getSubscriptionId())) {
                return Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS;
            }

            if (mONSProfileConfig.isDeviceInSingleSIMMode()) {
                if (!mONSProfileConfig.switchToMultiSIMMode()) {
                    return Result.ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE;
                }

                //Once device is Switched to Dual-SIM Mode, handleSimStateChange is triggered.
                return Result.ERR_SWITCHED_TO_DUAL_SIM_MODE;
            }

            return downloadAndActivateOpportunisticSubscription(pSubInfo);
        } else if (activeSubCount >= 2) {
            //If all the SIMs are physical SIM then it's a sure case of DUAL Active Subscription.
            boolean allPhysicalSIMs = true;
            for (SubscriptionInfo subInfo : activeSubInfos) {
                if (subInfo.isEmbedded()) {
                    allPhysicalSIMs = false;
                    break;
                }
            }

            if (allPhysicalSIMs) {
                return Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS;
            }

            //Check if one of the subscription is opportunistic but not marked.
            //if one of the SIM is opportunistic and not grouped then group the subscription.
            for (SubscriptionInfo subInfo : activeSubInfos) {
                int pSubId = subInfo.getSubscriptionId();
                if (!subInfo.isEmbedded() && mONSProfileConfig
                        .isOppDataAutoProvisioningSupported(pSubId)) {

                    Log.d(TAG, "CBRS pSIM found. SubId:" + pSubId);

                    //Check if other SIM is opportunistic based on carrier-id.
                    SubscriptionInfo oppSubInfo = mONSProfileConfig
                            .findOpportunisticSubscription(pSubId);

                    //If opportunistic eSIM is found and activated.
                    if (oppSubInfo != null) {
                        if (subManager.isActiveSubscriptionId(oppSubInfo.getSubscriptionId())
                                && oppSubInfo.isOpportunistic()) {
                            //Already configured. No action required.
                            return Result.SUCCESS;
                        }

                        ParcelUuid pSIMGroupId = mONSProfileConfig.getPSIMGroupId(subInfo);
                        mONSProfileConfig.groupWithPSIMAndSetOpportunistic(oppSubInfo, pSIMGroupId);
                        return Result.SUCCESS;
                    }
                }
            }

            return Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS;
        }

        return Result.ERR_UNKNOWN;
    }

    private Result downloadAndActivateOpportunisticSubscription(
            SubscriptionInfo primaryCBRSSubInfo) {
        Log.d(TAG, "downloadAndActivateOpportunisticSubscription");

        //Check if pSIM is part of a group. If not then create a group.
        ParcelUuid pSIMgroupId = mONSProfileConfig.getPSIMGroupId(primaryCBRSSubInfo);

        //Check if opp eSIM is already downloaded but not grouped.
        SubscriptionInfo oppSubInfo = mONSProfileConfig.findOpportunisticSubscription(
                primaryCBRSSubInfo.getSubscriptionId());
        if (oppSubInfo != null) {
            mONSProfileConfig.groupWithPSIMAndSetOpportunistic(oppSubInfo, pSIMgroupId);
            return Result.SUCCESS;
        }

        //Opportunistic subscription not found. Trigger Download.
        mONSProfileDownloader.downloadOpportunisticESIM(primaryCBRSSubInfo);
        return Result.SUCCESS;
    }

    private void onDownloadComplete(int primarySubId) {
        mONSProfileConfig.setRetryDownloadWhenConnectedFlag(false);
        SubscriptionInfo opportunisticESIM = mONSProfileConfig
                .findOpportunisticSubscription(primarySubId);
        if (opportunisticESIM == null) {
            Log.e(TAG, "Downloaded Opportunistic eSIM not found. Unable to group with pSIM");
            return;
        }

        SubscriptionManager subManager = mONSProfileConfig.getSubscriptionManager();
        SubscriptionInfo pSIMSubInfo = subManager.getActiveSubscriptionInfo(primarySubId);
        if (pSIMSubInfo != null) {
            mONSProfileConfig.groupWithPSIMAndSetOpportunistic(
                    opportunisticESIM, pSIMSubInfo.getGroupUuid());
            Log.d(TAG, "eSIM downloaded and configured successfully");
        } else {
            Log.d(TAG, "ESIM downloaded but pSIM is not active or removed");
        }
    }

    public enum Result {
        SUCCESS,
        ERR_AUTO_PROVISIONING_DISABLED,
        ERR_ESIM_NOT_SUPPORTED,
        ERR_MULTISIM_NOT_SUPPORTED,
        ERR_CARRIER_DOESNT_SUPPORT_CBRS,
        ERR_DUAL_ACTIVE_SUBSCRIPTIONS,//Both the slots have primary SIMs
        ERR_NO_SIM_INSERTED,
        ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM,
        ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE,
        ERR_SWITCHED_TO_DUAL_SIM_MODE,
        ERR_UNKNOWN;
    }
}
