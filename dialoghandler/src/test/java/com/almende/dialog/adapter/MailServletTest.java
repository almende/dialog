package com.almende.dialog.adapter;

import static org.junit.Assert.assertTrue;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;

public class MailServletTest extends TestFramework
{
    /**
     * test if a "/help" TextMessage is generated and processed properly by MailServlet
     * @throws Exception 
     */
    @Test
    public void MailServletReceiveHelpMessageTest() throws Exception
    {
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_ACCOUNT_ID,
                                                          localAddressMail, localAddressMail, "");
        //create session
        Session.createSession( adapterConfig, remoteAddressEmail );

        MimeMessage mimeMessage = new MimeMessage( javax.mail.Session.getDefaultInstance( new Properties(), null) );
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
}
