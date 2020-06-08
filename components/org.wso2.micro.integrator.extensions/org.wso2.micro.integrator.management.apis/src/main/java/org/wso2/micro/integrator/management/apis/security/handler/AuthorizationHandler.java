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
package org.wso2.micro.integrator.management.apis.security.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.wso2.micro.core.util.CarbonException;
import org.wso2.micro.integrator.management.apis.Constants;
import org.wso2.micro.integrator.management.apis.ManagementApiUndefinedException;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Handler to be used for resources that required admin privileges.
 * <p>
 * This handler will only authorize requests with a token that belong to an admin user.
 */
public class AuthorizationHandler extends AuthorizationHandlerAdapter {

    private static final Log LOG = LogFactory.getLog(AuthorizationHandler.class);
    private String name;

    public AuthorizationHandler(String context) throws CarbonException, XMLStreamException, IOException,
            ManagementApiUndefinedException {
        super(context);
    }

    @Override
    protected void populateDefaultResources() {
        defaultResources = new ArrayList<>(1);
        defaultResources.add(Constants.PREFIX_USERS);
    }

    @Override
    public Boolean invoke(MessageContext messageContext) {

        this.messageContext = messageContext;
        return super.invoke(messageContext);
    }

    @Override
    public String getName() {

        return this.name;
    }

    @Override
    public void setName(String name) {

        this.name = name;
    }

    @Override
    protected Boolean authorize(String userName) {
        if (useCarbonUserStore) {
            //Uses carbon user store
            try {
                return processAuthorizationWithCarbonUserStore(userName);
            } catch (UserStoreException e) {
                LOG.error("Error while authenticating with carbon user store", e);
                return false;
            }
        } else {
            //Uses in memory user store
            LOG.error("Authorization is not supported with the in memory user store. Please plug in a user store for "
                      + "the correct functionality");
            return false;
        }
    }

    /**
     * Processes /users request if the user is an admin.
     *
     * @return if successfully authorized
     */
    private boolean processAuthorizationWithCarbonUserStore(String userName) throws UserStoreException {

        boolean isAuthorized = authorize(userName,
                                         MicroIntegratorSecurityUtils.getRealmConfiguration().getAdminRoleName());
        if (!isAuthorized) {
            LOG.error("User " + userName + " cannot be authorized");
        }
        return isAuthorized;
    }

    private boolean authorize(String username, String desiredRole) {
        try {
            String[] listOfRoles = MicroIntegratorSecurityUtils.getUserStoreManager().getRoleListOfUser(username);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Authorizing user: " + username + " for the role: " + desiredRole + ". Assigned roles for "
                          + "the user: " + Arrays.toString(listOfRoles));
            }
            return Arrays.asList(listOfRoles).contains(desiredRole);
        } catch (UserStoreException e) {
            LOG.error("Error initializing the user store", e);
        }
        return false;
    }
}
