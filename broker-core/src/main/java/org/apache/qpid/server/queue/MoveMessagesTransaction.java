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
package org.apache.qpid.server.queue;

import java.util.List;

import org.apache.qpid.server.filter.MessageFilter;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.TransactionLogResource;

public class MoveMessagesTransaction extends QueueEntryTransaction
{
    private final Queue _destinationQueue;

    public MoveMessagesTransaction(Queue sourceQueue,
                                   List<Long> messageIds,
                                   Queue destinationQueue,
                                   final MessageFilter filter, final int limit)
    {
        super(sourceQueue, messageIds, filter, limit);
        _destinationQueue = destinationQueue;
    }

    @Override
    protected void updateEntry(QueueEntry entry, VirtualHost.Transaction txn)
    {
        ServerMessage msg = entry.getMessage();
        if(msg != null && !msg.isReferenced((TransactionLogResource)_destinationQueue))
        {
            txn.move(entry, _destinationQueue);
        }
    }
}
