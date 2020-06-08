/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.inbound.endpoint.protocol.rabbitmq;

import com.rabbitmq.client.AMQP;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.mediators.base.SequenceMediator;

import java.util.UUID;

/**
 * The received messages will be injected into the sequence for the mediation.
 */
public class RabbitMQInjectHandler {
    private static final Log log = LogFactory.getLog(RabbitMQInjectHandler.class);

    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;
    private SynapseEnvironment synapseEnvironment;
    private SequenceMediator seq;

    public RabbitMQInjectHandler(String injectingSeq, String onErrorSeq, boolean sequential,
                                 SynapseEnvironment synapseEnvironment) {
        this.injectingSeq = injectingSeq;
        if (injectingSeq == null || injectingSeq.equals("")) {
            String msg = "Injecting Sequence name is not specified.";
            log.error(msg);
            throw new SynapseException(msg);
        }
        seq = (SequenceMediator) synapseEnvironment.getSynapseConfiguration().getSequence(injectingSeq);
        if (seq == null) {
            throw new SynapseException("Specified injecting sequence: " + injectingSeq + "is invalid.");
        }
        if (!seq.isInitialized()) {
            seq.init(synapseEnvironment);
        }
        this.onErrorSeq = onErrorSeq;
        this.sequential = sequential;
        this.synapseEnvironment = synapseEnvironment;
    }

    /**
     * Determine the message builder to use, set the message payload to the message context and
     * inject the message.
     *
     * @param properties  the AMQP basic properties
     * @param body        the message body
     * @param inboundName Inbound Name
     * @return delivery status of the message
     */
    public boolean onMessage(AMQP.BasicProperties properties, byte[] body, String inboundName) {
        org.apache.synapse.MessageContext msgCtx = createMessageContext();
        try {
            MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) msgCtx)
                    .getAxis2MessageContext();
            RabbitMQUtils.buildMessage(properties, body, axis2MsgCtx);
            axis2MsgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, RabbitMQUtils.getTransportHeaders(properties));

            if (seq != null) {
                if (log.isDebugEnabled()) {
                    log.debug("injecting message to sequence : " + injectingSeq);
                }
                seq.setErrorHandler(onErrorSeq);
                msgCtx.setProperty(SynapseConstants.IS_INBOUND, true);
                msgCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, inboundName);
                msgCtx.setProperty(SynapseConstants.ARTIFACT_NAME,
                                   SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + inboundName);
                synapseEnvironment.injectInbound(msgCtx, seq, sequential);
            } else {
                log.error("Sequence: " + injectingSeq + " not found");
            }

            Object rollbackProperty = msgCtx.getProperty(RabbitMQConstants.SET_ROLLBACK_ONLY);
            if ((rollbackProperty instanceof Boolean && ((Boolean) rollbackProperty)) ||
                (rollbackProperty instanceof String && Boolean.parseBoolean((String) rollbackProperty))) {
                return false;
            }

        } catch (AxisFault axisFault) {
            log.error("Error when trying to read incoming message ...", axisFault);
            return false;
        }
        return true;
    }

    /**
     * Create the initial message context for rabbitmq
     */
    private org.apache.synapse.MessageContext createMessageContext() {
        org.apache.synapse.MessageContext msgCtx = synapseEnvironment.createMessageContext();
        MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) msgCtx)
                .getAxis2MessageContext();
        axis2MsgCtx.setServerSide(true);
        axis2MsgCtx.setMessageID(UUID.randomUUID().toString());
        msgCtx.setProperty(MessageContext.CLIENT_API_NON_BLOCKING, true);
        return msgCtx;
    }

}
