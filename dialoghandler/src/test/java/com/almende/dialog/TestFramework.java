
package com.almende.dialog;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.After;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.MBSmsServlet;
import com.almende.dialog.adapter.TextServlet;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.DatastoreThread;
import com.almende.util.ParallelInit;
import com.almende.util.TypeUtil;
import com.askfast.commons.Status;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;


/**
 * Test framework to be inherited by all test classes
 * 
 * @author Shravan
 */
public class TestFramework{
    protected static final String localAddressMail = "info@dialog-handler.appspotmail.com";
    protected static final String localAddressChat = "info@dialog-handler.appspotchat.com";
    protected static final String remoteAddressEmail = "info@askcs.com";
    protected static final String localAddressBroadsoft = "0854881000";
    protected static final String localFullAddressBroadsoft = localAddressBroadsoft + "@blabla.voipit.nl";
    protected static final String remoteAddressVoice = "0614765800";
    protected static final String TEST_ACCOUNT_ID = "test-account-id";
    protected static final String TEST_PUBLIC_KEY = "agent1@ask-cs.com";
    protected static final String TEST_PRIVATE_KEY = "test_private_key";
    private Server server;
    public static final int jettyPort = 8078;
    public static final String host = "http://localhost:" + jettyPort + "/dialoghandler";
    protected static final Logger log = Logger.getLogger( TestFramework.class.toString() );
    protected DialogAgent dialogAgent = null;
    
    @Before
    public void setup() throws Exception {

        TestServlet.TEST_SERVLET_PATH = TestFramework.host + "/unitTestServlet";
        ParallelInit.isTest = true;
        ParallelInit.datastoreThread = new DatastoreThread( true );
        if ( ParallelInit.getDatastore() != null ) {
            ParallelInit.datastore.dropDatabase();
        }
        //check if server has to be started
        Category integrationTestAnnotation = getClass().getAnnotation( Category.class );
        if ( integrationTestAnnotation != null && integrationTestAnnotation.toString().contains( "com.almende.dialog.IntegrationTest" ) ) {

            startJettyServer();
        }
        dialogAgent = new DialogAgent();
        dialogAgent.setDefaultProviderSettings( AdapterType.SMS, AdapterProviders.CM );
        dialogAgent.setDefaultProviderSettings( AdapterType.CALL, AdapterProviders.BROADSOFT );
        dialogAgent.setDefaultProviderSettings(AdapterType.SMS, AdapterProviders.CM);
        dialogAgent.setDefaultProviderSettings(AdapterType.CALL, AdapterProviders.BROADSOFT);
    }

    @After
    public void tearDown() throws Exception {

        if ( ParallelInit.datastore != null ) {
            ParallelInit.datastore.dropDatabase();
        }
        TestServlet.clearLogObject();
        if ( server != null ) {
            server.stop();
            server.destroy();
        }
    }

    public String createSessionKey( AdapterConfig adapterConfig, String responder ) {

        return adapterConfig.getAdapterType() + "|" + adapterConfig.getMyAddress() + "|" + PhoneNumberUtils.formatNumber( responder, null );
    }

    public Session createSession( AdapterConfig adapterConfig, String responder ) {

        return Session.createSession( adapterConfig, responder );
    }

    public AdapterConfig createBroadsoftAdapter() throws Exception {

        return createAdapterConfig( AdapterType.CALL.toString(), AdapterProviders.BROADSOFT, TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, "" );
    }

    public AdapterConfig createTwilioAdapter() throws Exception {

        return createAdapterConfig( AdapterType.CALL.toString(), AdapterProviders.TWILIO, TEST_ACCOUNT_ID, localAddressBroadsoft, localAddressBroadsoft, "" );
    }
    
    public AdapterConfig createTPAdapter() throws Exception {

        return createAdapterConfig(AdapterType.CALL.toString(), AdapterProviders.TP, TEST_ACCOUNT_ID,
            localAddressBroadsoft, localAddressBroadsoft, "");
    }

    public static AdapterConfig createAdapterConfig( String adapterType, AdapterProviders adapterProviders, String accountId, String address, String myAddress, String initiatAgentURL ) throws Exception {

        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setAdapterType( adapterType.toLowerCase() );
        adapterConfig.setAnonymous( false );
        adapterConfig.setPublicKey( accountId );
        adapterConfig.setMyAddress( myAddress );
        adapterConfig.setAddress( address );
        adapterConfig.setAccessToken( "1111|blabla" );
        adapterConfig.setKeyword( "TEST" );
        adapterConfig.setInitialAgentURL( initiatAgentURL );
        adapterConfig.setOwner( accountId );
        adapterConfig.addAccount( accountId );
        if ( adapterProviders != null ) {
            adapterConfig.addMediaProperties( AdapterConfig.ADAPTER_PROVIDER_KEY, adapterProviders );
        }
        String adapterConfigString = adapterConfig.createConfig( ServerUtils.serialize( adapterConfig ) ).getEntity().toString();
        adapterConfig = ServerUtils.deserialize( adapterConfigString, AdapterConfig.class );
        return adapterConfig;
    }

