/*
 *     Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *     WSO2 Inc. licenses this file to you under the Apache License,
 *     Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing,
 *    software distributed under the License is distributed on an
 *    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *    KIND, either express or implied.  See the License for the
 *    specific language governing permissions and limitations
 *    under the License.
 */

package org.wso2.carbon.esb.rabbitmq.securevault;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.base.CarbonBaseUtils;
import org.wso2.carbon.esb.rabbitmq.utils.RabbitMQServerInstance;
import org.wso2.carbon.esb.rabbitmq.utils.RabbitMQTestUtils;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.rabbitmqclient.RabbitMQConsumerClient;
import org.wso2.esb.integration.common.utils.clients.rabbitmqclient.RabbitMQProducerClient;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The purpose of this test case is to verify functionality of RabbitMQ sender and receiver with secured password
 * (in axis2.xml configuration) using cipher tool
 */
public class TestRabbitMQSecureVaultSupport extends ESBIntegrationTest {

    private ServerConfigurationManager serverConfigurationManager;

    private static final String TOML_FILE = "deployment.toml";
    private static final String PASSWD_FILE = "password-tmp";
    private static final String SYNAPSE_CONFIG_FILE = "RabbitMQSecureVaultProxy.xml";

    private static final String RABBITMQ_EXCHANGE = "exchangeSecureVault";
    private static final String RABBITMQ_RX_QUEUE = "queueSecureVault";
    private static final String RABBITMQ_RESULT_QUEUE = "resultQueueSecureVault";
    private static RabbitMQProducerClient sender;

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.init();
        serverConfigurationManager = new ServerConfigurationManager(context);
        String secureVaultConfDir =
                FrameworkPathUtil.getSystemResourceLocation() + "artifacts" + File.separator + "ESB" + File.separator
                        + "securevault" + File.separator;
        String confDir =
                CarbonBaseUtils.getCarbonHome() + File.separator + "conf" + File.separator;

        //copy deployment.toml
        String srcToml = secureVaultConfDir + TOML_FILE;
        String targetToml = confDir + TOML_FILE;

        File srcTomlFile = new File(srcToml);
        File targetTomlFile = new File(targetToml);
        serverConfigurationManager.applyConfigurationWithoutRestart(srcTomlFile, targetTomlFile, true);

        //copy password-tmp
        String srcPasswordTemp = secureVaultConfDir + PASSWD_FILE;
        String targetPasswordTemp = CarbonBaseUtils.getCarbonHome() + File.separator + PASSWD_FILE;

        File srcPasswordTempFile = new File(srcPasswordTemp);
        File targetPasswordTempFile = new File(targetPasswordTemp);
        serverConfigurationManager.applyConfigurationWithoutRestart(srcPasswordTempFile, targetPasswordTempFile, false);

        //start server with new configs
        serverConfigurationManager.restartGracefully();
        super.init();

        sender = RabbitMQServerInstance.createProducerWithDeclaration(RABBITMQ_EXCHANGE, RABBITMQ_RX_QUEUE);
        //The consumer proxy cannot be pre-deployed since the queue declaration(which is done in 'initRabbitMQBroker')
        // must happen before deployment.
        loadESBConfigurationFromClasspath(
                "artifacts" + File.separator + "ESB" + File.separator + "securevault" + File.separator
                        + SYNAPSE_CONFIG_FILE);
    }

    /**
     * This test will test both sender and receiver functionality (with password secured with secure vault):
     * Send message to queueRx in rabbitMQ, which ESB proxy (RabbitMQProxy.xml) listens to it.
     * Then the ESB proxy will put the received message (from exchangeRx) to queueResult in same RabbitMQ
     */
    @Test(groups = "wso2.esb", description = "Test RabbitMQ listener and sender by securing password parameters secured using secure vault")
    public void testRabbitMQWithSecureVault() throws InterruptedException, IOException {

        //Put message to rabbitMQ receiver queue (queueRx)
        String message = "<ser:placeOrder xmlns:ser=\"http://services.samples\">\n" + "<ser:order>\n"
                + "<ser:price>100</ser:price>\n" + "<ser:quantity>2000</ser:quantity>\n"
                + "<ser:symbol>RMQ</ser:symbol>\n" + "</ser:order>\n" + "</ser:placeOrder>";
        sender.sendMessage(message, "text/plain");

        RabbitMQTestUtils.waitForMssagesToGetPublished();

        //Read from result queue (queueResult)
        RabbitMQConsumerClient consumer = RabbitMQServerInstance
                .createConsumerWithDeclaration(RABBITMQ_EXCHANGE, RABBITMQ_RESULT_QUEUE);

        List<String> messages = null;
        try {
            messages = consumer.popAllMessages();
        } catch (IOException e) {
            Assert.fail("Error occurred while retrieving messages from the queue");
        }

        Assert.assertNotNull(messages, "No messages retrieved from the queue : " + RABBITMQ_RESULT_QUEUE);

        Assert.assertEquals(messages.size(), 1,
                "Message put to " + RABBITMQ_RX_QUEUE + " queue, was not published to " + RABBITMQ_RESULT_QUEUE
                        + " by the proxy");

        Assert.assertEquals(messages.get(0), message,
                "Popped message from " + RABBITMQ_RESULT_QUEUE + " differs from the original message");

    }

    @AfterClass(alwaysRun = true)
    public void cleanup() throws Exception {
        try {
            super.cleanup();
        } finally {
            serverConfigurationManager.restoreToLastConfiguration();
        }

    }
}
