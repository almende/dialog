
package com.almende.dialog.adapter;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Test;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CMStatus;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.test.TestServlet;
import com.almende.dialog.test.TestServlet.QuestionInRequest;
import com.almende.dialog.util.PhoneNumberUtils;
import com.almende.dialog.util.ServerUtils;


public class CMServletTest extends TestFramework
{
    private static final String simpleQuestion = "How are you?";

    @Test
    public void outBoundBroadcastCallSenderNameNotNullTest() throws Exception
    {
        String remoteAddressVoice2 = "+31614753658";
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, "", "" );

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressVoice, "testUser1" );
        addressNameMap.put( remoteAddressVoice2, "testUser2" );

        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.SIMPLE_COMMENT.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", simpleQuestion );
        outBoundSMSCallXMLTest( addressNameMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT,
            senderName, "outBoundBroadcastCallSenderNameNotNullTest" );
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, simpleQuestion, senderName,
            "outBoundBroadcastCallSenderNameNotNullTest" );
    }

    @Test
    public void outBoundSMSCallSenderNameNotNullTest() throws Exception
    {
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, "ASK", "" );

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put( remoteAddressVoice, null );
        outBoundSMSCallXMLTest( addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
            "outBoundSMSCallSenderNameNotNullTest" );
        assertXMLGeneratedFromOutBoundCall( addressMap, adapterConfig, simpleQuestion, senderName,
            "outBoundSMSCallSenderNameNotNullTest" );
    }
    
    /**
     * tests if an outbound call works when the sender name is null. In this
     * case it should pick up the adapter.Myaddress as the senderName. <br>
     * 
     * @return
     */
    @Test
    public void outBoundSMSCallSenderNameNullTest() throws Exception
    {
        String myAddress = "ASK";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, myAddress, TEST_PRIVATE_KEY );

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put( remoteAddressVoice, null );
        outBoundSMSCallXMLTest( addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, null, null );
        assertXMLGeneratedFromOutBoundCall( addressMap, adapterConfig, simpleQuestion, myAddress, null );
    }

    /**
     * test if a "hi" TextMessage is generated and processed properly by SMS
     * servlet as a new session
     * 
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception
    {
        String initialAgentURL = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "question", "start" );
        initialAgentURL = ServerUtils.getURLWithQueryParams( initialAgentURL, "questionType", QuestionInRequest.APPOINTMENT.name() );
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "SMS", TEST_PUBLIC_KEY, localAddressBroadsoft,
            initialAgentURL );
        //create session
        getOrCreateSession( adapterConfig, remoteAddressVoice );
        TextMessage textMessage = smsAppointmentInteraction( "hi" );
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( textMessage.getAddress(), textMessage.getRecipientName() );
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet
            .getJsonAppointmentQuestion() );
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress(), null );
    }

    /**
     * test if a "Yup" TextMessage is generated and processed properly by SMS
     * servlet as a new session
     * 
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentExistingSessionYesMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentNewSessionMessageTest();

        //respond with a "yes" message
        TextMessage textMessage = smsAppointmentInteraction( "Yup" );
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( textMessage.getAddress(), textMessage.getRecipientName() );
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet
            .getJsonAppointmentYesQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig.findAdapters( "SMS", localAddressBroadsoft, null ).iterator()
            .next();
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress(), null);
    }

    /**
     * test if a "Nope" TextMessage is generated and processed properly by SMS
     * servlet as a new session
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentExistingSessionNoMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentNewSessionMessageTest();

        //respond with a "yes" message
        TextMessage textMessage = smsAppointmentInteraction( "Nope" );
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( textMessage.getAddress(), textMessage.getRecipientName() );
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet
            .getJsonAppointmentNoQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig.findAdapters( "SMS", localAddressBroadsoft, null ).iterator()
            .next();
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress(), null );
    }

    /**
     * test if an open question is asked by the SMS servlet when a "Yup" is
     * answerd as a new session
     * 
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentExistingSessionFreeMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentExistingSessionYesMessageTest();

        //respond with a "yes" message
        TextMessage textMessage = smsAppointmentInteraction( "30" );
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( textMessage.getAddress(), textMessage.getRecipientName() );
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet
            .getJsonAppointmentFreeQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig.findAdapters( "SMS", localAddressBroadsoft, null ).iterator()
            .next();
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress(), null );
    }
    
    @Test
    public void parseSMSDeliveryStatusPayloadTest() throws Exception
    {
        String testXml = getTestSMSStatusXML("0031614765863", "SMS-Status_0031614765863_0031636465236_1385978625000");
        Method handleStatusReport = fetchMethodByReflection( "handleDeliveryStatusReport", CMSmsServlet.class,
            String.class );
        CMSmsServlet cmSmsServlet = new CMSmsServlet();
        Object reportReply = invokeMethodByReflection( handleStatusReport, cmSmsServlet, testXml );
        assertTrue( reportReply instanceof CMStatus );
        CMStatus cmStatus = (CMStatus) reportReply;
        assertEquals( "2009-06-15T13:45:30", cmStatus.getSentTimeStamp() );
        assertEquals( "0031614765863", cmStatus.getRemoteAddress() );
        assertEquals( "2009-06-15T13:45:30", cmStatus.getDeliveredTimeStamp() );
        assertEquals( "200", cmStatus.getCode() );
        assertEquals( "0", cmStatus.getErrorCode() );
        assertEquals( "No Error", cmStatus.getErrorDescription() );
    }
    
    @Test
    public void smsDeliveryStatusPayloadTest() throws Exception
    {
        //send an outbound SMS
        ReceiveAppointmentNewSessionMessageTest();
        
        String smsRequestXML = logObject.get().toString();
        //fetch the sms reference
        DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
        DocumentBuilder newDocumentBuilder = newInstance.newDocumentBuilder();
        Document parse = newDocumentBuilder.parse( new ByteArrayInputStream(smsRequestXML.getBytes("UTF-8")) );
        Node referenceNode = parse.getElementsByTagName( "REFERENCE" ).item( 0 );
        
        assertTrue( referenceNode != null );
        CMStatus cmStatus = CMStatus.fetch( referenceNode.getTextContent() );
        assertTrue( cmStatus != null );
        assertEquals( TestServlet.TEST_SERVLET_PATH, cmStatus.getCallback() );
        
        //mimic a POST call to /sms/cm/deliveryStatus
        CMSmsServlet cmSmsServlet = new CMSmsServlet();
        HttpServletRequest httpServletRequest = Mockito.mock( HttpServletRequest.class );
        Mockito.when( httpServletRequest.getRequestURI() ).thenReturn( "/sms/cm/deliveryStatus" );
        Mockito.when( httpServletRequest.getMethod() ).thenReturn( "POST" );
        Mockito.when( httpServletRequest.getReader() ).thenReturn(
            new BufferedReader( new InputStreamReader( new StringInputStream( getTestSMSStatusXML( remoteAddressVoice,
                referenceNode.getTextContent() ) ) ) ) );
        cmSmsServlet.service( httpServletRequest, null );
        
        //assert that a POST call was performd on the callback
        String callbackPayload = logObject.get().toString();
        assertTrue( callbackPayload != null );
        CMStatus smsPayload = ServerUtils.deserialize( callbackPayload, false, CMStatus.class);
        assertTrue( smsPayload != null );
        assertEquals( "Are you available today?\n[ Yup | Nope  ]", smsPayload.getSms() );
        assertEquals( "2009-06-15T13:45:30", smsPayload.getSentTimeStamp() );
        assertEquals( "2009-06-15T13:45:30", smsPayload.getDeliveredTimeStamp() );
        assertEquals( remoteAddressVoice, smsPayload.getRemoteAddress() );
        assertEquals( "200", smsPayload.getCode() );
        assertEquals( "0", smsPayload.getErrorCode() );
        assertEquals( "No Error", smsPayload.getErrorDescription() );
    }
    
    private String getTestSMSStatusXML(String to, String reference)
    {
        return "<?xml version=\"1.0\"?> \r\n<MESSAGES SENT=\"2009-06-15T13:45:30\" > \r\n <MSG RECEIVED="
        + "\"2009-06-15T13:45:30\" > \r\n <TO>" + to + "</TO> "
        + "\r\n <REFERENCE>"+ reference +"</REFERENCE> "
        + "\r\n <STATUS> \r\n <CODE>200</CODE> \r\n <ERRORCODE>0</ERRORCODE> "
        + "\r\n <ERRORDESCRIPTION>No Error</ERRORDESCRIPTION> \r\n </STATUS> \r\n </MSG> \r\n</MESSAGES>";
    }

    /**
     * @return
     * @throws Exception
     */
    private TextMessage smsAppointmentInteraction( String message ) throws Exception
    {
        HashMap<String, String> data = new HashMap<String, String>();
        data.put( "receiver", localAddressBroadsoft );
        data.put( "sender", remoteAddressVoice );
        data.put( "message", message );
        //fetch and invoke the receieveMessage method
        MBSmsServlet smsServlet = new MBSmsServlet();
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MBSmsServlet.class, HashMap.class );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, smsServlet, data );

        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class, TextMessage.class );

        int count = (Integer) invokeMethodByReflection( processMessage, smsServlet, textMessage );
        assertTrue( count == 1 );
        return textMessage;
    }

    private void outBoundSMSCallXMLTest( Map<String, String> addressNameMap, AdapterConfig adapterConfig,
        String simpleQuestion, QuestionInRequest questionInRequest, String senderName, String subject )
    throws Exception
    {
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
            questionInRequest.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", URLEncoder.encode( simpleQuestion, "UTF-8" ));
        DialogAgent dialogAgent = new DialogAgent();
        if ( addressNameMap.size() > 1 )
        {
            dialogAgent.outboundCallWithMap( addressNameMap, null, null, senderName, subject, url, null,
                adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
        }
        else
        {
            dialogAgent.outboundCall( addressNameMap.keySet().iterator().next(), senderName, subject, url, null,
                adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
        }
    }

    private void assertXMLGeneratedFromOutBoundCall( Map<String, String> addressNameMap, AdapterConfig adapterConfig,
        String simpleQuestion, String senderName, String subject ) throws Exception
    {
        //fetch the xml generated
        Document builder = getXMLDocumentBuilder( logObject.get().toString() );
        NodeList messageNodeList = builder.getElementsByTagName( "MESSAGES" );
        NodeList customerNodeList = builder.getElementsByTagName( "CUSTOMER" );
        NodeList userNodeList = builder.getElementsByTagName( "USER" );
        NodeList childMessageNodeList = builder.getElementsByTagName( "MSG" );
        assertTrue( messageNodeList.getLength() == 1 );
        assertTrue( customerNodeList.getLength() == 1 );
        assertTrue( userNodeList.getLength() == 1 );
        assertEquals( addressNameMap.size(), childMessageNodeList.getLength() );
        //fetch customerInfo from adapter
        String[] customerInfo = adapterConfig.getAccessToken().split( "\\|" );
        assertEquals( customerInfo[0], customerNodeList.item( 0 ).getAttributes().getNamedItem( "ID" ).getNodeValue() );

        assertEquals( customerInfo[1], userNodeList.item( 0 ).getAttributes().getNamedItem( "LOGIN" ).getNodeValue() );

        for ( int addressCount = 0 ; addressCount < addressNameMap.keySet().size(); addressCount++ )
        {
            Node msgNode = childMessageNodeList.item( addressCount );
            NodeList childNodes = msgNode.getChildNodes();
            for ( int childNodeCount = 0; childNodeCount < childNodes.getLength(); childNodeCount++ )
            {
                Node childNode = childNodes.item( childNodeCount );
                if ( childNode.getNodeName().equals( "CONCATENATIONTYPE" ) )
                {
                    assertEquals( "TEXT", childNode.getFirstChild().getNodeValue() );
                }
                else if ( childNode.getNodeName().equals( "FROM" ) )
                {
                    assertEquals( senderName, childNode.getFirstChild().getNodeValue() );
                }
                else if ( childNode.getNodeName().equals( "BODY" ) )
                {
                    assertEquals( simpleQuestion, childNode.getFirstChild().getNodeValue() );
                }
                else if ( childNode.getNodeName().equals( "TO" ) )
                {
                    boolean addressMatchFlag = false;
                    for ( String address : addressNameMap.keySet() )
                    {
                        if ( PhoneNumberUtils.formatNumber( address, null ).equals(
                            childNode.getFirstChild().getNodeValue() ) )
                        {
                            addressMatchFlag = true;
                        }
                    }
                    assertTrue( addressMatchFlag );
                }
            }
        }
    }
}
