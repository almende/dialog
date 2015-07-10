package com.almende.dialog.adapter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.core.Response.Status;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.RestResponse;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DialogRequestDetails;
import com.askfast.commons.entity.Language;

@Category(IntegrationTest.class)
public class MailServletIT extends TestFramework
{
    /**
     * test if an outgoing Email is triggered by the MailServlet 
     * @throws Exception 
     */
    @Test
    @Ignore
    public void sendDummyMessageTest() throws Exception
    {
        String testMessage = "testMessage";
        //create mail adapter
        AdapterConfig adapterConfig = createEmailAdapter("askfasttest@gmail.com", "askask2times", null, null, null,
                                                         null, null, null, null, UUID.randomUUID().toString(), null,
                                                         null);
        //create session
        Session.createSession( adapterConfig, remoteAddressEmail );
        
        //fetch and invoke the receieveMessage method
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressEmail, "Test" );
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType", QuestionInRequest.SIMPLE_COMMENT.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", testMessage );
        
        MailServlet mailServlet = new MailServlet();
        mailServlet.broadcastMessage(testMessage, "Test", adapterConfig.getXsiUser(), "Test message", addressNameMap,
                                     null, adapterConfig, adapterConfig.getOwner(), null);
        Message message = super.getMessageFromDetails( remoteAddressEmail, localAddressMail, testMessage, "sendDummyMessageTest" );
        assertOutgoingTextMessage( message );
    }
    
    /**
     * test if a "dummy" TextMessage is generated and processed properly by MailServlet 
     * @throws Exception 
     */
    @Test
    public void MailServletReceiveDummyMessageTest() throws Exception
    {
        String initialAgentURL = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType", QuestionInRequest.APPOINTMENT.name());
        initialAgentURL = ServerUtils.getURLWithQueryParams( initialAgentURL, "question", "start" );
        
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_PUBLIC_KEY,
                                                          localAddressMail, localAddressMail, initialAgentURL);
        //create session
        Session.createSession( adapterConfig, remoteAddressEmail );
        
        MimeMessage mimeMessage = new MimeMessage( javax.mail.Session.getDefaultInstance( new Properties(), null) );
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
     * Test if an outbound Appointment question TextMessage is sent and incoming
     * messages to it are accepted as a new session. Should pesist one session
     * and multiple ddrRecords
     * 
     * @throws Exception
     */
    @Test
    public void SendAppointmentNewSessionMessageTest() throws Exception
    {

        //setup actions to generate ddr records when the email is sent or received
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "outgoing", UnitType.PART,
                           AdapterType.EMAIL, null);
        createTestDDRPrice(DDRTypeCategory.INCOMING_COMMUNICATION_COST, 0.1, "outgoing", UnitType.PART,
                           AdapterType.EMAIL, null);
        
        //prepare outbound question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.APPOINTMENT.name());
        url = ServerUtils.getURLWithQueryParams( url, "question", "start" );
        
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_PUBLIC_KEY,
                                                          localAddressMail, localAddressMail, null);
        //send email
        new DialogAgent().outboundCall(remoteAddressEmail, "TEST", "TEST SUBJECT", url, null,
                                       adapterConfig.getConfigId(), TEST_PUBLIC_KEY, null);
        
        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(1));
        assertThat(allSessions.iterator().next().getDirection(), Matchers.is("outbound"));
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertThat(ddrRecords.size(), Matchers.is(1));
    }
    
    /**
     * Test if an outbound Appointment Accept question. A TextMessage is sent
     * and incoming messages to it are accepted with the existing sesison.
     * Should pesist one session and multiple ddrRecords
     * 
     * @throws Exception
     */
    @Test
    public void SendAppointmentAcceptMessageTest() throws Exception
    {

        SendAppointmentNewSessionMessageTest();
        
        //validate that new sessions are created for each transactions
        Session firstSession = Session.getAllSessions().iterator().next();
        
        //accept the invitation
        mailAppointmentInteraction("Yup");
        List<Session> allSessions = Session.getAllSessions();
        //make sure a new session is created and old one is discarded. 
        assertThat(allSessions.size(), Matchers.is(1));
        assertThat(allSessions.iterator().next().getKey(), Matchers.not(firstSession.getKey()));
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertThat(ddrRecords.size(), Matchers.is(3));
        int inboundCount = 0;
        int outboundCount = 0;
        for (DDRRecord ddrRecord : ddrRecords) {
            if(ddrRecord.getDirection().equals("inbound")) {
                inboundCount++;
            }
            if(ddrRecord.getDirection().equals("outbound")) {
                outboundCount++;
            }
        }
        assertTrue(inboundCount == 1);
        assertTrue(outboundCount == 2);
    }
    
    /**
     * Test if an outbound Appointment minutes available question. A TextMessage
     * is sent and incoming messages to it are accepted with the existing
     * sesison. Should pesist one session and multiple ddrRecords
     * 
     * @throws Exception
     */
    @Test
    public void SendAppointmentMinutesAvailableMessageTest() throws Exception
    {

        SendAppointmentAcceptMessageTest();
        
        //accept the invitation
        mailAppointmentInteraction("50");
        List<Session> allSessions = Session.getAllSessions();
        //as it is the end of the question sequence, all sessions must be flushed
        assertThat(allSessions.size(), Matchers.is(0));
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertThat(ddrRecords.size(), Matchers.is(5));
        int inboundCount = 0;
        int outboundCount = 0;
        for (DDRRecord ddrRecord : ddrRecords) {
            if(ddrRecord.getDirection().equals("inbound")) {
                inboundCount++;
            }
            if(ddrRecord.getDirection().equals("outbound")) {
                outboundCount++;
            }
        }
        assertTrue(inboundCount == 2);
        assertTrue(outboundCount == 3);
    }
    
    /**
     * test if a "hi" TextMessage is generated and processed properly by Mail servlet
     *  as a new session
     * @throws Exception 
     */
    @Test
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception
    {
        String initialAgentURL = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.APPOINTMENT.name() );
        initialAgentURL = ServerUtils.getURLWithQueryParams( initialAgentURL, "question", "start" );
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_PUBLIC_KEY,
                                                          localAddressMail, localAddressMail, initialAgentURL);
        //create session
        Session session = Session.createSession( adapterConfig, remoteAddressEmail );
        TextMessage textMessage = mailAppointmentInteraction("hi");
        //update the question text in the textMessage
        Question question = Question.fromURL(initialAgentURL, remoteAddressEmail, session);
        if(question != null) {
            textMessage.setBody(question.getTextWithAnswerTexts(session));
        }
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
        textMessage.setBody(TestServlet.APPOINTMENT_SECOND_QUESION);
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
        textMessage.setBody(TestServlet.APPOINTMENT_ACCEPTANCE_RESPONSE);
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
        textMessage.setBody(TestServlet.APPOINTMENT_REJECT_RESPONSE);
        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * test if a URL passed into the question_text is parsed normally
     * @throws Exception
     */
    @Test
    public void QuestionTextWithURLDoesNotCreateIssuesTest() throws Exception
    {
        String textMessage = "How are you doing?";
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_PUBLIC_KEY,
                                                          localAddressMail, localAddressMail, "");
        //fetch and invoke the receieveMessage method
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressEmail, "Test" );
        String url = TestServlet.TEST_SERVLET_PATH + TestServlet.OPEN_QUESTION_URL_WITH_SPACES;
        url = ServerUtils.getURLWithQueryParams(url, "question", textMessage);
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_INDIA.getCode());
        
        MailServlet mailServlet = new MailServlet();
        mailServlet.startDialog(addressNameMap, null, null, url, "test", "sendDummyMessageTest", adapterConfig,
                                adapterConfig.getOwner());
        
        //assert that one session is created
        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(1));
        assertThat(allSessions.iterator().next().getQuestion(), Matchers.notNullValue());

        Message message = super.getMessageFromDetails( remoteAddressEmail, localAddressMail, textMessage,
            "sendDummyMessageTest" );
        assertOutgoingTextMessage( message );
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
     * Test if the
     * {@link DialogAgent#outboundCallWithDialogRequest(com.askfast.commons.entity.DialogRequestDetails)}
     * gives an error code if the question is not fetched by the dialog agent
     * @throws UnsupportedEncodingException 
     */
    @Test
    public void outboundCallWithoutQuestionTest() throws Exception {
        
        DialogAgent dialogAgent = new DialogAgent();
        //setup bad question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH + "wrongURL", "questionType",
                                                       QuestionInRequest.TWELVE_INPUT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");
        
        //create mail adapter
        AdapterConfig adapterConfig = createEmailAdapter("test@test.com", "testtest", null, null, null, null, null,
                                                         null, null, TEST_PUBLIC_KEY, null, null);
        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.PART,
                           AdapterType.EMAIL, null);
        
        DialogRequestDetails details = new DialogRequestDetails();
        details.setAccountID(adapterConfig.getOwner());
        details.setAdapterID(adapterConfig.getConfigId());
        details.setAddress(remoteAddressEmail);
        details.setBearerToken(UUID.randomUUID().toString());
        details.setMethod("outboundCall");
        details.setUrl(url);
        RestResponse outboundCallResponse = dialogAgent.outboundCallWithDialogRequest(details);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), outboundCallResponse.getCode());
        assertThat(outboundCallResponse.getMessage(), Matchers.is(DialogAgent.getQuestionNotFetchedMessage(url)));
        
        //verify that the session is not saved
        assertEquals(0, Session.getAllSessions().size());
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertEquals(1, ddrRecords.size());
        assertEquals(CommunicationStatus.ERROR, ddrRecords.iterator().next().getStatusForAddress(remoteAddressEmail));
        assertEquals(1, ddrRecords.iterator().next().getStatusPerAddress().size());
    }
    
    /**
     * Check if email body is trimmed off the signature and only the first line
     * of the email is considered as answer
     * @throws Exception 
     */
    @Test
    public void mailBodyWithSignatureTrimTest() throws Exception {

        TextMessage textMessage = new TextMessage();
        textMessage.setBody("yup\r\n\r\nOn Wed, Jul 8, 2015 at 3:19 PM, blabla@bla.com <\r\nblaSender@bla.com> "
            + "wrote:\r\n\r\n> Are you available today? [ Yup | Nope ]\r\n\r\n\r\n\r\n\r\n-- \r\nKind regards,"
            + "\r\nBLA BLA\r\nBLA BLA Engineer\r\nBLA-BLA Company");
        String fristLineOfEmail = new MailServlet().getFirstLineOfEmail(textMessage, "");
        assertThat(fristLineOfEmail, Matchers.is("yup"));
    }
    
    /**
     * @return
     * @throws Exception
     */
    private TextMessage mailAppointmentInteraction(String message) throws Exception
    {
        MimeMessage mimeMessage = new MimeMessage( javax.mail.Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddressEmail ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddressEmail, localAddressMail, message, null );
        mimeMessage.setContent( mimeMultipart );
        
        //fetch and invoke the receieveMessage method
        MailServlet mailServlet = new MailServlet();

        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MailServlet.class, 
                                                                  Arrays.asList( MimeMessage.class, String.class ) );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, mailServlet, 
                                                       Arrays.asList( mimeMessage, localAddressMail ));
        //attach ddrs for the received messages
        Method receiveMessageAndAttachChargeMethod = fetchMethodByReflection("receiveMessageAndAttachCharge",
                                                                             TextServlet.class, TextMessage.class);
        invokeMethodByReflection(receiveMessageAndAttachChargeMethod, mailServlet, textMessage);
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);
        int count = (Integer) invokeMethodByReflection( processMessage, mailServlet, textMessage );
        assertTrue( count == 1 );
        return textMessage;
    }

    private void assertOutgoingTextMessage(TextMessage textMessage) throws Exception {

        javax.mail.Message messageFromDetails = getMessageFromDetails(textMessage.getAddress(),
                                                                      textMessage.getLocalAddress(),
                                                                      textMessage.getBody(), "");
        assertTrue(TestServlet.getLogObject(AdapterType.EMAIL.toString()) instanceof javax.mail.Message);
        javax.mail.Message messageLogged = (javax.mail.Message) TestServlet.getLogObject(AdapterType.EMAIL.toString());
        assertArrayEquals(messageFromDetails.getFrom(), messageLogged.getFrom());
        assertArrayEquals(messageFromDetails.getAllRecipients(), messageLogged.getAllRecipients());
        assertEquals(messageFromDetails.getSubject(), messageLogged.getSubject().replaceAll("RE:|null", "").trim());
        assertEquals(messageFromDetails.getContent().toString(), messageLogged.getContent().toString());
    }
    
    private void assertOutgoingTextMessage(Message message) throws Exception {

        assertTrue(TestServlet.getLogObject(AdapterType.EMAIL.toString()) instanceof javax.mail.Message);
        javax.mail.Message messageLogged = (javax.mail.Message) TestServlet.getLogObject(AdapterType.EMAIL.toString());
        assertArrayEquals(message.getFrom(), messageLogged.getFrom());
        assertArrayEquals(message.getAllRecipients(), messageLogged.getAllRecipients());
        assertEquals(message.getSubject(), messageLogged.getSubject().replaceAll("RE:|null", "").trim());
        assertEquals(message.getContent().toString(), messageLogged.getContent().toString());
    }
}
