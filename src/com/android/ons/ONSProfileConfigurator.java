package com.android.ons;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
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
    private CarrierConfigManager mCarrierConfigMgr = null;

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
    public boolean isOpportunisticDataAutoProvisioningSupported(SubscriptionInfo subInfo) {
        if (mCarrierConfigMgr == null) {
            mCarrierConfigMgr = mContext.getSystemService(CarrierConfigManager.class);
        }

        PersistableBundle config = mCarrierConfigMgr.getConfigForSubId(subInfo.getSubscriptionId());
        Boolean oppDataAutoProvSupported = (Boolean) config.get(
                CarrierConfigManager.KEY_CARRIER_SUPPORTS_OPP_DATA_AUTO_PROVISIONING_BOOL);

        if (!oppDataAutoProvSupported || !oppDataAutoProvSupported) {
            return false;
        }

        return true;
    }
}
