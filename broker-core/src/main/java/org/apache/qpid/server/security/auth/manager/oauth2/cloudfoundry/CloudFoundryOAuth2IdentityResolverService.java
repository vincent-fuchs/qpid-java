/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.security.auth.manager.oauth2.cloudfoundry;

import static org.apache.qpid.configuration.CommonProperties.QPID_SECURITY_TLS_CIPHER_SUITE_BLACK_LIST;
import static org.apache.qpid.configuration.CommonProperties.QPID_SECURITY_TLS_CIPHER_SUITE_WHITE_LIST;
import static org.apache.qpid.configuration.CommonProperties.QPID_SECURITY_TLS_PROTOCOL_BLACK_LIST;
import static org.apache.qpid.configuration.CommonProperties.QPID_SECURITY_TLS_PROTOCOL_WHITE_LIST;
import static org.apache.qpid.server.util.ParameterizedTypes.LIST_OF_STRINGS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.configuration.CommonProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.manager.oauth2.IdentityResolverException;
import org.apache.qpid.server.security.auth.manager.oauth2.OAuth2AuthenticationProvider;
import org.apache.qpid.server.security.auth.manager.oauth2.OAuth2IdentityResolverService;
import org.apache.qpid.server.security.auth.manager.oauth2.OAuth2Utils;
import org.apache.qpid.server.util.ConnectionBuilder;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

@PluggableService
public class CloudFoundryOAuth2IdentityResolverService implements OAuth2IdentityResolverService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudFoundryOAuth2IdentityResolverService.class);
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    public static final String TYPE = "CloudFoundryIdentityResolver";

    private final ObjectMapper _objectMapper = new ObjectMapper();

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public void validate(final OAuth2AuthenticationProvider<?> authProvider) throws IllegalConfigurationException
    {
    }

    @Override
    public Principal getUserPrincipal(final OAuth2AuthenticationProvider<?> authenticationProvider,
                                      final String accessToken) throws IOException, IdentityResolverException
    {
        URL checkTokenEndpoint = authenticationProvider.getIdentityResolverEndpointURI().toURL();
        TrustStore trustStore = authenticationProvider.getTrustStore();
        String clientId = authenticationProvider.getClientId();
        String clientSecret = authenticationProvider.getClientSecret();
        int connectTimeout = authenticationProvider.getContextValue(Integer.class, OAuth2AuthenticationProvider.AUTHENTICATION_OAUTH2_CONNECT_TIMEOUT);
        int readTimeout = authenticationProvider.getContextValue(Integer.class, OAuth2AuthenticationProvider.AUTHENTICATION_OAUTH2_READ_TIMEOUT);
        List<String> tlsProtocolWhiteList = authenticationProvider.getContextValue(List.class, LIST_OF_STRINGS,
                                                                                   QPID_SECURITY_TLS_PROTOCOL_WHITE_LIST);
        List<String> tlsProtocolBlackList = authenticationProvider.getContextValue(List.class, LIST_OF_STRINGS,
                                                                                   QPID_SECURITY_TLS_PROTOCOL_BLACK_LIST);
        List<String> tlsCipherSuiteWhiteList = authenticationProvider.getContextValue(List.class, LIST_OF_STRINGS,
                                                                                      QPID_SECURITY_TLS_CIPHER_SUITE_WHITE_LIST);
        List<String> tlsCipherSuiteBlackList = authenticationProvider.getContextValue(List.class, LIST_OF_STRINGS,
                                                                                      QPID_SECURITY_TLS_CIPHER_SUITE_BLACK_LIST);

        ConnectionBuilder connectionBuilder = new ConnectionBuilder(checkTokenEndpoint);
        connectionBuilder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout);
        if (trustStore != null)
        {
            try
            {
                connectionBuilder.setTrustMangers(trustStore.getTrustManagers());
            }
            catch (GeneralSecurityException e)
            {
                throw new ServerScopedRuntimeException("Cannot initialise TLS", e);
            }
        }
        connectionBuilder.setTlsProtocolWhiteList(tlsProtocolWhiteList)
                .setTlsProtocolBlackList(tlsProtocolBlackList)
                .setTlsCipherSuiteWhiteList(tlsCipherSuiteWhiteList)
                .setTlsCipherSuiteBlackList(tlsCipherSuiteBlackList);

        LOGGER.debug("About to call identity service '{}'", checkTokenEndpoint);
        HttpURLConnection connection = connectionBuilder.build();

        connection.setDoOutput(true); // makes sure to use POST
        connection.setRequestProperty("Accept-Charset", UTF8);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + UTF8);
        connection.setRequestProperty("Accept", "application/json");
        String encoded = DatatypeConverter.printBase64Binary((clientId + ":" + clientSecret).getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encoded);

        final Map<String,String> requestParameters = Collections.singletonMap("token", accessToken);

        connection.connect();

        try (OutputStream output = connection.getOutputStream())
        {
            output.write(OAuth2Utils.buildRequestQuery(requestParameters).getBytes(UTF8));
            output.close();

            try (InputStream input = OAuth2Utils.getResponseStream(connection))
            {
                int responseCode = connection.getResponseCode();
                LOGGER.debug("Call to identity service '{}' complete, response code : {}", checkTokenEndpoint, responseCode);

                Map<String, String> responseMap = null;
                try
                {
                    responseMap = _objectMapper.readValue(input, Map.class);
                }
                catch (JsonProcessingException e)
                {
                    throw new IOException(String.format("Identity resolver '%s' did not return json", checkTokenEndpoint), e);
                }
                if (responseCode != 200)
                {
                    throw new IdentityResolverException(String.format("Identity resolver '%s' failed, response code %d, error '%s', description '%s'",
                                                                      checkTokenEndpoint,
                                                                      responseCode,
                                                                      responseMap.get("error"),
                                                                      responseMap.get("error_description")));
                }
                final String userName = responseMap.get("user_name");
                if (userName == null)
                {
                    throw new IdentityResolverException(String.format("Identity resolver '%s' failed, response did not include 'user_name'",
                                                                      checkTokenEndpoint));
                }
                return new UsernamePrincipal(userName);
            }
        }
    }

    @Override
    public URI getDefaultAuthorizationEndpointURI(final OAuth2AuthenticationProvider<?> oAuth2AuthenticationProvider)
    {
        return null;
    }

    @Override
    public URI getDefaultTokenEndpointURI(final OAuth2AuthenticationProvider<?> oAuth2AuthenticationProvider)
    {
        return null;
    }

    @Override
    public URI getDefaultIdentityResolverEndpointURI(final OAuth2AuthenticationProvider<?> oAuth2AuthenticationProvider)
    {
        return null;
    }

    @Override
    public String getDefaultScope(final OAuth2AuthenticationProvider<?> oAuth2AuthenticationProvider)
    {
        return "";
    }
}
