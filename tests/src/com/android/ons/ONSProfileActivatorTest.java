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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class ONSProfileActivatorTest extends ONSBaseTest {
    private static final String TAG = ONSProfileActivatorTest.class.getName();

    @Mock
    Context mMockContext;
    @Mock
    SubscriptionManager mMockSubManager;
    @Mock
    ONSProfileConfigurator mMockONSProfileConfigurator;
    @Mock
    SubscriptionInfo mMockSubInfo;
    @Mock
    List<SubscriptionInfo> mMockactiveSubInfos;
    @Mock
    SubscriptionInfo mMockPrimaryCBRSSubInfo;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testONSAutoProvisioningDisabled() {
        doReturn(false).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_AUTO_PROVISIONING_DISABLED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithESIMNotSupported() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(false).when(mMockONSProfileConfigurator).isESIMSupported();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_ESIM_NOT_SUPPORTED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithESIMSupportedAndMultiSIMNotSupported() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(false).when(mMockONSProfileConfigurator).isMultiSIMPhone();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_MULTISIM_NOT_SUPPORTED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithDeviceSwitchToDualSIMModeFailed() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator)
                .isOpportunisticDataAutoProvisioningSupported(mMockSubInfo);
        doReturn(true).when(mMockONSProfileConfigurator).isDeviceInSingleSIMMode();
        doReturn(false).when(mMockONSProfileConfigurator).switchToMultiSIMMode();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_CANNOT_SWITCH_TO_DUAL_SIM_MODE,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithDeviceSwitchToDualSIMModeSuccess() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockSubInfo).isOpportunistic();
        doReturn(true).when(mMockONSProfileConfigurator)
                .isOpportunisticDataAutoProvisioningSupported(mMockSubInfo);
        doReturn(true).when(mMockONSProfileConfigurator).isDeviceInSingleSIMMode();
        doReturn(true).when(mMockONSProfileConfigurator).switchToMultiSIMMode();

        ONSProfileActivator mONSProfileActivator =
                new ONSProfileActivator(mMockContext, mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_SWITCHED_TO_DUAL_SIM_MODE,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithMultiSIMSupportedAndTwoActiveSubscriptions() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(2).when(mMockactiveSubInfos).size();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_DUAL_ACTIVE_SUBSCRIPTIONS,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithMultiSIMSupportedAndNoActiveSubscriptions() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(0).when(mMockactiveSubInfos).size();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_NO_SIM_INSERTED,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithOnlyOpportunisticSIMInserted() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(true).when(mMockPrimaryCBRSSubInfo).isOpportunistic();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_SINGLE_ACTIVE_OPPORTUNISTIC_SIM,
                mONSProfileActivator.handleSimStateChange());
    }

    @Test
    public void testONSProfileActivatorWithOnlyCBRSSupportedCarrierPSIMInserted() {
        doReturn(true).when(mMockONSProfileConfigurator).isONSAutoProvisioningEnabled();
        doReturn(true).when(mMockONSProfileConfigurator).isESIMSupported();
        doReturn(true).when(mMockONSProfileConfigurator).isMultiSIMPhone();
        doReturn(false).when(mMockONSProfileConfigurator)
                .isOpportunisticDataAutoProvisioningSupported(mMockPrimaryCBRSSubInfo);
        doReturn(mMockactiveSubInfos).when(mMockSubManager).getActiveSubscriptionInfoList();
        doReturn(1).when(mMockactiveSubInfos).size();
        doReturn(mMockPrimaryCBRSSubInfo).when(mMockactiveSubInfos).get(0);
        doReturn(false).when(mMockPrimaryCBRSSubInfo).isOpportunistic();

        ONSProfileActivator mONSProfileActivator = new ONSProfileActivator(mMockContext,
                mMockSubManager, mMockONSProfileConfigurator);

        assertEquals(ONSProfileActivator.Result.ERR_CARRIER_DOESNT_SUPPORT_CBRS,
                mONSProfileActivator.handleSimStateChange());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
