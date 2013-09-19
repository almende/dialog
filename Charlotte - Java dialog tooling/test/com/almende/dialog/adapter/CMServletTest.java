package com.almende.dialog.adapter;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.test.TestServlet;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CMServletTest extends TestFramework
{
    private static final String simpleQuestion = "How are you?";

    @Test
    public void outBoundBroadcastCallSenderNameNotNullTest() throws Exception
    {
        String remoteAddressVoice2 = "4561237890";
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, "", "" );

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressVoice, "testUser1" );
        addressNameMap.put( remoteAddressVoice2, "testUser2" );
        
        NodeList messageNodeList = outBoundSMSCallXMLTest( addressNameMap, adapterConfig, simpleQuestion, senderName );
        
        for ( int nodeCounter = 2; nodeCounter < messageNodeList.item( 0 ).getChildNodes().getLength(); nodeCounter++ )
        {
            Node messageNode = messageNodeList.item( 0 ).getChildNodes().item( nodeCounter );
            assertEquals( "MSG", messageNode.getNodeName() );
            
            assertEquals( "FROM", messageNode.getChildNodes().item( 1 ).getNodeName() );
            assertEquals( senderName, messageNode.getChildNodes().item( 1 ).getChildNodes().item(0 ).getNodeValue() );
            assertEquals( "BODY", messageNode.getChildNodes().item( 2 ).getNodeName() );
            assertEquals( simpleQuestion, messageNode.getChildNodes().item( 2 ).getChildNodes().item(0 ).getNodeValue() );
            assertEquals( "TO", messageNode.getChildNodes().item( 3 ).getNodeName() );
            assertEquals( addressNameMap.keySet().toArray()[nodeCounter - 2], messageNode.getChildNodes().item( 3 ).getChildNodes().item(0 ).getNodeValue() );
        }
    }
    
    @Test
    public void outBoundSMSCallSenderNameNotNullTest() throws Exception
    {
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, "", "" );

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put( remoteAddressVoice, null );
        NodeList messageNodeList = outBoundSMSCallXMLTest(addressMap, adapterConfig, simpleQuestion, senderName );
        
        //assert the xml generated
        Node messageNode = messageNodeList.item( 0 ).getChildNodes().item( 2 );
        assertEquals( "MSG", messageNode.getNodeName() );
        assertEquals( "FROM", messageNode.getChildNodes().item( 1 ).getNodeName() );
        assertEquals( senderName, messageNode.getChildNodes().item( 1 ).getChildNodes().item(0 ).getNodeValue() );
        assertEquals( "BODY", messageNode.getChildNodes().item( 2 ).getNodeName() );
        assertEquals( simpleQuestion, messageNode.getChildNodes().item( 2 ).getChildNodes().item(0 ).getNodeValue() );
        assertEquals( "TO", messageNode.getChildNodes().item( 3 ).getNodeName() );
        assertEquals( remoteAddressVoice, messageNode.getChildNodes().item( 3 ).getChildNodes().item(0 ).getNodeValue() );
    }

    /**
     * tests if an outbound call works when the sender name is null.
     * In this case it should pick up the adapter.Myaddress as the senderName. <br>
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
        NodeList messageNodeList = outBoundSMSCallXMLTest( addressMap, adapterConfig, simpleQuestion, null );
        
        //assert the xml generated
        Node messageNode = messageNodeList.item( 0 ).getChildNodes().item( 2 );
        assertEquals( "MSG", messageNode.getNodeName() );
        assertEquals( "FROM", messageNode.getChildNodes().item( 1 ).getNodeName() );
        assertEquals( myAddress, messageNode.getChildNodes().item( 1 ).getChildNodes().item(0 ).getNodeValue() );
        assertEquals( "BODY", messageNode.getChildNodes().item( 2 ).getNodeName() );
        assertEquals( simpleQuestion, messageNode.getChildNodes().item( 2 ).getChildNodes().item(0 ).getNodeValue() );
        assertEquals( "TO", messageNode.getChildNodes().item( 3 ).getNodeName() );
        assertEquals( remoteAddressVoice, messageNode.getChildNodes().item( 3 ).getChildNodes().item(0 ).getNodeValue() );
    }

    private NodeList outBoundSMSCallXMLTest( Map<String, String> addressNameMap, AdapterConfig adapterConfig, String simpleQuestion, String senderName) throws Exception
    {
        DialogAgent dialogAgent = new DialogAgent();
        if(addressNameMap.size() > 1)
        {
            dialogAgent.outboundCallWithMap( addressNameMap, senderName, TestServlet.TEXT_SERVLET_PATH + "?simpleComment="+ simpleQuestion, 
                                         null, adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
        }
        else
        {
            dialogAgent.outboundCall( addressNameMap.keySet().iterator().next(), senderName, TestServlet.TEXT_SERVLET_PATH + "?simpleComment="+ simpleQuestion, 
                                      null, adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
        }
        
        //fetch the xml generated
        Document builder = getXMLDocumentBuilder( logObject.get().toString() );
        NodeList messageNodeList = builder.getElementsByTagName( "MESSAGES" );
        assertTrue( messageNodeList.getLength() != 0 );
        return messageNodeList;
    }
}