    public static AdapterConfig createAdapterConfig( String adapterType, String owner, Collection<String> linkedAccounts, String myAddress, String initiatAgentURL ) throws Exception {

        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setAdapterType( adapterType );
        adapterConfig.setMyAddress( myAddress );
        adapterConfig.setOwner( owner );
        adapterConfig.setAccounts( linkedAccounts );
        adapterConfig.setInitialAgentURL( initiatAgentURL );
        adapterConfig.setStatus( Status.ACTIVE );
        String adapterConfigString = adapterConfig.createConfig( ServerUtils.serialize( adapterConfig ) ).getEntity().toString();
        return ServerUtils.deserialize( adapterConfigString, AdapterConfig.class );
    }

    public static AdapterConfig createEmailAdapter( String emailAddress, String password, String name, String preferredLanguage, String sendingPort, String sendingHost, String protocol, String receivingHost, String receivingProtocol, String accountId, String initialAgentURL, Boolean isPrivate ) throws Exception {

        String emailAdapterId = new AdapterAgent().createEmailAdapter( emailAddress, password, name, preferredLanguage, sendingPort, sendingHost, protocol, receivingProtocol, receivingHost, accountId, initialAgentURL, null, isPrivate );
        return AdapterConfig.getAdapterConfig( emailAdapterId );
    }

    public static Method fetchMethodByReflection( String methodName, Class<?> class1, Class<?> parameterType ) throws Exception {
        Collection<Class<?>> argumentList = new ArrayList<Class<?>>();
        argumentList.add( parameterType );
        return fetchMethodByReflection( methodName, class1, argumentList );
    }

    public static Method fetchMethodByReflection( String methodName, Class<?> class1, Collection<Class<?>> parameterTypes ) throws Exception {
        Method declaredMethod = class1.getDeclaredMethod( methodName, parameterTypes.toArray( new Class[parameterTypes.size()] ) );
        declaredMethod.setAccessible( true );
        return declaredMethod;
    }

    public static Object invokeMethodByReflection( Method methodToBeFetched, Object targetObject, Object argObjects ) throws Exception {
        Collection<Object> argumentValues = new ArrayList<Object>();
        argumentValues.add( argObjects );
        return invokeMethodByReflection( methodToBeFetched, targetObject, argumentValues );
    }

    public static Object invokeMethodByReflection( Method methodToBeFetched, Object targetObject, Collection<Object> argObjects ) throws Exception {
        return methodToBeFetched.invoke( targetObject, argObjects.toArray( new Object[argObjects.size()] ) );
    }

    public static MimeMultipart getTestMimeMultipart( String from, String to, String body, String stanza ) throws MessagingException {
        MimeMultipart mimeMultipart = new MimeMultipart();
        if ( body != null ) {
            addHeader( mimeMultipart, "body", body );
        }
        if ( from != null ) {
            addHeader( mimeMultipart, "from", from );
        }
        if ( to != null ) {
            addHeader( mimeMultipart, "to", to );
        }
        if ( stanza != null ) {
            addHeader( mimeMultipart, "stanza", stanza );
        }
        return mimeMultipart;
    }

    /**
     * Create a DDRPrice. This will also generate all the default,generic
     * ddrtypes
     * 
     * @param category
     * @param price
     * @param name
     * @param unitType
     * @param adapterType
     * @param adapterId
     * @return
     * @throws Exception
     */
    public static DDRPrice createTestDDRPrice( DDRTypeCategory category, double price, String name, UnitType unitType, AdapterType adapterType, String adapterId ) throws Exception {

        DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
        ddrRecordAgent.generateDefaultDDRTypes();
        Object ddrPriceObject = ddrRecordAgent.createDDRPriceWithNewDDRType( name, category.name(), TimeUtils.getServerCurrentTimeInMillis(), TimeUtils.getCurrentServerTimePlusMinutes( 100 ), price, 0, 10, 1, unitType.name(), adapterType != null ? adapterType.name() : null, adapterId, null );
        TypeUtil<DDRPrice> injector = new TypeUtil<DDRPrice>() {
        };
        return injector.inject( ddrPriceObject );
    }

