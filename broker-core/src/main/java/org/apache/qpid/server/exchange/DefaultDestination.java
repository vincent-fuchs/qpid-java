/*
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
 */
package org.apache.qpid.server.exchange;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;

public class DefaultDestination implements MessageDestination
{

    private VirtualHost<?> _virtualHost;

    public DefaultDestination(VirtualHost<?> virtualHost)
    {
        _virtualHost =  virtualHost;
    }

    @Override
    public String getName()
    {
        return ExchangeDefaults.DEFAULT_EXCHANGE_NAME;
    }


    public final  <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                                                                                        String routingAddress,
                                                                                        final InstanceProperties instanceProperties,
                                                                                        final ServerTransaction txn,
                                                                                        final Action<? super MessageInstance> postEnqueueAction)
    {
        if(routingAddress == null || routingAddress.trim().equals(""))
        {
            return 0;
        }
        final MessageDestination dest = _virtualHost.getAttainedMessageDestination(routingAddress);
        if(dest == null)
        {
            routingAddress = _virtualHost.getLocalAddress(routingAddress);
            if(routingAddress.contains("/") && !routingAddress.startsWith("/"))
            {
                String[] parts = routingAddress.split("/",2);
                Exchange<?> exchange = _virtualHost.getAttainedChildFromAddress(Exchange.class, parts[0]);
                if(exchange != null)
                {
                    return exchange.send(message, parts[1], instanceProperties, txn, postEnqueueAction);
                }
            }
            else if(!routingAddress.contains("/"))
            {
                Exchange<?> exchange = _virtualHost.getAttainedChildFromAddress(Exchange.class, routingAddress);
                if(exchange != null)
                {
                    return exchange.send(message, "", instanceProperties, txn, postEnqueueAction);
                }
            }
            return 0;
        }
        else
        {
            return dest.send(message, routingAddress, instanceProperties, txn, postEnqueueAction);
        }
    }

}
