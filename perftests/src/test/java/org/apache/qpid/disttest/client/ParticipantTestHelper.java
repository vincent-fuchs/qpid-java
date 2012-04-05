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
package org.apache.qpid.disttest.client;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import org.apache.qpid.disttest.message.ParticipantResult;

public class ParticipantTestHelper
{

    public static void assertAtLeast(String message, final long minimumExpected, final long actual)
    {
        assertTrue(message + " " + actual, actual >= minimumExpected);
    }

    public static void assertExpectedResults(ParticipantResult result, String participantName, String registeredClientName, long expectedTestStartTime, Integer expectedNumberOfMessages, Integer expectedPayloadSize, Long expectedTotalPayloadProcessed, Long expectedMinimumExpectedDuration)
    {
        assertFalse(result.hasError());

        assertEquals("unexpected participant name", participantName, result.getParticipantName());
        assertEquals("unexpected client name", registeredClientName, result.getRegisteredClientName());

        assertAtLeast("start time of result is too low", expectedTestStartTime, result.getStartInMillis());
        assertAtLeast("end time of result should be after start time", result.getStartInMillis(), result.getEndInMillis());
        if(expectedNumberOfMessages != null)
        {
            assertEquals("unexpected number of messages", expectedNumberOfMessages.intValue(), result.getNumberOfMessagesProcessed());
        }
        if (expectedPayloadSize != null)
        {
            assertEquals("unexpected payload size", expectedPayloadSize.intValue(), result.getPayloadSize());
        }
        if (expectedTotalPayloadProcessed != null)
        {
            assertEquals("unexpected total payload processed", expectedTotalPayloadProcessed.longValue(), result.getTotalPayloadProcessed());
        }
        if(expectedMinimumExpectedDuration != null)
        {
            assertAtLeast("participant did not take a sufficient length of time.", expectedMinimumExpectedDuration, result.getTimeTaken());
        }
    }
}
