package com.almende.dialog.adapter;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.test.TestServlet;
import org.junit.Ignore;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        AdapterConfig adapterConfig = createAdapterConfig( "MAIL", TEST_PUBLIC_KEY, localAddressMail, "" );
        //create session
        getOrCreateSession( adapterConfig, remoteAddressEmail );
        
        MimeMessage mimeMessage = new MimeMessage( Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddressEmail ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddressEmail, localAddressMail, "dummyData", null );
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
        AdapterConfig adapterConfig = createAdapterConfig( "MAIL", TEST_PUBLIC_KEY, localAddressMail, "" );
        //create session
        getOrCreateSession( adapterConfig, remoteAddressEmail );

        MimeMessage mimeMessage = new MimeMessage( Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddressEmail ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddressEmail, localAddressMail, "/help", null );
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
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception
    {
        String initialAgentURL = TestServlet.TEXT_SERVLET_PATH + "?appointment=start";
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "MAIL", TEST_PUBLIC_KEY,
                                                           localAddressMail, initialAgentURL );
        //create session
        getOrCreateSession( adapterConfig, remoteAddressEmail );
        TextMessage textMessage = mailAppointmentInteraction("hi");
        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * use an existing session and send yes to it
     * @throws Exception 
     */
    @Test
    public void AcceptAppointmentExistingSessionMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the first question is alrady asked
        ReceiveAppointmentNewSessionMessageTest();
        
        //send Yup to the Appointment question
        TextMessage textMessage = mailAppointmentInteraction(TestServlet.APPOINTMENT_YES_ANSWER);

        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * use an existing session and send yes to it
     * @throws Exception 
     */
    @Test
    public void AnswerSecondAppointmentQuestionMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the second question is alrady asked
        AcceptAppointmentExistingSessionMessageTest();
        
        //send Yup to the Appointment question
        TextMessage textMessage = mailAppointmentInteraction("120");//send free for 120 mins

        assertOutgoingTextMessage(textMessage);
    }

    /**
     * use an existing session and send no to it
     * @throws Exception 
     */
    @Test
    public void RejectAppointmentExistingSessionMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the first question is alrady asked
        ReceiveAppointmentNewSessionMessageTest();
        
        //send Yup to the Appointment question
        TextMessage textMessage = mailAppointmentInteraction(TestServlet.APPOINTMENT_NO_ANSWER);
        assertOutgoingTextMessage(textMessage);
    }

    /**
     * this is a test to test if the old message block is trimmed off 
     * when an email is sent using the reply button.
     * @throws Exception 
     */
    @Test
    @Ignore
    public void TripOldMessageByReplyRecieveProcessMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the first question is alrady asked
        ReceiveAppointmentNewSessionMessageTest();
        
        //reply Yup to the Appointment question
        //adding some just text as part of the previous email
        String reply = TestServlet.APPOINTMENT_YES_ANSWER + " \n \n \n2013/9/6 <" + localAddressMail +"> \n \n> U heeft een ongeldig aantal gegeven. " +
        		"Geef een getal tussen 1 en 100 000. \n> \n \n \n \n--  \nKind regards, \nShravan Shetty "; 
        mailAppointmentInteraction( reply );
    }
    
    /**
     * @return
     * @throws Exception
     */
    private TextMessage mailAppointmentInteraction(String message) throws Exception
    {
        MimeMessage mimeMessage = new MimeMessage( Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddressEmail ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddressEmail, localAddressMail, message, null );
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
        return textMessage;
    }

    private void assertOutgoingTextMessage(TextMessage textMessage) throws Exception
    {
        javax.mail.Message messageFromDetails = getMessageFromDetails(textMessage.getAddress(), textMessage.getLocalAddress(), responseQuestionString.get());

        assertTrue(logObject.get() instanceof javax.mail.Message);
        javax.mail.Message messageLogged = (javax.mail.Message) logObject.get();
        assertEquals(messageFromDetails.getFrom(), messageLogged.getFrom());
        assertEquals(messageFromDetails.getAllRecipients(), messageLogged.getAllRecipients());
        assertEquals(messageFromDetails.getSubject(), messageLogged.getSubject().replaceAll("RE:|null","").trim());
        assertEquals(messageFromDetails.getContent().toString(), messageLogged.getContent().toString());
    }
}
