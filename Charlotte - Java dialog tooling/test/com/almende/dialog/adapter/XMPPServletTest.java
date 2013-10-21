package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.test.TestServlet;
import com.almende.dialog.test.TestServlet.QuestionInRequest;
import com.almende.dialog.util.ServerUtils;
import com.google.appengine.api.xmpp.Message;

public class XMPPServletTest extends TestFramework
{
    /**
     * test if a "/help" TextMessage is generated and processed properly by XMPP servlet
     * @throws Exception 
     */
    @Test
    public void ReceiveHelpMessageTest() throws Exception
    {
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "XMPP", TEST_PUBLIC_KEY, localAddressChat, "" );
        //create session
        getOrCreateSession( adapterConfig, remoteAddressEmail );
        
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", XMPPServlet.class, Message.class );
        XMPPServlet xmppServlet = new XMPPServlet();
        
        Message xmppMessage = getTestXMPPMessage(localAddressChat, remoteAddressEmail, "test body");
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, xmppServlet, xmppMessage );
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);
        int count = (Integer) invokeMethodByReflection( processMessage, xmppServlet, textMessage );
        assertTrue( count == 1 );
    }
    
    /**
     * test if a "hi" TextMessage is generated and processed properly by XMPP servlet
     *  as a new session
     * @throws Exception 
     */
    @Test
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception
    {
        String initialAgentURL = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType", QuestionInRequest.APPOINTMENT.name());
        initialAgentURL = ServerUtils.getURLWithQueryParams( initialAgentURL, "question", "start" );
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "XMPP", TEST_PUBLIC_KEY, localAddressChat, initialAgentURL );
        //create session
        getOrCreateSession( adapterConfig, remoteAddressEmail );

        TextMessage textMessage = xmppAppointmentInteraction("hi");
        assertOutgoingTextMessage(textMessage);
    }

    /**
     * test if a "Yup" TextMessage is generated and processed properly by XMPP servlet
     *  as an existing session
     * @throws Exception 
     */
    @Test
    public void ReceiveAppointmentExistingSessionYesMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentNewSessionMessageTest();
        
        //respond with a "Yup" message. which is a valid answer
        TextMessage textMessage = xmppAppointmentInteraction("Yup");
        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * test if a "No" TextMessage is generated and processed properly by XMPP servlet
     *  as an existing session
     * @throws Exception 
     */
    @Test
    public void ReceiveAppointmentExistingSessionNoMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentNewSessionMessageTest();
        
        //respond with a "Nope" message. which is a valid answer
        TextMessage textMessage = xmppAppointmentInteraction("Nope");
        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * test if a "Yes" TextMessage is generated and processed properly by XMPP servlet
     *  as an existing session
     * @throws Exception 
     */
    @Test
    public void ReceiveAppointmentExistingSessionFreeMessageTest() throws Exception
    {
        //initiate teh session with a new message and yes messsage
        ReceiveAppointmentExistingSessionYesMessageTest();
        
        //respond with a "30" message. which is a valid answer
        TextMessage textMessage = xmppAppointmentInteraction("30");
        assertOutgoingTextMessage(textMessage);
    }

    /**
     * @return
     * @throws Exception
     */
    private TextMessage xmppAppointmentInteraction(String message) throws Exception
    {
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", XMPPServlet.class, Message.class );
        XMPPServlet xmppServlet = new XMPPServlet();
        
        Message xmppMessage = getTestXMPPMessage(localAddressChat, remoteAddressEmail, message);
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, xmppServlet, xmppMessage );
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);

        int count = (Integer) invokeMethodByReflection( processMessage, xmppServlet, textMessage );
        
        assertTrue( count == 1 );
        return textMessage;
    }

    private void assertOutgoingTextMessage(TextMessage textMessage)
            throws Exception
    {
        Message xmppMessage = getTestXMPPMessage(textMessage.getAddress(), textMessage.getLocalAddress(), responseQuestionString.get());

        Message messageLogged = (Message) logObject.get();
        assertEquals(xmppMessage.getFromJid().getId(), messageLogged.getFromJid().getId());
        assertEquals(xmppMessage.getMessageType(), messageLogged.getMessageType());
        assertEquals(xmppMessage.getBody(), messageLogged.getBody());
        assertEquals(xmppMessage.getStanza(), messageLogged.getStanza());
        assertEquals(xmppMessage.getRecipientJids().length, messageLogged.getRecipientJids().length);
        for (int recipientCount = 0; recipientCount < xmppMessage.getRecipientJids().length ; recipientCount++)
        {
            assertEquals(xmppMessage.getRecipientJids()[recipientCount].getId(),
                    messageLogged.getRecipientJids()[recipientCount].getId());
        }
    }
}