    private static void addHeader( MimeMultipart mimeMultipart, String key, String value ) throws MessagingException {
        //value appended to the headerValue having from, to, body etc
        String headerValue = "form-data; name=";
        InternetHeaders internetHeaders = new InternetHeaders( new ByteArrayInputStream( "Content-Disposition".getBytes() ) );
        internetHeaders.removeHeader( "Content-Disposition" );
        String appendedHeaderValue = headerValue + "\"" + key + "\"";
        internetHeaders.addHeader( "Content-Disposition", appendedHeaderValue );
        mimeMultipart.addBodyPart( new MimeBodyPart( internetHeaders, value.getBytes() ) );
    }

    public static Document getXMLDocumentBuilder( String xmlContent ) throws Exception {
        DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
        DocumentBuilder newDocumentBuilder = newInstance.newDocumentBuilder();
        Document parse = newDocumentBuilder.parse( new ByteArrayInputStream( xmlContent.getBytes( "UTF-8" ) ) );
        return parse;
    }

    protected javax.mail.Message getMessageFromDetails( String remoteAddress, String localAddress, String messageText, String subject ) throws Exception {
        MimeMessage mimeMessage = new MimeMessage( javax.mail.Session.getDefaultInstance( new Properties(), null ) );
        mimeMessage.setFrom( new InternetAddress( localAddress ) );
        mimeMessage.setSubject( subject );
        mimeMessage.addRecipient( javax.mail.Message.RecipientType.TO, new InternetAddress( remoteAddress ) );
        mimeMessage.setText( messageText );
        return mimeMessage;
    }

    /**
     * Asserts if the given XMLS are equals
     * 
     * @param expected
     * @param actual
     * @throws Exception
     */
    protected void assertXMLGeneratedByTwilioLibrary( String expected, String actual ) throws Exception {

        XMLTestCase xmlTestCase = new XMLTestCase() {
        };
        XMLUnit.setIgnoreAttributeOrder( true );
        XMLUnit.setIgnoreComments( true );
        XMLUnit.setIgnoreWhitespace( true );
        xmlTestCase.assertXMLEqual( expected, actual );
    }

    /**
     * Starts the jetty server within the test
     * 
     * @return
     * @throws Exception
     */
    public void startJettyServer() throws Exception {

        if ( server == null || !server.isRunning() ) {
            server = new Server( jettyPort );
            server.setStopAtShutdown( true );
            WebAppContext webAppContext = new WebAppContext( "src/test/webapp/WEB-INF/web.xml", "/dialoghandler" );
            webAppContext.setResourceBase( "src/test/webapp" );
            webAppContext.setClassLoader( getClass().getClassLoader() );
            server.setHandler( webAppContext );
            try {
                server.start();
            }
            catch ( BindException ex ) {
                log.info( String.format( "Jetty is already running on port: %s. Ignoring request", jettyPort ) );
            }
        }
    }

    /**
     * Returns all the ddr records for this accountId
     * 
     * @param accountId
     * @return
     * @throws Exception 
     */
    protected List<DDRRecord> getAllDdrRecords(String accountId) throws Exception {

        return DDRRecord.getDDRRecords(accountId, null, null, null, null, null, null, null, null, null, null, null,
            null);
    }
    
