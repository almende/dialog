package com.almende.dialog.adapter;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.junit.Ignore;
import org.junit.Test;

import com.almende.dialog.LoggedPrintStream;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.test.TestServlet;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class MailServletTest extends TestFramework
{
    /**
     * test if a "dummy" TextMessage is generated and processed properly by MailServlet 
     * @throws Exception 
     */
    @Test
    public void MailServletReceiveDummyMessageTest() throws Exception
    {
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "MAIL", "agent1@ask-cs.com", localAddressMail, "" );
        //create session
        getOrCreateSession( adapterConfig, Arrays.asList( remoteAddress ) );
        
        MimeMessage mimeMessage = new MimeMessage( Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddress ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddress, localAddressMail, "dummyData", null );
        mimeMessage.setContent( mimeMultipart );
        
        //fetch and invoke the receieveMessage method
        MailServlet mailServlet = new MailServlet();
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MailServlet.class, 
                                                                  Arrays.asList( MimeMessage.class, String.class ) );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, mailServlet, 
                                                       Arrays.asList( mimeMessage, localAddressMail ));
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);
        int count = (Integer) invokeMethodByReflection( processMessage, mailServlet, textMessage );
        assertTrue( count == 1 );
    }
    
    /**
     * test if a "/help" TextMessage is generated and processed properly by MailServlet
     * @throws Exception 
     */
    @Test
    public void MailServletReceiveHelpMessageTest() throws Exception
    {
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "MAIL", "agent1@ask-cs.com", localAddressMail, "" );
        //create session
        getOrCreateSession( adapterConfig, Arrays.asList( remoteAddress ) );

        MimeMessage mimeMessage = new MimeMessage( Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddress ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddress, localAddressMail, "/help", null );
        mimeMessage.setContent( mimeMultipart );
        
        //fetch and invoke the receieveMessage method
        MailServlet mailServlet = new MailServlet();
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MailServlet.class, 
                                                                  Arrays.asList( MimeMessage.class, String.class ) );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, mailServlet, 
                                                       Arrays.asList( mimeMessage, localAddressMail ));
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);
        int count = (Integer) invokeMethodByReflection( processMessage, mailServlet, textMessage );
        assertTrue( count == 1 );
    }
    
    /**
     * test if a "hi" TextMessage is generated and processed properly by XMPP servlet
     *  as a new session
     * @throws Exception 
     */
    @Test
    @Ignore
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception
    {
        String initialAgentURL = TestServlet.TEXT_SERVLET_PATH + "?appointment=start";
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "MAIL", "agent1@ask-cs.com", localAddressMail, initialAgentURL );
        //create session
        getOrCreateSession( adapterConfig, Arrays.asList( remoteAddress ) );

        LoggedPrintStream lpsOut = mailAppointmentInteraction("hi");
        
        ArrayNode logs = LoggedPrintStream.getLogs();
        
        assertTrue( lpsOut.getOutput().toString().contains( "Sending an XMPP Message:" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "Are you available today?\n[ Yup | Nope  ]" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "info@dialog-handler.appspotchat.com" ) );
        assertTrue( lpsOut.getOutput().toString().contains( "sshetty@ask-cs.com" ) );
    }
    
    /**
     * @return
     * @throws Exception
     */
    private LoggedPrintStream mailAppointmentInteraction(String message) throws Exception
    {
        MimeMessage mimeMessage = new MimeMessage( Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddress ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddress, localAddressMail, "/help", null );
        mimeMessage.setContent( mimeMultipart );
        
        //fetch and invoke the receieveMessage method
        MailServlet mailServlet = new MailServlet();
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MailServlet.class, 
                                                                  Arrays.asList( MimeMessage.class, String.class ) );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, mailServlet, 
                                                       Arrays.asList( mimeMessage, localAddressMail ));
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);
        
        //collect log information to test processMessage locally
        LoggedPrintStream lpsOut = LoggedPrintStream.create(System.out);
        System.setOut( lpsOut );
        
        int count = (Integer) invokeMethodByReflection( processMessage, mailServlet, textMessage );
        
        System.out.flush();
        System.setOut( lpsOut.underlying );
        assertTrue( count == 1 );
        return lpsOut;
    }
}
