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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.micro.integrator.security.MicroIntegratorSecurityUtils;
import org.wso2.micro.integrator.security.user.api.RealmConfiguration;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.api.UserStoreManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.INTERNAL_SERVER_ERROR;
import static org.wso2.micro.integrator.management.apis.Constants.IS_ADMIN;
import static org.wso2.micro.integrator.management.apis.Constants.NOT_FOUND;
import static org.wso2.micro.integrator.management.apis.Constants.ROLES;
import static org.wso2.micro.integrator.management.apis.Constants.STATUS;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;
import static org.wso2.micro.integrator.management.apis.Constants.USER_ID;


/**
 * Resource for a retrieving and deleting a single user with the userId provided.
 * <p>
 * Handles resources in the form "management/users/{userId}"
 */
public class UserResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(UserResource.class);

    // HTTP method types supported by the resource
    protected Set<String> methods;

    public UserResource() {

        methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_DELETE);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext, org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {
        String httpMethod = axis2MessageContext.getProperty(Constants.HTTP_METHOD_PROPERTY).toString();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Handling " + httpMethod + "request.");
        }
        JSONObject response;
        try {
            switch (httpMethod) {
                case Constants.HTTP_GET: {
                    response = handleGet(messageContext);
                    break;
                }
                case Constants.HTTP_DELETE: {
                    response = handleDelete(messageContext);
                    break;
                }
                default: {
                    response = Utils.createJsonError("Unsupported HTTP method, " + httpMethod + ". Only GET and "
                                                     + "DELETE methods are supported",
                                                     axis2MessageContext, BAD_REQUEST);
                    break;
                }
            }
        } catch (UserStoreException e) {
            response = Utils.createJsonError("Error initializing the user store. Please try again later", e,
                                             axis2MessageContext, INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            response = Utils.createJsonError("Error processing the request", e, axis2MessageContext, BAD_REQUEST);
        } catch (ResourceNotFoundException e) {
            response = Utils.createJsonError("Requested resource not found. ", e, axis2MessageContext, NOT_FOUND);
        }
        axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
        Utils.setJsonPayLoad(axis2MessageContext, response);
        return true;
    }

    protected JSONObject handleGet(MessageContext messageContext) throws UserStoreException, ResourceNotFoundException {
        String user = getUserFromPathParam(messageContext);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Requested details for the user: " + user);
        }
        JSONObject userObject = new JSONObject();
        userObject.put(USER_ID, user);
        String[] roles = getUserStore().getRoleListOfUser(user);
        userObject.put(IS_ADMIN, isAdmin(roles));
        JSONArray list = new JSONArray(roles);
        userObject.put(ROLES, list);
        return userObject;
    }

    protected JSONObject handleDelete(MessageContext messageContext) throws UserStoreException, IOException,
            ResourceNotFoundException {
        String user = getUserFromPathParam(messageContext);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Request received to delete the user: " + user);
        }
        String userName = Utils.getStringPropertyFromMessageContext(messageContext, USERNAME_PROPERTY);
        if (Objects.isNull(userName)) {
            LOG.warn("Deleting a user without authenticating/authorizing the request sender. Adding "
                     + "authentication and authorization handlers is recommended.");
        } else {
            if (userName.equals(user)) {
                throw new IOException("Attempt to delete the logged in user. Operation not allowed. Please login "
                                      + "from another user.");
            }
        }
        getUserStore().deleteUser(user);
        JSONObject jsonBody = new JSONObject();
        jsonBody.put(USER_ID, user);
        jsonBody.put(STATUS, "Deleted");
        return jsonBody;
    }

    protected UserStoreManager getUserStore() throws UserStoreException {
        return MicroIntegratorSecurityUtils.getUserStoreManager();
    }

    protected RealmConfiguration getRealmConfiguration() throws UserStoreException {
        return MicroIntegratorSecurityUtils.getRealmConfiguration();
    }

    private String getUserFromPathParam(MessageContext messageContext) throws UserStoreException,
            ResourceNotFoundException {
        String userId = Utils.getPathParameter(messageContext, USER_ID);
        if (Objects.isNull(userId)) {
            throw new AssertionError("Incorrect path parameter used: " + USER_ID);
        }
        String[] users = getUserStore().listUsers(userId, -1);
        if (null == users || 0 == users.length) {
            throw new ResourceNotFoundException("User: " + userId + " cannot be found.");
        }
        for (String user : users) {
            if (userId.equals(user)) {
                return user;
            }
        }
        throw new ResourceNotFoundException("User: " + userId + " cannot be found.");
    }

    /**
     * Method to assert if the admin role is contained within a list of users
     *
     * @param rolesList the list of roles assigned to a user
     * @return true if the admin role is present in the list of roles provided
     * @throws UserStoreException if any error occurs while reading the realm configuration
     */
    private boolean isAdmin(String[] rolesList) throws UserStoreException {
        return Arrays.asList(rolesList).contains(getRealmConfiguration().getAdminRoleName());
    }

}
