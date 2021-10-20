package com.android.ons;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;

/**
 * @class ONSProfileConfigurator
 * @brief Helper class to support ONSProfileActivator to read and update profile, operator and CBRS
 * configurations.
 */
public class ONSProfileConfigurator {

    private final Context mContext;
    private EuiccManager mEuiccManager = null;
    private TelephonyManager mTelephonyManager = null;

    public ONSProfileConfigurator(Context context) {
        mContext = context;
    }

    /**
     * Checks if device supports eSIM.
     */
    public boolean isESIMSupported() {
        if (mEuiccManager == null) {
            mEuiccManager = mContext.getSystemService(EuiccManager.class);
        }

        return (mEuiccManager != null && mEuiccManager.isEnabled());
    }

    /**
     * Check if device support multiple active SIMs
     */
    public boolean isMultiSIMPhone() {
        if (mTelephonyManager == null) {
            mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        }

        return (mTelephonyManager.getActiveModemCount() >= 2);
    }

    /**
     * Check if the given subscription is a CBRS supported carrier.
     */
    public boolean isPSIMforCBRSCarrier(SubscriptionInfo subInfo) {
        String mcc = subInfo.getMccString();
        String mnc = subInfo.getMncString();

        //TODO: fetch MNC/MCC from the carrier config
        //TODO: Check if subInfo is CBRS carrier
        return true;//temp
    }
}
