package com.almende.dialog.adapter;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

import com.almende.dialog.LoggedPrintStream;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.test.TestServlet;
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
        AdapterConfig adapterConfig = createAdapterConfig( "XMPP", "agent1@ask-cs.com", localAddressChat, "" );
        //create session
        getOrCreateSession( adapterConfig, Arrays.asList( remoteAddress ) );
        
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", XMPPServlet.class, Message.class );
        XMPPServlet xmppServlet = new XMPPServlet();
        
        Message xmppMessage = getTestXMPPMessage("test body");
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
        String initialAgentURL = TestServlet.TEXT_SERVLET_PATH + "?appointment=start";
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "XMPP", "agent1@ask-cs.com", localAddressChat, initialAgentURL );
        //create session
        getOrCreateSession( adapterConfig, Arrays.asList( remoteAddress ) );

        LoggedPrintStream lpsOut = xmppAppointmentInteraction("hi");
        
        assertTrue( lpsOut.getOutput().toString().contains( "Sending an XMPP Message:" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "Are you available today?\n[ Yup | Nope  ]" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "info@dialog-handler.appspotchat.com" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "sshetty@ask-cs.com" ) );
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
        LoggedPrintStream lpsOut = xmppAppointmentInteraction( "Yup" );
        assertTrue( lpsOut.getOutput().toString().contains( "Sending an XMPP Message:" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "How long are you available? (in mins)" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "info@dialog-handler.appspotchat.com" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "sshetty@ask-cs.com" ) );
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
        
        //respond with a "Yup" message. which is a valid answer
        LoggedPrintStream lpsOut = xmppAppointmentInteraction( "Nope" );
        assertTrue( lpsOut.getOutput().toString().contains( "Sending an XMPP Message:" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "Thanks for responding to the invitation!" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "info@dialog-handler.appspotchat.com" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "sshetty@ask-cs.com" ) );
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
        
        //respond with a "Yup" message. which is a valid answer
        LoggedPrintStream lpsOut = xmppAppointmentInteraction( "30" );
        assertTrue( lpsOut.getOutput().toString().contains( "Sending an XMPP Message:" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "Thanks for accepting the invitation!" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "info@dialog-handler.appspotchat.com" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "sshetty@ask-cs.com" ) );
    }

    /**
     * @return
     * @throws Exception
     */
    private LoggedPrintStream xmppAppointmentInteraction(String message) throws Exception
    {
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", XMPPServlet.class, Message.class );
        XMPPServlet xmppServlet = new XMPPServlet();
        
        Message xmppMessage = getTestXMPPMessage(message);
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, xmppServlet, xmppMessage );
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);

        //collect log information to test processMessage locally
        LoggedPrintStream lpsOut = LoggedPrintStream.create(System.out);
        System.setOut( lpsOut );
        
        int count = (Integer) invokeMethodByReflection( processMessage, xmppServlet, textMessage );
        
        System.out.flush();
        System.setOut( lpsOut.underlying );
        
        assertTrue( count == 1 );
        return lpsOut;
    }
}
