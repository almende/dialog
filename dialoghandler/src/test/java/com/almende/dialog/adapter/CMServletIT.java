package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.codehaus.plexus.util.StringInputStream;
import org.codehaus.plexus.util.StringOutputStream;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.Log;
import com.almende.dialog.Logger;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.adapter.tools.SMSDeliveryStatus;
import com.almende.dialog.adapter.tools.SMSDeliveryStatus.SMSStatusCode;
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
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.RestResponse;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.entity.DialogRequest;
import com.askfast.commons.entity.Language;
import com.askfast.commons.utils.PhoneNumberUtils;

@SuppressWarnings("deprecation")
@Category(IntegrationTest.class)
public class CMServletIT extends TestFramework {

    private static final String simpleQuestion = "How are you?";
    String secondTestResponder = "0614567890";
    AdapterConfig adapterConfig = null;

    @Test
    public void outBoundSMSCallSenderNameNotNullTest() throws Exception
    {
        String senderName = "TestUser";
        //create SMS adapter
        adapterConfig = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM, TEST_ACCOUNT_ID, "ASK",
            "ASK", "");

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put( remoteAddressVoice, null );
        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.OPEN_QUESTION, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        assertXMLGeneratedFromOutBoundCall( addressMap, adapterConfig, simpleQuestion, senderName );
    }
    
    /**
     * test if a "Yup" TextMessage is generated and processed properly by SMS
     * servlet as a new session
     * 
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentExistingSessionYesMessageTest() throws Exception {

        //initiate session with a new message
        ReceiveAppointmentNewSessionMessageTest();

        //respond with a "yes" message
        TextMessage textMessage = (TextMessage) processInboundMessage("Yup", remoteAddressVoice, localAddressBroadsoft,
            adapterConfig);
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(textMessage.getAddress(), textMessage.getRecipientName());
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString(
            TestServlet.getJsonAppointmentYesQuestion());

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig.findAdapters(AdapterAgent.ADAPTER_TYPE_SMS, localAddressBroadsoft,
            null).iterator().next();
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress());
    }
    
    @Test
    public void outBoundBroadcastCallSenderNameNotNullTest() throws Exception {

        String remoteAddressVoice2 = "+31614753658";
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM,
                                                          TEST_ACCOUNT_ID, remoteAddressVoice, remoteAddressVoice, "");

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressVoice, "testUser1");
        addressNameMap.put(remoteAddressVoice2, "testUser2");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", simpleQuestion);
        outBoundCall(addressNameMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
            "outBoundBroadcastCallSenderNameNotNullTest", adapterConfig.getOwner());
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, simpleQuestion, senderName);
    }
    
    /**
     * test if a "hi" TextMessage is generated and processed properly by SMS
     * servlet as a new session
     * 
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception {

        String initialAgentURL = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "question", "start");
        initialAgentURL = ServerUtils.getURLWithQueryParams(initialAgentURL, "questionType",
            QuestionInRequest.APPOINTMENT.name());
        //create mail adapter
        adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_SMS, AdapterProviders.CM,
            TEST_ACCOUNT_ID, localAddressBroadsoft, localAddressBroadsoft, initialAgentURL);
        //create session
        Session.createSession(adapterConfig, PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        TextMessage textMessage = processInboundMessage("Hi", remoteAddressVoice, localAddressBroadsoft, adapterConfig);
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(textMessage.getAddress(), textMessage.getRecipientName());
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString(
            TestServlet.getJsonAppointmentQuestion(false, null, null));
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress());
    }
    
    /**
     * Initiates an appointment question with first sending a "hi" SMSMessage.
     * 
     * @throws Exception
     */
    public void sendAppointmentNewSessionMessageTest(AdapterProviders provider) throws Exception
    {
        String initialAgentURL = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "question", "start" );
        initialAgentURL = ServerUtils.getURLWithQueryParams( initialAgentURL, "questionType", QuestionInRequest.APPOINTMENT.name() );
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_SMS, provider,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft,
                                                          localAddressBroadsoft, initialAgentURL);
        //create session
        Session.createSession( adapterConfig, PhoneNumberUtils.formatNumber(remoteAddressVoice, null ));
        TextMessage textMessage = processInboundMessage("Hi", remoteAddressVoice, localAddressBroadsoft, adapterConfig);
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( textMessage.getAddress(), textMessage.getRecipientName() );
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString(TestServlet.getJsonAppointmentQuestion(false,
                                                                                                                            null,
                                                                                                                            null));
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress() );
    }
    
    /**
     * Test if a long (above 160 chars) text is sent and billed properly.
     * 
     * @throws Exception
     */
    @Test
    public void SendLongMessageTest() throws Exception {

        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM,
                                                          TEST_ACCOUNT_ID, "ASK", "ASK", "");

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        String text = "Miusov, as a man man of breeding and deilcacy, could not but feel some inwrd qualms, "
                      + "when he reached the Father Superior's with Ivan: he felt ashamed of havin lost his temper. "
                      + "He felt that he ought to have disdaimed that despicable wretch, Fyodor Pavlovitch, too "
                      + "much to have been upset by him in Father Zossima's cell, and so to have forgotten himself."
                      + "\"Teh monks were not to blame, in any case,\" he reflceted, on the steps. \"And if "
                      + "they're decent people here (and the Father Superior, I understand, is a nobleman) why "
                      + "not be friendly and courteous withthem? I won't argue, I'll fall in with everything,"
                      + "I'll win them by politness, and show them that I've nothing to do with that Aesop, "
                      + "thta buffoon, that Pierrot, and have merely been takken in over this affair, just "
                      + "as they have.\"";
        //create ddrPrice
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.5, "outgoing sms", UnitType.PART,
                           AdapterType.SMS, null);
        outBoundCall(addressMap, adapterConfig, text, QuestionInRequest.SIMPLE_COMMENT, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        assertXMLGeneratedFromOutBoundCall(addressMap, adapterConfig, text, senderName);
        //check the total message parts
        int countMessageParts = CM.countMessageParts(text, addressMap.size());
        //fetch the ddr records
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        Assert.assertThat(ddrRecords.size(), Matchers.is(1));
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        ddrRecord.setShouldIncludeServiceCosts(true);
        ddrRecord.setShouldGenerateCosts(true);
        Assert.assertThat(ddrRecord.getQuantity(), Matchers.is(countMessageParts));
        Assert.assertThat(ddrRecord.getTotalCost(), Matchers.is(0.5 * countMessageParts));
    }
    
    /**
     * Test if a long (above 160 chars) special characters text is sent and billed properly.
     * 
     * @throws Exception
     */
    @Test
    public void SendLongSpecialCharectersMessageTest() throws Exception {

        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM,
                                                          TEST_ACCOUNT_ID, "ASK", "ASK", "");

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        addressMap.put("0612345678", null);
        String text = "ç, @, € and courteous withthem? ç, @, € ç, @, € % ç, @, € ç, @, € ç, @, € ç, " +
                      "@, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, " +
                      "@, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, € ç, @, " +
                      "€ ç, @, € ";
        //create ddrPrice
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.5, "outgoing sms", UnitType.PART,
                           AdapterType.SMS, null);
        outBoundCall(addressMap, adapterConfig, URLEncoder.encode(text, "UTF-8"), QuestionInRequest.SIMPLE_COMMENT,
            senderName, "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        assertXMLGeneratedFromOutBoundCall(addressMap, adapterConfig, text, senderName);
        //check the total message parts
        int countMessageParts = CM.countMessageParts(text, addressMap.size());
        //fetch the ddr records
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        Assert.assertThat(ddrRecords.size(), Matchers.is(1));
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        ddrRecord.setShouldIncludeServiceCosts(true);
        ddrRecord.setShouldGenerateCosts(true);
        Assert.assertThat(Math.ceil(text.length() / 160.0), Matchers.lessThan(new Double(countMessageParts)));
        Assert.assertThat(ddrRecord.getQuantity(), Matchers.is(countMessageParts));
        Assert.assertThat(ddrRecord.getTotalCost(), Matchers.is(0.5 * countMessageParts));
    }
    
    /**
     * Test if an outbound TextMessage send from a shared account for an adapter, is
     * billed (DDRRecord) against the shared account and all logs are linked this accountId too.
     * 
     * @throws Exception
     */
    @Test
    public void checkSharedOwnerIDIsUsedInAllDDRAndLogs() throws Exception {

        createTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 1.0, "Adapter purchage", UnitType.PART, null, null);
        //create an SMS adapter
        String adapterConfigID = new AdapterAgent().createMBAdapter(remoteAddressVoice, null, "1111|blabla", "test",
                                                                    null, TEST_ACCOUNT_ID, null, null);
        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterConfigID);
        assertEquals(TEST_ACCOUNT_ID, adapterConfig.getOwner());
        //collect all ddrRecord ids and log ids
        HashSet<String> ownerDDRRecordIds = new HashSet<String>();
        HashSet<String> ownerLogIds = new HashSet<String>();
        HashSet<String> ownerLogIdsForDDRRecordId = new HashSet<String>();
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        for (DDRRecord ddrRecord : ddrRecords) {
            ownerDDRRecordIds.add(ddrRecord.get_Id());
        }
        List<Log> logs = Logger.find(TEST_ACCOUNT_ID, null, null, null, null, null, null, null);
        for (Log log : logs) {
            ownerLogIds.add(log.getLogId());
        }
        String ddrRecordId = ownerDDRRecordIds.iterator().next();
        logs = Logger.find(TEST_ACCOUNT_ID, ddrRecordId, null, null, null, null, null, null);
        for (Log log : logs) {
            ownerLogIdsForDDRRecordId.add(log.getLogId());
        }

        //add another user as a shared owner of this adapter
        adapterConfig.addAccount(TEST_PRIVATE_KEY);
        adapterConfig.update();

        //send outbound sms using shared accountId
        String senderName = "TestUser";
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressVoice, "testUser1");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", simpleQuestion);
        outBoundCall(addressNameMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
            "outBoundBroadcastCallSenderNameNotNullTest", TEST_PRIVATE_KEY);
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, simpleQuestion, senderName);

        //check that all the new logs belong to the shared account
        List<DDRRecord> ddrRecordsReFetch = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(ddrRecordsReFetch.size(), ddrRecords.size());
        for (DDRRecord ddrRecord : ddrRecordsReFetch) {
            assertTrue(ownerDDRRecordIds.contains(ddrRecord.get_Id()));
        }
        List<Log> logsRefetch = Logger.find(TEST_ACCOUNT_ID, null, null, null, null, null, null, null);
        assertEquals(logsRefetch.size(), logs.size());
        for (Log log : logsRefetch) {
            assertTrue(ownerLogIds.contains(log.getLogId()));
        }
        
        List<Log> logsRefetchForDDRRecordId = Logger.find(TEST_ACCOUNT_ID, ddrRecordId, null, null, null, null, null,
            null);
        assertEquals(logsRefetchForDDRRecordId.size(), logs.size());
        for (Log log : logsRefetchForDDRRecordId) {
            assertTrue(logsRefetchForDDRRecordId.contains(log.getLogId()));
        }

        //check that there are logs formed with shared account
        ddrRecords = getAllDdrRecords(TEST_PRIVATE_KEY);
        assertTrue(ddrRecords.size() > 0);
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
        TextMessage textMessage = processInboundMessage("Nope", remoteAddressVoice, localAddressBroadsoft,
            adapterConfig);
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( textMessage.getAddress(), textMessage.getRecipientName() );
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet
            .getJsonAppointmentNoQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig
                                        .findAdapters(AdapterAgent.ADAPTER_TYPE_SMS, localAddressBroadsoft, null)
                                        .iterator().next();
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress() );
    }
    
    /**
     * Send an SMS to multiple ppl and check if equal number of status entities
     * are created. All are linked to the first reference entity
     * 
     * @throws Exception
     */
    @Test
    public void MultipleAddressStatusEntityTest() throws Exception {

        String myAddress = "Ask-Fast";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM,
                                                          TEST_ACCOUNT_ID, myAddress, myAddress, TEST_PRIVATE_KEY);

        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 1.0, "SMS outbound", UnitType.PART, null, null);
        
        HashMap<String, String> addressMap = new LinkedHashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        addressMap.put(secondTestResponder, "Test");
        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, null, null,
            TEST_ACCOUNT_ID);
        assertXMLGeneratedFromOutBoundCall(addressMap, adapterConfig, simpleQuestion, myAddress);
        //check that multiple SMSDeliveryNotifications are created.
        List<SMSDeliveryStatus> allSMSStatus = SMSDeliveryStatus.fetchAll();
        assertEquals(2, allSMSStatus.size());
        //check that the leading status entity has a reference to other ones corresponding to the address
        boolean linkedStatusEntityChecked = false;
        SMSDeliveryStatus linkeDeliveryStatus = null;
        for (SMSDeliveryStatus smsDeliveryStatus : allSMSStatus) {

            Object linkedDeliveryStatusId = smsDeliveryStatus.getExtraInfos()
                                            .get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
            linkedDeliveryStatusId = linkedDeliveryStatusId != null ? linkedDeliveryStatusId : smsDeliveryStatus
                                            .getExtraInfos().get(PhoneNumberUtils.formatNumber(secondTestResponder,
                                                                                               null));
            if (linkedDeliveryStatusId != null) {
                SMSDeliveryStatus linkedStatus = SMSDeliveryStatus.fetch(linkedDeliveryStatusId.toString());

                //if unlinked status is not loaded yet.. fetch the next item in the list
                if (linkeDeliveryStatus == null) {
                    linkeDeliveryStatus = allSMSStatus.get(1);
                }
                assertEquals(linkedStatus.getReference(), linkeDeliveryStatus.getReference());
                linkedStatusEntityChecked = true;
                //save the parent sms delivery notification in the buffer
                TestServlet.logForTest(AdapterType.SMS.toString(), smsDeliveryStatus.getReference() + "|" +
                                                                   smsDeliveryStatus.getRemoteAddress());
            }
            else {
                linkeDeliveryStatus = smsDeliveryStatus;
            }
        }
        assertTrue(linkedStatusEntityChecked);
    }
    
    /**
     * Send an SMS to multiple ppl and check if equal number of status entities
     * are created. Also check if the delivery status callback works accordingly
     * 
     * @throws Exception
     */
    @Test
    public void MultipleAddressStatusEntityDLRNOtificationTest() throws Exception {

        //initiate multiple address sms
        MultipleAddressStatusEntityTest();
        
        String[] parentStatusDetails = TestServlet.getLogObject(AdapterType.SMS.toString()).toString().split("\\|");
        //check if the DLR acceptance works properly for both numbers

        String testXml = getTestSMSStatusXML(parentStatusDetails[1], parentStatusDetails[0]);
        Method handleStatusReport = fetchMethodByReflection("handleDeliveryStatusReport", CMSmsServlet.class,
                                                            String.class);
        CMSmsServlet cmSmsServlet = new CMSmsServlet();
        Object reportReply = invokeMethodByReflection(handleStatusReport, cmSmsServlet, testXml);
        assertTrue(reportReply instanceof String);
        SMSDeliveryStatus cmStatus = ServerUtils.deserialize(reportReply.toString(), false, SMSDeliveryStatus.class);
        assertEquals("2009-06-15T13:45:30", cmStatus.getSentTimeStamp());
        assertEquals(cmStatus.getRemoteAddress(), PhoneNumberUtils.formatNumber(parentStatusDetails[1], null));
        assertEquals("2009-06-15T13:45:30", cmStatus.getDeliveredTimeStamp());
        assertEquals("0", cmStatus.getCode());
        assertEquals("No Error", cmStatus.getDescription());
        
        //fetch ddr records
        List<DDRRecord> ddrRecords = getAllDdrRecords(cmStatus.getAccountId());
        assertEquals(1, ddrRecords.size());
        assertEquals(ddrRecords.iterator().next().getId(), cmStatus.getDdrRecordId());
        

        //test the status for the second number
        String remoteAddress = parentStatusDetails[1].equals(remoteAddressVoice) ? secondTestResponder
                                                                                : remoteAddressVoice;
        testXml = getTestSMSStatusXML(remoteAddress, parentStatusDetails[0]);
        reportReply = invokeMethodByReflection(handleStatusReport, cmSmsServlet, testXml);
        assertTrue(reportReply instanceof String);
        SMSDeliveryStatus linkeDeliveryStatus = ServerUtils.deserialize(reportReply.toString(), false,
                                                                        SMSDeliveryStatus.class);

        assertEquals(cmStatus.getLinkedSmsDeliveryStatus(remoteAddress).getReference(),
                     linkeDeliveryStatus.getReference());
        assertEquals(cmStatus.getStatusCode(), SMSStatusCode.DELIVERED);
        assertEquals("2009-06-15T13:45:30", cmStatus.getSentTimeStamp());
        assertEquals(linkeDeliveryStatus.getRemoteAddress(), PhoneNumberUtils.formatNumber(remoteAddress, null));
        assertEquals("2009-06-15T13:45:30", linkeDeliveryStatus.getDeliveredTimeStamp());
        assertEquals("0", linkeDeliveryStatus.getCode());
        assertEquals("No Error", linkeDeliveryStatus.getDescription());
        Assert.assertThat(cmStatus.getDdrRecordId(), Matchers.notNullValue());
    }
    
    /**
     * Send an SMS to multiple ppl and check if equal number of status entities
     * are created. Also check if the delivery status callback works
     * accordingly. Perform a GET request on the CM servlet endpoint for the
     * delivery notification.
     * 
     * @throws Exception
     */
    @Test
    public void MultipleAddressStatusEntityDLRNOtificationByURLTest() throws Exception {


        //initiate multiple address sms
        MultipleAddressStatusEntityTest();
        //validate the ddrRecord to make sure that the message is sent
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(1, ddrRecords.size());
        assertThat(ddrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
            Matchers.is(CommunicationStatus.SENT));
        
        String[] parentStatusDetails = TestServlet.getLogObject(AdapterType.SMS.toString()).toString().split("\\|");
        //check if the DLR acceptance works properly for both numbers

        AFHttpClient client = ParallelInit.getAFHttpClient();
        String url = host + "/sms/cm/deliveryStatus";
        url = ServerUtils.getURLWithQueryParams(url, "sent", "2015-11-13T15:40:17");
        url = ServerUtils.getURLWithQueryParams(url, "received", "2015-11-13T15:40:17");
        url = ServerUtils.getURLWithQueryParams(url, "to", remoteAddressVoice);
        url = ServerUtils.getURLWithQueryParams(url, "reference", parentStatusDetails[0]);
        url = ServerUtils.getURLWithQueryParams(url, "statuscode", "0");
        url = ServerUtils.getURLWithQueryParams(url, "errorcode", "");
        client.get(url);

        //fetch ddr records and validate that its delivered
        ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(1, ddrRecords.size());
        assertThat(ddrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
            Matchers.is(CommunicationStatus.DELIVERED));
    }
    
    /**
     * tests if an outbound call works when the sender name is null. In this
     * case it should pick up the adapter.Myaddress as the senderName. <br>
     * 
     * @return
     */
    @Test
    public void outBoundSMSCallSenderNameNullTest() throws Exception {

        String myAddress = "Ask-Fast";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM,
                                                          TEST_ACCOUNT_ID, myAddress, myAddress, TEST_PRIVATE_KEY);
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, null, null,
            TEST_ACCOUNT_ID);
        assertXMLGeneratedFromOutBoundCall(addressMap, adapterConfig, simpleQuestion, myAddress);
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

        //respond with a "30" as mins free message
        TextMessage textMessage = processInboundMessage("30", remoteAddressVoice, localAddressBroadsoft, adapterConfig);
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( textMessage.getAddress(), textMessage.getRecipientName() );
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet
            .getJsonAppointmentFreeQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig
                                        .findAdapters(AdapterAgent.ADAPTER_TYPE_SMS, localAddressBroadsoft, null)
                                        .iterator().next();
        assertXMLGeneratedFromOutBoundCall( addressNameMap, adapterConfig, expectedQuestion,
            textMessage.getLocalAddress() );
    }
    
    @Test
    public void smsDeliveryStatusPayloadTest() throws Exception {

        //send an outbound SMS
        ReceiveAppointmentNewSessionMessageTest();

        String smsRequestXML = TestServlet.getLogObject(AdapterType.SMS.toString()).toString();
        //fetch the sms reference
        DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
        DocumentBuilder newDocumentBuilder = newInstance.newDocumentBuilder();
        Document parse = newDocumentBuilder.parse(new ByteArrayInputStream(smsRequestXML.getBytes("UTF-8")));
        Node referenceNode = parse.getElementsByTagName("REFERENCE").item(0);

        assertTrue(referenceNode != null);
        SMSDeliveryStatus cmStatus = SMSDeliveryStatus.fetch(referenceNode.getTextContent());
        assertTrue(cmStatus != null);
        assertEquals(TestServlet.TEST_SERVLET_PATH, cmStatus.getCallback());

        //mimic a POST call to /sms/cm/deliveryStatus
        CMSmsServlet cmSmsServlet = new CMSmsServlet();
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        Mockito.when(httpServletRequest.getRequestURI()).thenReturn("/dialoghandler/sms/cm/deliveryStatus");
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        Mockito.when(httpServletRequest.getReader()).thenReturn(new BufferedReader(
                         new InputStreamReader(new StringInputStream(getTestSMSStatusXML(PhoneNumberUtils.formatNumber(remoteAddressVoice,null),
                                                                                         referenceNode.getTextContent())))));
        Mockito.when(httpServletResponse.getWriter()).thenReturn(new PrintWriter(new StringOutputStream(), true));
        cmSmsServlet.service(httpServletRequest, httpServletResponse);

        //assert that a POST call was performd on the callback
        assertTrue(TestServlet.getLogObject(AdapterType.SMS.toString()) instanceof SMSDeliveryStatus);
        SMSDeliveryStatus smsPayload = (SMSDeliveryStatus) TestServlet.getLogObject(AdapterType.SMS.toString());
        assertTrue(smsPayload != null);
        assertEquals("Are you available today?\n[ Yup | Nope ]", smsPayload.getSms());
        assertEquals("2009-06-15T13:45:30", smsPayload.getSentTimeStamp());
        assertEquals("2009-06-15T13:45:30", smsPayload.getDeliveredTimeStamp());
        assertTrue(cmStatus.getRemoteAddress().equals(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertEquals("0", smsPayload.getCode());
        assertEquals("No Error", smsPayload.getDescription());
    }
    
    /**
     * create a session, an adapter with keyword. call the inbound message
     * processing method to test if it is handled well.
     * 
     * @throws Exception
     */
    @Test
    public void inboundMBSMSTest() throws Exception {

        AdapterConfig smsAdapter = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_SMS, AdapterProviders.CM,
            TEST_ACCOUNT_ID, "0612345678", "0612345678", null);
        //create a session with already a question (meaning a message is already sent)
        Session session = Session.createSession(smsAdapter, PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.APPOINTMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");
        session.setStartUrl(url);
        Question question = Question.fromURL(url, remoteAddressVoice, session);
        session.setQuestion(question);
        session.storeSession();
        processInboundMessage("yup", remoteAddressVoice, "0612345678", smsAdapter);
        
        //assert that the outboudn message sent matches as expected
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressVoice, "");
        assertXMLGeneratedFromOutBoundCall(addressNameMap, smsAdapter, TestServlet.APPOINTMENT_SECOND_QUESION,
            smsAdapter.getMyAddress());
    }

    @Test
    public void parseSMSDeliveryStatusPayloadTest() throws Exception {

        String remoteNumber = PhoneNumberUtils.formatNumber(remoteAddressVoice, null);
        ReceiveAppointmentNewSessionMessageTest();
        String testXml = getTestSMSStatusXML(remoteNumber, TestServlet.getLogObject("messageId").toString());
        Method handleStatusReport = fetchMethodByReflection("handleDeliveryStatusReport", CMSmsServlet.class,
            String.class);
        CMSmsServlet cmSmsServlet = new CMSmsServlet();
        Object reportReply = invokeMethodByReflection(handleStatusReport, cmSmsServlet, testXml);
        assertTrue(reportReply instanceof String);
        SMSDeliveryStatus cmStatus = ServerUtils.deserialize(reportReply.toString(), false, SMSDeliveryStatus.class);
        assertEquals("2009-06-15T13:45:30", cmStatus.getSentTimeStamp());
        assertTrue(cmStatus.getRemoteAddress().equals(remoteNumber));
        assertEquals("2009-06-15T13:45:30", cmStatus.getDeliveredTimeStamp());
        assertEquals("0", cmStatus.getCode());
        assertEquals("No Error", cmStatus.getDescription());
    }
    
    /**
     * Perform an outbound sms request first. Switch the adapter details with a global switch
     * @throws Exception
     */
    @Test
    public void outBoundSMSCallWithGlobalSwitch() throws Exception {

        
        dialogAgent = Mockito.mock(DialogAgent.class);
        Mockito.when(dialogAgent.getGlobalProviderCredentials()).thenReturn(null);
        outBoundSMSCallSenderNameNotNullTest();

        //set switch related test info
        Map<AdapterProviders, Map<String, AdapterConfig>> globalAdapterCredentials = new HashMap<AdapterProviders, Map<String, AdapterConfig>>();
        AdapterConfig adapterCredentials = new AdapterConfig();
        adapterCredentials.setAccessToken("testTest");
        adapterCredentials.setAccessTokenSecret("testTestSecret");
        adapterCredentials.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        HashMap<String, AdapterConfig> credentials = new HashMap<String, AdapterConfig>();
        credentials.put(DialogAgent.ADAPTER_CREDENTIALS_GLOBAL_KEY, adapterCredentials);
        globalAdapterCredentials.put(AdapterProviders.ROUTE_SMS, credentials);
        
        //mock the Context
        Mockito.when(dialogAgent.getGlobalProviderCredentials()).thenReturn(globalAdapterCredentials);
        
        //fetch the session
        Session session = Session.getSessionByInternalKey("sms", "ASK",
                                                          PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        //switch sms adapter globally
        Mockito.when(dialogAgent.getGlobalAdapterSwitchSettingsForType(AdapterType.SMS))
                                        .thenReturn(AdapterProviders.ROUTE_SMS);
        
        //perform outbound request
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        outBoundCall(addressMap, session.getAdapterConfig(), simpleQuestion, QuestionInRequest.OPEN_QUESTION, "",
            "outBoundSMSCallSenderNameNotNullTest", session.getAdapterConfig().getOwner());
        //fetch the sms delivery status reports
        List<SMSDeliveryStatus> smsStatues = SMSDeliveryStatus.fetchAll();
        Assert.assertThat(smsStatues, Matchers.notNullValue());
        Assert.assertThat(smsStatues.size(), Matchers.is(2));
        boolean smsSentFromRouteSMS = false;
        for (SMSDeliveryStatus smsDeliveryStatus : smsStatues) {
            if(smsDeliveryStatus.getCode() != null) {
                Assert.assertThat(smsDeliveryStatus.getCode(), Matchers.is("1701"));
                Assert.assertThat(smsDeliveryStatus.getDescription(), Matchers.is("Successfully Sent"));
                Assert.assertThat(smsDeliveryStatus.getProvider(), Matchers.is(AdapterProviders.ROUTE_SMS.toString()));
                smsSentFromRouteSMS = true;
            }
        }
        assertTrue(smsSentFromRouteSMS);
        session = Session.getSessionByInternalKey("sms", "ASK", PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        assertTrue(session != null);
        assertEquals("ASK", session.getLocalAddress());
        assertEquals(AdapterAgent.ADAPTER_TYPE_SMS.toLowerCase(), session.getType().toLowerCase());
    }
    
    /**
     * Perform an outbound sms request using an adapter whose credentials are stored globally.
     * So default SMS provider is set as CM and an adapter without credentials is created.
     * @throws Exception
     */
    @Test
    public void outBoundSMSCallWithNoLocalCredentials() throws Exception {

        dialogAgent = Mockito.mock(DialogAgent.class);
        Mockito.when(dialogAgent.getGlobalProviderCredentials()).thenReturn(null);
        outBoundSMSCallSenderNameNotNullTest();

        //set switch related test info
        Map<AdapterProviders, Map<String, AdapterConfig>> globalAdapterCredentials = new HashMap<AdapterProviders, Map<String, AdapterConfig>>();
        AdapterConfig adapterCredentials = new AdapterConfig();
        adapterCredentials.setAccessToken("testTest|blabla");
        adapterCredentials.setAccessTokenSecret("testTestSecret");
        adapterCredentials.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.CM);
        HashMap<String, AdapterConfig> credentials = new HashMap<String, AdapterConfig>();
        credentials.put(DialogAgent.ADAPTER_CREDENTIALS_GLOBAL_KEY, adapterCredentials);
        globalAdapterCredentials.put(AdapterProviders.CM, credentials);
        
        //mock the Context
        Mockito.when(dialogAgent.getGlobalProviderCredentials()).thenReturn(globalAdapterCredentials);
        
        //fetch the session
        Session session = Session.getSessionByInternalKey("sms", "ASK",
                                                          PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        //do not perform any switch sms adapter globally
        Mockito.when(dialogAgent.getGlobalAdapterSwitchSettingsForType(AdapterType.SMS)).thenReturn(null);
        
        //remove the adapter credentials in the adapter locally
        AdapterConfig adapterConfig = session.getAdapterConfig();
        adapterConfig.setAccessToken(null);
        adapterConfig.setAccessTokenSecret(null);
        adapterConfig.update();
        
        //perform outbound request
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS, "",
            "outBoundSMSCallSenderNameNotNullTest", session.getAdapterConfig().getOwner());
        
        //fetch the sms delivery status reports
        List<SMSDeliveryStatus> smsStatues = SMSDeliveryStatus.fetchAll();
        Assert.assertThat(smsStatues, Matchers.notNullValue());
        Assert.assertThat(smsStatues.size(), Matchers.is(2));
        for (SMSDeliveryStatus smsDeliveryStatus : smsStatues) {
            assertEquals(AdapterProviders.CM, AdapterProviders.getByValue(smsDeliveryStatus.getProvider()));
        }
        Session session2 = Session.getSessionByInternalKey("sms", "ASK", PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        assertTrue(session2 != null);
        assertEquals("ASK", session2.getLocalAddress());
        assertEquals(AdapterAgent.ADAPTER_TYPE_SMS.toLowerCase(), session2.getType().toLowerCase());
        assertTrue(session.getKey() != session2.getKey());
    }
    
    /**
     * Test if a proper response is received when an outbound email is triggered
     * for one wrong email address and one valid
     * 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    public void invalidAndValidEmailAddressTest() throws Exception {

        String invalidAddres = "112dd";
        //setup actions to generate ddr records when the email is sent or received
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "outgoing", UnitType.PART,
                           AdapterType.SMS, null);

        //prepare outbound question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.APPOINTMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");

        //create mail adapter
        AdapterConfig smsAdapter = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_SMS, AdapterProviders.CM,
                                                       TEST_ACCOUNT_ID, "0642500086", "0642500086", null);
        //send email
        DialogRequest dialogRequestDetails = new DialogRequest();
        dialogRequestDetails.setAccountID(TEST_ACCOUNT_ID);
        dialogRequestDetails.setAdapterID(smsAdapter.getConfigId());
        dialogRequestDetails.setAddressList(Arrays.asList(invalidAddres, remoteAddressVoice));
        dialogRequestDetails.setUrl(url);

        RestResponse dialogRequest = new DialogAgent().outboundCallWithDialogRequest(dialogRequestDetails);

        assertThat(dialogRequest.getCode(), Matchers.is(201));
        Map<String, String> result = (Map<String, String>) dialogRequest.getResult(Map.class);
        assertThat(result.get(invalidAddres).toString(),
                   Matchers.is(String.format(DialogAgent.INVALID_ADDRESS_MESSAGE, invalidAddres)));
        assertThat(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)).toString(),
                   Matchers.not(String.format(DialogAgent.INVALID_ADDRESS_MESSAGE, invalidAddres)));

        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(1));
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(ddrRecords.size(), Matchers.is(1));
    }
    
    /**
     * Test if a proper response is received when an outbound email is triggered
     * for no method and also no address given. So basically a dialog method
     * isnt deduced based on the addresses given
     * 
     * @throws Exception
     */
    @Test
    public void invalidDialogMethodAndEmptyAddressTest() throws Exception {

        //setup actions to generate ddr records when the email is sent or received
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "outgoing", UnitType.PART,
                           AdapterType.SMS, null);

        //prepare outbound question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.APPOINTMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");

        //create mail adapter
        AdapterConfig smsAdapter = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_SMS, AdapterProviders.CM,
                                                       TEST_ACCOUNT_ID, "0642500086", "0642500086", null);
        //send email
        DialogRequest dialogRequestDetails = new DialogRequest();
        dialogRequestDetails.setAccountID(TEST_ACCOUNT_ID);
        dialogRequestDetails.setAdapterID(smsAdapter.getConfigId());
        dialogRequestDetails.setUrl(url);

        RestResponse dialogRequest = new DialogAgent().outboundCallWithDialogRequest(dialogRequestDetails);

        assertThat(dialogRequest.getCode(), Matchers.is(400));
        assertThat(dialogRequest.getResult().toString(), Matchers.is(Status.BAD_REQUEST.getReasonPhrase()));

        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(0));
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(ddrRecords.size(), Matchers.is(0));
    }
    
    /**
     * Test if a proper response is received when an outbound email is triggered
     * for no method and also no address given. So basically a dialog method
     * isnt deduced based on the addresses given
     * 
     * @throws Exception
     */
    @Test
    public void invalidDialogMethodTest() throws Exception {

        //setup actions to generate ddr records when the email is sent or received
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "outgoing", UnitType.PART,
                           AdapterType.SMS, null);

        //prepare outbound question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.APPOINTMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");

        //create mail adapter
        AdapterConfig smsAdapter = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_SMS, AdapterProviders.CM,
                                                       TEST_ACCOUNT_ID, "0642500086", "0642500086", null);
        //send email
        DialogRequest dialogRequestDetails = new DialogRequest();
        dialogRequestDetails.setAccountID(TEST_ACCOUNT_ID);
        dialogRequestDetails.setAdapterID(smsAdapter.getConfigId());
        dialogRequestDetails.setAddress(remoteAddressVoice);
        dialogRequestDetails.setMethod("invalid method");
        dialogRequestDetails.setUrl(url);

        RestResponse dialogRequest = new DialogAgent().outboundCallWithDialogRequest(dialogRequestDetails);

        assertThat(dialogRequest.getCode(), Matchers.is(400));
        assertThat(dialogRequest.getResult(Map.class).get("Error").toString(),
                   Matchers.is(DialogAgent.INVALID_METHOD_TYPE_MESSAGE + "invalid method"));

        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(0));
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(ddrRecords.size(), Matchers.is(0));
    }
    
    /**
     * check if a trial message is played when an outbound call is initiated
     * from a trial account sharing an adapter with POST PAID type owner
     * account.
     * 
     * @throws Exception
     */
    @Test
    public void trialMessagePlayedWithSharingPostPaidAccountOutBoundTest() throws Exception {

        //setup actions to generate ddr records when the email is sent or received
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "outgoing", UnitType.PART,
            AdapterType.SMS, null);
        
        String sharedAccountId = UUID.randomUUID().toString();

        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressVoice, "Test");
        //create POST PAID CALL adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_SMS, AdapterProviders.CM,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localAddressBroadsoft, null);
        adapterConfig.setPreferred_language(Language.DUTCH.getCode());
        adapterConfig.setAccountType(AccountType.POST_PAID);
        adapterConfig.addAccount(sharedAccountId);
        adapterConfig.update();

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "Some random text, no one bothers!");

        //trigger an outbound call
        dialogAgent = dialogAgent != null ? dialogAgent : new DialogAgent();
        HashMap<String, String> outboundCallWithMap = dialogAgent.outboundCallWithMap(addressNameMap, null, null,
            "Test", null, url, null, adapterConfig.getConfigId(), sharedAccountId, "", AccountType.TRIAL);
        
        //check the message sent
        DDRRecord ddrRecord = getAllDdrRecords(sharedAccountId).iterator().next();
        Assert.assertThat(ddrRecord.getAccountType(), Matchers.is(AccountType.TRIAL));
        
        //fetch the contents of the sms sent
        String message = ddrRecord.getAdditionalInfo().get("text").toString();
        Assert.assertThat(message,
            Matchers.containsString("Dit is een proefaccount. Overweeg alstublieft om uw account te upgraden. "));
        
        //check the session and validate the question for the trial audio
        Session session = Session.getSession(outboundCallWithMap.values().iterator().next());
        Assert.assertThat(session.getAccountType(), Matchers.is(AccountType.TRIAL));
    }

    private String getTestSMSStatusXML(String to, String reference) {

        return "<?xml version=\"1.0\"?> \r\n<MESSAGES SENT=\"2009-06-15T13:45:30\" > \r\n <MSG RECEIVED=" +
            "\"2009-06-15T13:45:30\" > \r\n <TO>" + to + "</TO> " + "\r\n <REFERENCE>" + reference + "</REFERENCE> " +
            "\r\n <STATUS> \r\n <CODE>200</CODE> \r\n <ERRORCODE>0</ERRORCODE> " +
            "\r\n <ERRORDESCRIPTION>No Error</ERRORDESCRIPTION> \r\n </STATUS> \r\n </MSG> \r\n</MESSAGES>";
    }
}
