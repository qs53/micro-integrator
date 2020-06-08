/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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


package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.endpoints.EndpointSerializer;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.Endpoint;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.ACTIVE_STATUS;
import static org.wso2.micro.integrator.management.apis.Constants.INACTIVE_STATUS;
import static org.wso2.micro.integrator.management.apis.Constants.NAME;
import static org.wso2.micro.integrator.management.apis.Constants.STATUS;

public class EndpointResource implements MiApiResource {

    private static Log LOG = LogFactory.getLog(EndpointResource.class);
    //HTTP method types supported by the resource
    private Set<String> methods;
    // Endpoint is active property
    private static final String IS_ACTIVE = "isActive";

    public EndpointResource() {
        methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_POST);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext,
                          org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {

        if (messageContext.isDoingGET()) {
            String param = Utils.getQueryParameter(messageContext, "endpointName");

            if (Objects.nonNull(param)) {
                populateEndpointData(messageContext, param);
            } else {
                populateEndpointList(messageContext, synapseConfiguration);
            }
        } else {
            try {
                if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
                    Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("JSON payload is missing"));
                    return true;
                }
                JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
                if (payload.has(NAME) && payload.has(STATUS)) {
                    changeEndpointStatus(axis2MessageContext, synapseConfiguration, payload);
                } else {
                    Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("Missing parameters in payload"));
                }
            } catch (IOException e) {
                LOG.error("Error when parsing JSON payload", e);
                Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("Error when parsing JSON payload"));
            }
        }

        return true;
    }

    private void populateEndpointList(MessageContext messageContext, SynapseConfiguration configuration) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        Map<String, Endpoint> namedEndpointMap = configuration.getDefinedEndpoints();
        Collection<Endpoint> namedEndpointCollection = namedEndpointMap.values();

        JSONObject jsonBody = Utils.createJSONList(namedEndpointCollection.size());

        for (Endpoint ep : namedEndpointCollection) {

            JSONObject endpointObject = new JSONObject();

            String epName = ep.getName();
            endpointObject.put(Constants.NAME, epName);

            OMElement element = EndpointSerializer.getElementFromEndpoint(ep);
            OMElement firstElement = element.getFirstElement();
            String type;
            // For template endpoints the endpoint type can not be retrieved from firstElement
            if (firstElement == null) {
                type = element.getAttribute(new QName("template")).getLocalName();
            } else {
                type = firstElement.getLocalName();
            }
            endpointObject.put(Constants.TYPE, type);
            endpointObject.put(IS_ACTIVE, isEndpointActive(ep));

            jsonBody.getJSONArray(Constants.LIST).put(endpointObject);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    private void populateEndpointData(MessageContext messageContext, String endpointName) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        JSONObject jsonBody = getEndpointByName(messageContext, endpointName);

        if (Objects.nonNull(jsonBody)) {
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
    }

    private JSONObject getEndpointByName(MessageContext messageContext, String endpointName) {

        SynapseConfiguration configuration = messageContext.getConfiguration();
        Endpoint ep = configuration.getEndpoint(endpointName);
        if (Objects.nonNull(ep)) {
            return getEndpointAsJson(ep);
        } else {
            return null;
        }
    }

    /**
     * Returns the json representation of the endpoint based on its type.
     *
     * @param endpoint endpoint
     * @return json-object with endpoint details
     */
    private JSONObject getEndpointAsJson(Endpoint endpoint) {

        JSONObject endpointObject = endpoint.getJsonRepresentation();
        OMElement synapseConfiguration = EndpointSerializer.getElementFromEndpoint(endpoint);
        endpointObject.put(Constants.SYNAPSE_CONFIGURATION, synapseConfiguration);
        endpointObject.put(IS_ACTIVE, isEndpointActive(endpoint));

        return endpointObject;
    }

    /**
     * Changes the endpoint state based on json payload.
     *
     * @param axis2MessageContext Axis2Message context
     * @param configuration       Synapse configuration
     * @param payload             Request json payload
     */
    private void changeEndpointStatus(org.apache.axis2.context.MessageContext axis2MessageContext,
                                      SynapseConfiguration configuration, JsonObject payload) {

        String endpointName = payload.get(NAME).getAsString();
        String status = payload.get(STATUS).getAsString();
        Endpoint ep = configuration.getEndpoint(endpointName);
        if (ep != null) {
            JSONObject jsonResponse = new JSONObject();
            if (INACTIVE_STATUS.equalsIgnoreCase(status)) {
                ep.getContext().switchOff();
                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched Off");
            } else if (ACTIVE_STATUS.equalsIgnoreCase(status)) {
                ep.getContext().switchOn();
                jsonResponse.put(Constants.MESSAGE_JSON_ATTRIBUTE, endpointName + " is switched On");
            } else {
                jsonResponse = Utils.createJsonError("Provided state is not valid", axis2MessageContext, Constants.BAD_REQUEST);
            }
            Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);
        } else {
            Utils.setJsonPayLoad(axis2MessageContext,  Utils.createJsonError("Endpoint does not exist",
                    axis2MessageContext, Constants.NOT_FOUND));
        }
    }

    private Boolean isEndpointActive(Endpoint endpoint) {
        // 1 represents the endpoint active state
        return endpoint.getContext().isState(1);
    }
}
