/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.management.apis;

import org.apache.axiom.om.OMElement;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.wso2.carbon.inbound.endpoint.internal.http.api.ConfigurationLoader;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.integrator.core.internal.MicroIntegratorBaseConstants;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.wso2.micro.integrator.management.apis.Constants.MGT_API_NAME;
import static org.wso2.micro.integrator.management.apis.Constants.NAME_ATTR;


@RunWith(PowerMockRunner.class)
@PrepareForTest(ConfigurationLoader.class)
public class ManagementApiParserTest {

    private void initializeConfDirectory() {
        String filePath = getClass().getResource("").getPath();
        System.setProperty(MicroIntegratorBaseConstants.CARBON_CONFIG_DIR_PATH, filePath);
    }

    @Test
    public void getManagementApiElement() throws CarbonException, XMLStreamException, ManagementApiUndefinedException, IOException {
        initializeConfDirectory();
        OMElement managementApiElement = ManagementApiParser.getManagementApiElement();
        Assert.assertNotNull(managementApiElement);
        Assert.assertEquals(MGT_API_NAME, managementApiElement.getAttributeValue(NAME_ATTR));
    }

    @Test
    public void testGetUserList() throws UserStoreUndefinedException {
        Map<String, char[]> userMap = new HashMap<>();
        userMap.put("user1", "pwd1".toCharArray());
        PowerMockito.mockStatic(ConfigurationLoader.class);
        Mockito.when(ConfigurationLoader.getUserMap()).thenReturn(userMap);
        ManagementApiParser managementApiParser = new ManagementApiParser();
        Map<String, char[]> retrievedUserMap = managementApiParser.getUserMap();
        Assert.assertNotNull(retrievedUserMap);
        Assert.assertEquals("pwd1", new String(retrievedUserMap.get("user1")));
    }

    @Test(expected = UserStoreUndefinedException.class)
    public void testGetNullUserStore() throws UserStoreUndefinedException {
        PowerMockito.mockStatic(ConfigurationLoader.class);
        Mockito.when(ConfigurationLoader.getUserMap()).thenReturn(null);
        ManagementApiParser managementApiParser = new ManagementApiParser();
        managementApiParser.getUserMap();
    }
}
