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
package org.apache.qpid.transport;

import org.apache.mina.util.AvailablePortFinder;

import org.apache.qpid.util.concurrent.Condition;

import org.apache.qpid.transport.network.ConnectionBinding;
import org.apache.qpid.transport.network.io.IoAcceptor;
import org.apache.qpid.transport.network.io.IoTransport;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.transport.util.Waiter;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ConnectionTest
 */

public class ConnectionTest extends TestCase implements SessionListener
{

    private static final Logger log = Logger.get(ConnectionTest.class);

    private int port;
    private volatile boolean queue = false;
    private List<MessageTransfer> messages = new ArrayList<MessageTransfer>();

    protected void setUp() throws Exception
    {
        super.setUp();

        port = AvailablePortFinder.getNextAvailable(12000);

        ConnectionDelegate server = new ServerDelegate() {
            @Override public Session getSession(Connection conn, SessionAttach atc)
            {
                Session ssn = super.getSession(conn, atc);
                ssn.setSessionListener(ConnectionTest.this);
                return ssn;
            }
        };

        IoAcceptor ioa = new IoAcceptor
            ("localhost", port, ConnectionBinding.get(server));
        ioa.start();
    }

    public void opened(Session ssn) {}

    public void message(Session ssn, MessageTransfer xfr)
    {
        if (queue)
        {
            messages.add(xfr);
            ssn.processed(xfr);
            return;
        }

        String body = xfr.getBodyString();

        if (body.startsWith("CLOSE"))
        {
            ssn.getConnection().close();
        }
        else if (body.startsWith("ECHO"))
        {
            int id = xfr.getId();
            ssn.invoke(xfr);
            ssn.processed(id);
        }
        else if (body.startsWith("SINK"))
        {
            ssn.processed(xfr);
        }
        else if (body.startsWith("DROP"))
        {
            // do nothing
        }
        else
        {
            throw new IllegalArgumentException
                ("unrecognized message: " + body);
        }
    }

    public void exception(Session ssn, SessionException exc)
    {
        throw exc;
    }

    public void closed(Session ssn) {}

    private void send(Session ssn, String msg)
    {
        ssn.messageTransfer
            ("xxx", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
             null, msg);
    }

    private Connection connect(final Condition closed)
    {
        Connection conn = new Connection();
        conn.setConnectionListener(new ConnectionListener()
        {
            public void opened(Connection conn) {}
            public void exception(Connection conn, ConnectionException exc)
            {
                exc.printStackTrace();
            }
            public void closed(Connection conn)
            {
                if (closed != null)
                {
                    closed.set();
                }
            }
        });
        conn.connect("localhost", port, null, "guest", "guest",false);
        return conn;
    }

    public void testClosedNotificationAndWriteToClosed() throws Exception
    {
        Condition closed = new Condition();
        Connection conn = connect(closed);

        Session ssn = conn.createSession();
        send(ssn, "CLOSE");

        if (!closed.get(3000))
        {
            fail("never got notified of connection close");
        }

        try
        {
            conn.connectionCloseOk();
            fail("writing to a closed socket succeeded");
        }
        catch (TransportException e)
        {
            // expected
        }
    }

    public void testResume() throws Exception
    {
        Connection conn = new Connection();
        conn.connect("localhost", port, null, "guest", "guest",false);

        conn.setConnectionListener(new ConnectionListener()
        {
            public void opened(Connection conn) {}
            public void exception(Connection conn, ConnectionException e)
            {
                throw e;
            }
            public void closed(Connection conn)
            {
                queue = true;
                conn.connect("localhost", port, null, "guest", "guest",false);
                conn.resume();
            }
        });

        Session ssn = conn.createSession(1);
        final List<MessageTransfer> incoming = new ArrayList<MessageTransfer>();
        ssn.setSessionListener(new SessionListener()
        {
            public void opened(Session s) {}
            public void exception(Session s, SessionException e) {}
            public void message(Session s, MessageTransfer xfr)
            {
                synchronized (incoming)
                {
                    incoming.add(xfr);
                    incoming.notifyAll();
                }

                s.processed(xfr);
            }
            public void closed(Session s) {}
        });

        send(ssn, "SINK 0");
        send(ssn, "ECHO 1");
        send(ssn, "ECHO 2");

        ssn.sync();

        String[] msgs = { "DROP 3", "DROP 4", "DROP 5", "CLOSE 6", "SINK 7" };
        for (String m : msgs)
        {
            send(ssn, m);
        }

        ssn.sync();

        assertEquals(msgs.length, messages.size());
        for (int i = 0; i < msgs.length; i++)
        {
            assertEquals(msgs[i], messages.get(i).getBodyString());
        }

        queue = false;

        send(ssn, "ECHO 8");
        send(ssn, "ECHO 9");

        synchronized (incoming)
        {
            Waiter w = new Waiter(incoming, 30000);
            while (w.hasTime() && incoming.size() < 4)
            {
                w.await();
            }

            assertEquals(4, incoming.size());
            assertEquals("ECHO 1", incoming.get(0).getBodyString());
            assertEquals(0, incoming.get(0).getId());
            assertEquals("ECHO 2", incoming.get(1).getBodyString());
            assertEquals(1, incoming.get(1).getId());
            assertEquals("ECHO 8", incoming.get(2).getBodyString());
            assertEquals(0, incoming.get(0).getId());
            assertEquals("ECHO 9", incoming.get(3).getBodyString());
            assertEquals(1, incoming.get(1).getId());
        }
    }

}
