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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import java.util.List;

/**
 * @class ONSProfileActivator
 * @brief ONSProfileActivator makes sure that the CBRS profile is downloaded, activated and grouped
 * when an opportunistic data enabled pSIM is inserted.
 */
public class ONSProfileActivator {
    private static final String TAG = ONSProfileActivator.class.getName();
    private final Context mContext;
    private final SubscriptionManager mSubManager;
    private final ONSProfileConfigurator mONSProfileConfigurator;

    public ONSProfileActivator(Context context, SubscriptionManager subscriptionManager) {
        mContext = context;
        mSubManager = subscriptionManager;
        mONSProfileConfigurator = new ONSProfileConfigurator(mContext);
    }

    /**
     * This constructor is only for JUnit testing
     */
    //TODO: find an annotation which will include the below construction only in Debug builds.
    @TestApi
    ONSProfileActivator(Context context, SubscriptionManager subscriptionManager,
            ONSProfileConfigurator onsProfileConfigurator) {
        mContext = context;
        mSubManager = subscriptionManager;
        mONSProfileConfigurator = onsProfileConfigurator;
    }

    /**
     * Handles SIM state change event. Checks for MultiSIM and eSIM support, cbrs pSIM,
     * triggers download if eSIM is not available, activates eSIM after successful download.
     */
    public Result handleSimStateChange() {

        if (!mONSProfileConfigurator.isONSAutoProvisioningEnabled()) {
            return Result.ERR_AUTO_PROVISIONING_DISABLED;
        }

        //Check if device supports eSIM
        if (mONSProfileConfigurator.isESIMSupported() == false) {
            return Result.ERR_ESIM_NOT_SUPPORTED;
        }

        //Check if it's a multi SIM Phone. CBRS is not supported on Single SIM phone.
        if (mONSProfileConfigurator.isMultiSIMPhone() == false) {
            return Result.ERR_MULTISIM_NOT_SUPPORTED;
        }

        SubscriptionInfo primaryCBRSSubInfo = null;
        //Get the list of active subscriptions
        List<SubscriptionInfo> activeSubInfos = mSubManager.getActiveSubscriptionInfoList();
        int subCount = activeSubInfos.size();

        if (subCount >= 2) {
            return Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS;
        }
        else if(subCount <= 0) {
            return Result.ERR_NO_SIM_INSERTED;
        }
        else if(subCount == 1) {
            primaryCBRSSubInfo = activeSubInfos.get(0);
            if(primaryCBRSSubInfo.isOpportunistic()) {
                //Only one SIM is inserted and its opportunistic SIM. No action is required.
                return Result.ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM;
            }
        }

        //if pSIM is not a CBRS carrier
        if (!mONSProfileConfigurator.isOpportunisticDataAutoProvisioningSupported(
                primaryCBRSSubInfo)) {
            return Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS;
        }

        //cbrs eSIM is not active.
        //TODO: Check if cbrs eSIM is downloaded but disabled. (if yes then, activate).
        //TODO: else download cbrs eSIM (call download API of ONSProfileDownloader)

        return Result.SUCCESS;
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
        ERR_OPPORTUNISTIC_SIM_WITHOUT_PSIM_DISABLED,
        ERR_UNKNOWN;
    }
}
