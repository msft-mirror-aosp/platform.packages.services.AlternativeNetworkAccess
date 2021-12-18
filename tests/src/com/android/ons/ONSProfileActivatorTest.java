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

import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class ONSProfileActivatorTest extends ONSBaseTest {
    private static final String TAG = ONSProfileActivatorTest.class.getName();

    @Mock
    Context mMockContext;
    @Mock
    SubscriptionManager mMockSubManager;
    @Mock
    EuiccManager mMockEuiCCManager;
    @Mock
    TelephonyManager mMockTeleManager;
    @Mock
    CarrierConfigManager mMockCarrierConfigManager;
    @Mock
    ONSProfileConfigurator mMockONSProfileConfigurator;
    @Mock
    ONSProfileDownloader mMockONSProfileDownloader;
    @Mock
    List<SubscriptionInfo> mMockactiveSubInfos;
    @Mock
    SubscriptionInfo mMockSubInfo;
    @Mock
    SubscriptionInfo mMockSubInfo2;
    @Mock
    List<SubscriptionInfo> mMocksubsInPSIMGroup;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);

        doReturn(mMockSubManager).when(mMockONSProfileConfigurator).getSubscriptionManager();
        doReturn(mMockEuiCCManager).when(mMockONSProfileConfigurator).getEuiccManager();
        doReturn(mMockTeleManager).when(mMockONSProfileConfigurator).getTelephonyManager();
        doReturn(mMockCarrierConfigManager).when(mMockONSProfileConfigurator)
                .getCarrierConfigManager();
    }

    @Test
    public void testONSAutoProvisioningDisabled() {
        doReturn(false).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_AUTO_PROVISIONING_DISABLED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Device doesn't support eSIM")
    public void testESIMNotSupported() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(false).when(mMockONSProfileConfigurator).isESIMSupported();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_ESIM_NOT_SUPPORTED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Single SIM Device with eSIM support")
    public void testMultiSIMNotSupported() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(false).when(mMockONSProfileConfigurator).isMultiSIMPhone();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_MULTISIM_NOT_SUPPORTED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testDeviceSwitchToDualSIMModeFailed() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(1).when(mMockSubInfo).getSubscriptionId();
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator)
                .isOppDataAutoProvisioningSupported(1);
        doReturn(true).when(mMockONSProfileConfigurator).isDeviceInSingleSIMMode();
        doReturn(false).when(mMockONSProfileConfigurator).switchToMultiSIMMode();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                        mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testDeviceSwitchToDualSIMModeSuccess() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(1).when(mMockSubInfo).getSubscriptionId();
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator)
                .isOppDataAutoProvisioningSupported(1);
        doReturn(true).when(mMockONSProfileConfigurator).isDeviceInSingleSIMMode();
        doReturn(true).when(mMockONSProfileConfigurator).switchToMultiSIMMode();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_SWITCHED_TO_DUAL_SIM_MODE,
                mONSProfileActivator.handleSimStateChange());
    }

    //@DisplayName("Dual SIM device with no SIM inserted")
    public void testNoActiveSubscriptions() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(0).when(mMockactiveSubInfos).size();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_NO_SIM_INSERTED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Dual SIM device and non CBRS carrier pSIM inserted")
    public void testNonCBRSCarrierPSIMInserted() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(false).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(1);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(1).when(mMockSubInfo).getSubscriptionId();
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(false).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(1);

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Dual SIM device with Two PSIM active subscriptions")
    public void testTwoActivePSIMSubscriptions() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        ArrayList<SubscriptionInfo> mActiveSubInfos = new ArrayList<>();
        mActiveSubInfos.add(mMockSubInfo);
        mActiveSubInfos.add(mMockSubInfo2);
        doReturn(mActiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(false).when(mMockSubInfo).isEmbedded();
        doReturn(false).when(mMockSubInfo2).isEmbedded();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS,
                mONSProfileActivator.handleSimStateChange());
    }

    /*@Test
    //Cannot mock/spy class android.os.PersistableBundle
    public void testOneActivePSIMAndOneNonOpportunisticESIM() {
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        ArrayList<SubscriptionInfo> mActiveSubInfos = new ArrayList<>();
        mActiveSubInfos.add(mMockSubInfo1);
        mActiveSubInfos.add(mMockSubInfo2);
        doReturn(mActiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(false).when(mMockSubInfo1).isEmbedded();
        doReturn(true).when(mMockSubInfo2).isEmbedded();
        //0 - using carrier-id=0 to make sure it doesn't map to any opportunistic carrier-id
        doReturn(0).when(mMockSubInfo2).getCarrierId();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS,
                mONSProfileActivator.handleSimStateChange());
    }*/

    /*@Test
    //Cannot mock/spy class android.os.PersistableBundle
    public void testOneActivePSIMAndOneOpportunisticESIM() {
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        ArrayList<SubscriptionInfo> mActiveSubInfos = new ArrayList<>();
        mActiveSubInfos.add(mMockSubInfo1);
        mActiveSubInfos.add(mMockSubInfo2);
        doReturn(mActiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(false).when(mMockSubInfo1).isEmbedded();
        doReturn(true).when(mMockSubInfo2).isEmbedded();
        doReturn(1).when(mMockSubInfo2).getSubscriptionId();
        doReturn(mMockCarrierConfig).when(mMockCarrierConfigManager).getConfigForSubId(1);
        doReturn(new int[]{1}).when(mMockCarrierConfig).get(
                CarrierConfigManager.KEY_OPPORTUNISTIC_CARRIER_IDS_INT_ARRAY);
        //1 - using carrier-id=1 to match with opportunistic carrier-id
        doReturn(1).when(mMockSubInfo2).getCarrierId();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                mONSProfileActivator.handleSimStateChange());
    }*/

    @Test
    //@DisplayName("Dual SIM device with only opportunistic eSIM active")
    public void testOnlyOpportunisticESIMActive() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(true).when(mMockSubInfo).isOpportunistic();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM not Grouped")
    public void testCBRSpSIMAndNotGrouped() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(1);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(1).when(mMockSubInfo).getSubscriptionId();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(1);
        doReturn(null).when(mMockSubInfo).getGroupUuid();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                mONSProfileActivator.handleSimStateChange());
    }

    /* Unable to mock final class ParcelUuid. These testcases should be enabled once the solution
    is found */
    /*@Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM Grouped")
    public void testOneSubscriptionInCBRSpSIMGroup() {
        ParcelUuid mMockParcelUuid = crateMock(ParcelUuid.class);

        doReturn(true).when(mMockONSProfileConfigurator)
                .isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
                mMockPrimaryCBRSSubInfo);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockPrimaryCBRSSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
        mMockPrimaryCBRSSubInfo);
        doReturn(mMockParcelUuid).when(mMockPrimaryCBRSSubInfo).getGroupUuid();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getSubscriptionsInGroup(
        mMockParcelUuid);
        doReturn(1).when(mMocksubsInPSIMGroup).size();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockEuiCCManager,
                mMockTeleManager,
                        mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM Group has two
    subscription info.")
    public void testTwoSubscriptionsInCBRSpSIMGroup() {
        ParcelUuid mMockParcelUuid = crateMock(ParcelUuid.class);

        doReturn(true).when(mMockONSProfileConfigurator)
                .isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
                mMockPrimaryCBRSSubInfo);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockPrimaryCBRSSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
        mMockPrimaryCBRSSubInfo);
        doReturn(mMockParcelUuid).when(mMockPrimaryCBRSSubInfo).getGroupUuid();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getSubscriptionsInGroup(
        mMockParcelUuid);
        doReturn(2).when(mMocksubsInPSIMGroup).size();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockEuiCCManager,
                mMockTeleManager, mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    //@DisplayName("Dual SIM device, only CBRS carrier pSIM inserted and pSIM Group has more than
    two subscription info.")
    public void testMoreThanTwoSubscriptionsInCBRSpSIMGroup() {
        ParcelUuid mMockParcelUuid = crateMock(ParcelUuid.class);

        doReturn(true).when(mMockONSProfileConfigurator)
                .isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
                mMockPrimaryCBRSSubInfo);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockPrimaryCBRSSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator).isOppDataAutoProvisioningSupported(
        mMockPrimaryCBRSSubInfo);
        doReturn(mMockParcelUuid).when(mMockPrimaryCBRSSubInfo).getGroupUuid();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getSubscriptionsInGroup(
        mMockParcelUuid);
        doReturn(3).when(mMocksubsInPSIMGroup).size();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockEuiCCManager,
                mMockTeleManager, mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.SUCCESS,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testRetryDownloadAfterRebootWithOppESIMAlreadyDownloaded() {
        doReturn(true).when(mMockONSProfileConfigurator).getRetryDownloadAfterReboot();
        doReturn(1).when(mMockONSProfileConfigurator).getRetryDownloadPSIMSubId();
        doReturn(mMockSubManager).when(mMockONSProfileConfigurator).getSubscriptionManager();
        doReturn(mMocksubsInPSIMGroup).when(mMockSubManager).getActiveSubscriptionInfo();
        //TODO: mock ParcelUuid - pSIM group

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockONSProfileConfigurator, mMockONSProfileDownloader);

        assertEquals(ONSProfileActivator.Result.ERR_INVALID_PSIM_SUBID,
                mONSProfileActivator.retryDownloadAfterReboot());
    }
    */

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}