    /**
     * Sends an outbound call based on the given params
     * @param addressNameMap
     * @param adapterConfig
     * @param simpleQuestion
     * @param questionInRequest
     * @param senderName
     * @param subject
     * @param accountId
     * @param dialogAgent
     * @return
     * @throws Exception
     */
    public HashMap<String, String> outBoundCall(Map<String, String> addressNameMap, AdapterConfig adapterConfig,
        String simpleQuestion, QuestionInRequest questionInRequest, String senderName, String subject, String accountId)
            throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            questionInRequest.name());
        HashMap<String, String> result = null;
        if (simpleQuestion != null) {
            url = ServerUtils.getURLWithQueryParams(url, "question", simpleQuestion);
        }
        if (new MockUtil().isMock(dialogAgent)) {
            Mockito.when(dialogAgent.outboundCallWithMap(addressNameMap, null, null, senderName, subject, url, null,
                adapterConfig.getConfigId(), accountId, "", adapterConfig.getAccountType())).thenCallRealMethod();
        }
        result = dialogAgent.outboundCallWithMap(addressNameMap, null, null, senderName, subject, url, null,
            adapterConfig.getConfigId(), accountId, "", adapterConfig.getAccountType());
        return result;
    }
    
    /**
     * This method processes an inbound text using MBSmsServlet, considering
     * that a session is already active
     * 
     * @param smsAdapter
     * @return The textMessage that is parsed and processed
     * @throws Exception
     */
    protected TextMessage processInboundMessage(String message, String sender, String receiver, AdapterConfig smsAdapter)
        throws Exception {

        //setup test inbound data
        Method receiveMessage = fetchMethodByReflection("receiveMessage", MBSmsServlet.class, HashMap.class);
        MBSmsServlet mbSmsServlet = new MBSmsServlet();
        HashMap<String, String> testInboundSMSData = new HashMap<String, String>();
        testInboundSMSData.put("id", "87708ec0453c4d95a284ff4m68999827");
        testInboundSMSData.put("message", message);
        testInboundSMSData.put("sender", sender);
        testInboundSMSData.put("body", message);
        testInboundSMSData.put("receiver", receiver); //"0612345678");
        Object textMessage = invokeMethodByReflection(receiveMessage, mbSmsServlet, testInboundSMSData);
        receiveMessage = fetchMethodByReflection("receiveMessageAndAttachCharge", TextServlet.class,
            TextMessage.class);
        textMessage = invokeMethodByReflection(receiveMessage, mbSmsServlet, textMessage);

        //process the message
        Method processMessage = fetchMethodByReflection("processMessage", TextServlet.class, TextMessage.class);
        invokeMethodByReflection(processMessage, mbSmsServlet, textMessage);
        return (TextMessage) textMessage;
    }
    
    /**
     * Asserts the xml generated by CMServlet while sending an SMS 
     * @param addressNameMap
     * @param adapterConfig
     * @param simpleQuestion
     * @param senderName
     * @return
     * @throws Exception
     */
    protected String assertXMLGeneratedFromOutBoundCall(Map<String, String> addressNameMap, AdapterConfig adapterConfig,
        String simpleQuestion, String senderName) throws Exception {

        //fetch the xml generated
        Document builder = getXMLDocumentBuilder(TestServlet.getLogObject(AdapterType.SMS.toString()).toString());
        NodeList messageNodeList = builder.getElementsByTagName("MESSAGES");
        NodeList customerNodeList = builder.getElementsByTagName("CUSTOMER");
        NodeList userNodeList = builder.getElementsByTagName("USER");
        NodeList childMessageNodeList = builder.getElementsByTagName("MSG");
        NodeList referenceNodeList = builder.getElementsByTagName("REFERENCE");
        assertTrue(messageNodeList.getLength() == 1);
        assertTrue(customerNodeList.getLength() == 1);
        assertTrue(userNodeList.getLength() == 1);
        assertTrue(referenceNodeList.getLength() == 1);
        assertEquals(addressNameMap.size(), childMessageNodeList.getLength());
        //fetch customerInfo from adapter
        String[] customerInfo = adapterConfig.getAccessToken().split("\\|");
        assertEquals(customerInfo[0], customerNodeList.item(0).getAttributes().getNamedItem("ID").getNodeValue());

        assertEquals(customerInfo[1], userNodeList.item(0).getAttributes().getNamedItem("LOGIN").getNodeValue());

        for (int addressCount = 0; addressCount < addressNameMap.keySet().size(); addressCount++) {
            Node msgNode = childMessageNodeList.item(addressCount);
            NodeList childNodes = msgNode.getChildNodes();
            for (int childNodeCount = 0; childNodeCount < childNodes.getLength(); childNodeCount++) {
                Node childNode = childNodes.item(childNodeCount);
                if (childNode.getNodeName().equals("CONCATENATIONTYPE")) {
                    assertEquals("TEXT", childNode.getFirstChild().getNodeValue());
                }
                else if (childNode.getNodeName().equals("FROM")) {
                    assertEquals(senderName, childNode.getFirstChild().getNodeValue());
                }
                else if (childNode.getNodeName().equals("BODY")) {
                    assertEquals(simpleQuestion, childNode.getFirstChild().getNodeValue());
                }
                else if (childNode.getNodeName().equals("TO")) {
                    boolean addressMatchFlag = false;
                    for (String address : addressNameMap.keySet()) {
                        if (PhoneNumberUtils.formatNumber(address, null)
                                                        .equals(childNode.getFirstChild().getNodeValue())) {
                            addressMatchFlag = true;
                        }
                    }
                    assertTrue(addressMatchFlag);
                }
            }
        }
        return referenceNodeList.item(0).getFirstChild().getNodeValue();
    }
}
