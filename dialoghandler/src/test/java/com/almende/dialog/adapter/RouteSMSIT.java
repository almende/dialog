package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.SMSDeliveryStatus;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;

@Category(IntegrationTest.class)
public class RouteSMSIT extends TestFramework {

    private static final String simpleQuestion = "How are you?";
    AdapterConfig adapterConfig = null;
    String senderName = "0612345678";

    @Before
    public void setup() throws Exception {

        super.setup();
        new DialogAgent().setDefaultProviderSettings(AdapterType.SMS, AdapterProviders.ROUTE_SMS);
    }

    @Test
    public void outBoundSMSCallStatusCheck() throws Exception {

        //create SMS adapter
        adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS, TEST_ACCOUNT_ID,
            "ASK", "ASK", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.APPOINTMENT, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        //fetch the sms delivery status reports
        List<SMSDeliveryStatus> smsStatues = SMSDeliveryStatus.fetchAll();
        Assert.assertThat(smsStatues, Matchers.notNullValue());
        Assert.assertThat(smsStatues.size(), Matchers.is(1));
        Assert.assertThat(smsStatues.iterator().next().getCode(), Matchers.is("1701"));
        Assert.assertThat(smsStatues.iterator().next().getDescription(), Matchers.is("Successfully Sent"));
    }
    
    /**
     * This is a test to verify if RouteSMS is used when <br>
     * 1. An outbound open/closed question sent using ROUTE_SMS using the MB or
     * CM address <br>
     * 2. An inbound SMS reply using CM or MB servlet <br>
     * 3. An outbound SMS must then be sent with RouteSMS (based on the initial
     * step1) and not CM or MB.
     * @throws Exception 
     */
    @Test
    public void outbound2WaySMSTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.07, "sms charge", UnitType.PART,
                           AdapterType.SMS, null);
        
        //send an outbound sms with the apointment question
        outBoundSMSCallStatusCheck();
        //validate if the ddrRecord shows the senderName instead of the adapter myaddress
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        Assert.assertThat(allDdrRecords.size(), Matchers.is(1));
        Assert.assertThat(allDdrRecords.iterator().next().getFromAddress(), Matchers.is(senderName));
        
        //flush the log
        TestServlet.clearLogObject();
        //receive an inbound sms usinwg CM
        processInboundMessage("yup", remoteAddressVoice, "0612345678", adapterConfig);

        //validate if the ddrRecord shows the senderName instead of the adapter myaddress        
        allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        DDRRecord incomingDddrRecord = null;
        DDRRecord outgoingDddrRecord = null;
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getMessage().equals("yup")) {
                incomingDddrRecord = ddrRecord;
            }
            else if(ddrRecord.getMessage().equals(TestServlet.APPOINTMENT_SECOND_QUESION)) {
                outgoingDddrRecord = ddrRecord;
            }
        }
        Assert.assertTrue(incomingDddrRecord.getToAddress().containsKey(senderName));
        Assert.assertThat(outgoingDddrRecord.getFromAddress(), Matchers.is(senderName));

        //The processed message must be sent using the same initial adapter
        List<Session> allSessions = Session.getAllSessions();
        Session session = allSessions.iterator().next();
        Assert.assertThat(allSessions.size(), Matchers.is(1));
        Assert.assertThat(session.getAdapterConfig().getProvider(), Matchers.is(AdapterProviders.ROUTE_SMS));
        Assert.assertThat(session.getQuestion().getQuestion_expandedtext(null, null),
            Matchers.is(TestServlet.APPOINTMENT_SECOND_QUESION));
        //validate the log saved
        Assert.assertNotNull(TestServlet.getLogObject(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));

        //receive an inbound sms using CM saying free for 50mins
        processInboundMessage("50", remoteAddressVoice, "0612345678", adapterConfig);
        //validate if the ddrRecord shows the senderName instead of the adapter myaddress  
        allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        incomingDddrRecord = null;
        outgoingDddrRecord = null;
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getMessage().equals("50")) {
                incomingDddrRecord = ddrRecord;
            }
            else if(ddrRecord.getMessage().equals(TestServlet.APPOINTMENT_ACCEPTANCE_RESPONSE)) {
                outgoingDddrRecord = ddrRecord;
            }
        }
        Assert.assertTrue(incomingDddrRecord.getToAddress().containsKey(senderName));
        Assert.assertThat(outgoingDddrRecord.getFromAddress(), Matchers.is(senderName));
        
        //The processed message must be sent using the same initial adapter
        allSessions = Session.getAllSessions();
        Assert.assertThat(allSessions.size(), Matchers.is(0));
        //leading ddrRecord must have the latest message sent
        Assert.assertEquals(allDdrRecords.size(), 5);
    }
    
    /**
     * Checks outbound SMS with invalid numbers. Creates sessions for landlines
     * number, but should not create sessions for invalid numbers.
     * 
     * @throws Exception
     */
    @Test
    public void outBoundSMSWithInvalidNumberDDRCheck() throws Exception {

        String senderName = "TestUser";
        String landlineNumber = "0103031111";
        String invalidNumber = "1234";

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
                                                          TEST_ACCOUNT_ID, "0612345678", "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //create ddrType for the ddr records to be created.
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.10, "outgoing sms", UnitType.PART,
                           AdapterType.SMS, null);

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        addressMap.put(landlineNumber, null);
        addressMap.put(invalidNumber, null);

        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.OPEN_QUESTION, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        //fetch sessions
        List<Session> allSessions = Session.getAllSessions();
        //sessions for the landline and the invalid numbers must be dropped
        Assert.assertThat(allSessions.size(), Matchers.is(1));
        for (Session session : allSessions) {
            Assert.assertThat(session.getRemoteAddress(), Matchers.not(invalidNumber));
            Assert.assertThat(session.getRemoteAddress(), Matchers.not(landlineNumber));
        }

        //fetch the sms ddr records
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        Assert.assertThat(allSessions.size(), Matchers.is(1));
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        Assert.assertThat(ddrRecord.getStatusForAddress(invalidNumber), Matchers.is(CommunicationStatus.ERROR));
        Assert.assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(landlineNumber, null)),
                          Matchers.is(CommunicationStatus.ERROR));
        Assert.assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                          Matchers.is(CommunicationStatus.SENT));
    }
    
    /**
     * Checks outbound SMS with invalid senderid. Make sure that the session is
     * deleted. No {@link SMSDeliveryStatus} is created. DDR with invalid
     * senderId is created.
     * 
     * @throws Exception
     */
    @Test
    public void outBoundSMSWithInvalidSenderIdCheck() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.07, "sms charge", UnitType.PART,
                           AdapterType.SMS, "");
        String senderName = "TestUser122342345";

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
                                                          TEST_ACCOUNT_ID, "0612345678", "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //create ddrType for the ddr records to be created.
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.10, "outgoing sms", UnitType.PART,
                           AdapterType.SMS, null);

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);

        boolean isExceptionThrown = false;
        try {

            outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
                "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        }
        catch (Exception e) {
            isExceptionThrown = true;
        }
        Assert.assertThat(isExceptionThrown, Matchers.is(true));
        //fetch sessions
        List<Session> allSessions = Session.getAllSessions();
        //no sessions must be found
        Assert.assertThat(allSessions.size(), Matchers.is(0));

        //fetch the sms ddr records
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        Assert.assertThat(ddrRecords.size(), Matchers.is(1));
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        Assert.assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                          Matchers.is(CommunicationStatus.ERROR));

        //check the SMSDeliveryStatus
        List<SMSDeliveryStatus> allSMSStatus = SMSDeliveryStatus.fetchAll();
        //no status must be found
        Assert.assertThat(allSMSStatus.size(), Matchers.is(0));
    }
    
    @Test
    public void outBoundSMSCallDeliveryNotificationTest() throws Exception {

        //send an outbound sms
        outBoundSMSCallStatusCheck();
        List<SMSDeliveryStatus> smsStatues = SMSDeliveryStatus.fetchAll();
        
        Collection<Class<?>> argumentList = new ArrayList<Class<?>>();
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        Method handleDeliveryStatusReportMethod = fetchMethodByReflection("handleDeliveryStatusReport",
                                                                          RouteSmsServlet.class, argumentList);
        Collection<Object> parameterList = new ArrayList<Object>();
        parameterList.add(smsStatues.iterator().next().getReference());
        parameterList.add(TimeUtils.getStringFormatFromDateTime(TimeUtils.getServerCurrentTimeInMillis() - 10000,
                                                                "yyyy-mm-dd hh:mm:ss"));
        parameterList.add(TimeUtils.getStringFormatFromDateTime(TimeUtils.getServerCurrentTimeInMillis(),
                                                                "yyyy-mm-dd hh:mm:ss"));
        parameterList.add(remoteAddressVoice);
        parameterList.add(TEST_PUBLIC_KEY);
        parameterList.add("DELIVRD");
        invokeMethodByReflection(handleDeliveryStatusReportMethod, new RouteSmsServlet(), parameterList);

        //fetch the dlr again and see if it has a delivered status
        smsStatues = SMSDeliveryStatus.fetchAll();
        Assert.assertThat(smsStatues.size(), Matchers.is(1));
        Assert.assertThat(smsStatues.iterator().next().getDescription(), Matchers.is("DELIVRD"));
        Assert.assertThat(smsStatues.iterator().next().getCode(), Matchers.is("1701"));
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

        new DDRRecordAgent().generateDefaultDDRTypes();
        
        //send an outbound sms
        outBoundSMSCallStatusCheck();
        //validate the ddrRecord to make sure that the message is sent
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(1, ddrRecords.size());
        assertThat(
            ddrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
            Matchers.is(CommunicationStatus.SENT));

        List<SMSDeliveryStatus> smsStatues = SMSDeliveryStatus.fetchAll();
        String messageId = smsStatues.iterator().next().getReference();
        //check if the DLR acceptance works properly for both numbers

        AFHttpClient client = ParallelInit.getAFHttpClient();
        String url = host + "/sms/route-sms/deliveryStatus";
        String payload = "";
        payload = ServerUtils.getURLWithQueryParams(payload, "sentdate", TimeUtils.getStringFormatFromDateTime(
            TimeUtils.getServerCurrentTimeInMillis() - 10000, "yyyy-mm-dd hh:mm:ss"));
        payload = ServerUtils.getURLWithQueryParams(payload, "donedate",
            TimeUtils.getStringFormatFromDateTime(TimeUtils.getServerCurrentTimeInMillis(), "yyyy-mm-dd hh:mm:ss"));
        payload = ServerUtils.getURLWithQueryParams(payload, "destination", remoteAddressVoice);
        payload = ServerUtils.getURLWithQueryParams(payload, "source", adapterConfig.getMyAddress());
        payload = ServerUtils.getURLWithQueryParams(payload, "messageid", messageId);
        payload = ServerUtils.getURLWithQueryParams(payload, "status", "DELIVRD");
        payload = payload.replace("?", "");
        client.post(payload, url);

        //fetch ddr records and validate that its delivered
        ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(1, ddrRecords.size());
        assertThat(
            ddrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
            Matchers.is(CommunicationStatus.DELIVERED));
    }
    
    /**
     * Test to check if one session exists per outbound SMS sent in a broadcast scenario.
     * @throws Exception
     */
    @Test
    public void checkOneSessionPerNumberIsCreatedTest() throws Exception {

        String senderName = "TestUser";
        String remoteAddressVoice1 = "0614765801";

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
            TEST_ACCOUNT_ID, "0612345678", "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //create ddrType for the ddr records to be created.
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.10, "outgoing sms", UnitType.PART,
            AdapterType.SMS, null);

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        addressMap.put(remoteAddressVoice1, null);

        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());

        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        DDRRecord outboundDdrRecord = null;
        int outboundDrrCount = 0;
        //made sure only one ddr record exist with outbound charge
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getDirection().equals("outbound")) {
                outboundDdrRecord = allDdrRecords.iterator().next();
                outboundDrrCount++;
            }
        }
        Assert.assertNotNull(outboundDdrRecord);
        Assert.assertThat(outboundDrrCount, Matchers.is(1));
        //make sure two sessions are linked to the ddrRecord
        Assert.assertThat(outboundDdrRecord.getSessionKeys().size(), Matchers.is(2));
        Assert.assertThat(outboundDdrRecord.getStatusPerAddress().size(), Matchers.is(2));
    }
    
    /**
     * Tests to validate if the sesisons are clears for broadcast after the SMS
     * delivery notifitions are received
     * 
     * @throws Exception
     */
    @Test
    public void checkSessionsAreClearedAfterDeliveryNotification() throws Exception {
        
        //send a broadcast
        checkOneSessionPerNumberIsCreatedTest();
        //fetch the message id
        String messageId = TestServlet.getLogObject(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)).toString();
        //make a post delivery notification request for remoteAddressVoice
        performPOSTDeliveryStatusRequest(remoteAddressVoice, messageId);
        //test the dd status
        testDDRStatusAndSessionExistence(remoteAddressVoice, CommunicationStatus.DELIVERED);
        testDDRStatusAndSessionExistence("0614765801", CommunicationStatus.SENT);
        List<Session> allSessions = Session.getAllSessions();
        Assert.assertEquals(allSessions.size(), 0);

        messageId = TestServlet.getLogObject(PhoneNumberUtils.formatNumber("0614765801", null)).toString();
        //make a post delivery notification request for 0614765801
        performPOSTDeliveryStatusRequest("0614765801", messageId);
        //test the dd status
        testDDRStatusAndSessionExistence(remoteAddressVoice, CommunicationStatus.DELIVERED);
        testDDRStatusAndSessionExistence("0614765801", CommunicationStatus.DELIVERED);
        allSessions = Session.getAllSessions();
        Assert.assertEquals(allSessions.size(), 0);
    }
    
    /**
     * Test to check if the SMS quantity is updated correctly for a long sms for a broadcast scenario
     * @throws Exception 
     */
    @Test
    public void checkIfQuantityIsUpdatedCorrectlyInDDRRecord() throws Exception {
        
        //send a broadcast
        checkOneSessionPerNumberIsCreatedTest();
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        DDRRecord outboundDdrRecord = null;
        //made sure only one ddr record exist with outbound charge
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getDirection().equals("outbound")) {
                outboundDdrRecord = allDdrRecords.iterator().next();
            }
        }
        Assert.assertThat(outboundDdrRecord.getQuantity(), Matchers.is(2));
    }
    
    /**
     * Checks that an SMS is not sent to a blacklisted number in the Dialog
     * Agent level.
     * @throws Exception 
     */
    @Test
    public void checkIfSMSIsSentToBlacklistedNumberTest() throws Exception {

        //create SMS adapter
        adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS, TEST_ACCOUNT_ID,
            "ASK", "ASK", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //generate url
        //prepare outbound question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.APPOINTMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");
        
        //generate options to generate ddr records
        new DDRRecordAgent().generateDefaultDDRTypes();
        //trigger outbound request
        triggerOutboundCallForAddresses(Arrays.asList(remoteAddressVoice), Arrays.asList(remoteAddressVoice),
            adapterConfig, url);

        //There should not be any sessions created
        assertThat(Session.getAllSessions().size(), Matchers.is(0));
        //There should not be any DDR with status SENT
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(allDdrRecords.size(), Matchers.is(1));
        assertThat(allDdrRecords.iterator().next().getStatusForAddress(
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null)), Matchers.is(CommunicationStatus.REJECTED));
    }
    
    /**
     * Checks that an SMS is not sent to a blacklisted number while broadcasting
     * in the Dialog Agent level. Appointment question. So session must persist
     * 
     * @throws Exception
     */
    @Test
    public void checkIfSMSIsBroadcastedToBlacklistedNumberTest() throws Exception {

        //create SMS adapter
        adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS, TEST_ACCOUNT_ID,
            "ASK", "ASK", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //generate options to generate ddr records
        new DDRRecordAgent().generateDefaultDDRTypes();

        triggerOutboundCallForAddresses(Arrays.asList(remoteAddressVoice, senderName),
            Arrays.asList(remoteAddressVoice), adapterConfig, null);

        //There should not be any sessions created
        assertThat(Session.getAllSessions().size(), Matchers.is(0));
        //There should not be any DDR with status SENT
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(allDdrRecords.size(), Matchers.is(1));
        assertThat(allDdrRecords.iterator().next().getStatusForAddress(
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null)), Matchers.is(CommunicationStatus.REJECTED));
        assertThat(allDdrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(senderName, null)),
            Matchers.is(CommunicationStatus.SENT));
    }
    
    /**
     * Checks that an SMS is not sent to a blacklisted number while broadcasting
     * in the Dialog Agent level. Comment question. So session must not persist
     * 
     * @throws Exception
     */
    @Test
    public void checkIfSMSIsBroadcastedToBlacklistedNumberTest2() throws Exception {

        String thirdNumber = "0614236543";
        String forthNumber = "0614236542";

        //create SMS adapter
        adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS, TEST_ACCOUNT_ID,
            "ASK", "ASK", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //blackList at the dialogAgent level to be blocked by all channels
        dialogAgent.addAddressToBlackList(thirdNumber, null, TEST_ACCOUNT_ID);

        //generate options to generate ddr records
        new DDRRecordAgent().generateDefaultDDRTypes();
        //trigger outbound request
        triggerOutboundCallForAddresses(Arrays.asList(remoteAddressVoice, senderName, thirdNumber, forthNumber),
            Arrays.asList(remoteAddressVoice), adapterConfig, null);

        //There should not be any sessions created
        assertThat(Session.getAllSessions().size(), Matchers.is(0));
        //There should not be any DDR with status SENT
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(allDdrRecords.size(), Matchers.is(1));
        assertThat(allDdrRecords.iterator().next().getStatusForAddress(
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null)), Matchers.is(CommunicationStatus.REJECTED));
        assertThat(allDdrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(senderName, null)),
            Matchers.is(CommunicationStatus.SENT));
        assertThat(
            allDdrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(forthNumber, null)),
            Matchers.is(CommunicationStatus.SENT));
        assertThat(
            allDdrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(thirdNumber, null)),
            Matchers.is(CommunicationStatus.REJECTED));
    }
    
    /**
     * Performs a RouteSMS delivery status
     * 
     * @param messageId
     * @throws Exception
     */
    private void performPOSTDeliveryStatusRequest(String address, String messageId) throws Exception {

        //make a post delivery notification request for remoteAddressVoice
        String payload = ServerUtils.getURLWithQueryParams("", "messageid", messageId);
        payload = ServerUtils.getURLWithQueryParams(payload, "source", "0612345678"); //0612345678 is the adapter myaddress
        payload = ServerUtils.getURLWithQueryParams(payload, "destination", address);
        payload = ServerUtils.getURLWithQueryParams(payload, "status", "DELIVRD");
        payload = ServerUtils.getURLWithQueryParams(payload, "sentdate",
            TimeUtils.getStringFormatFromDateTime(TimeUtils.getServerCurrentTimeInMillis(), "yyyy-mm-dd hh:mm:ss"));
        payload = ServerUtils.getURLWithQueryParams(payload, "donedate",
            TimeUtils.getStringFormatFromDateTime(TimeUtils.getServerCurrentTimeInMillis(), "yyyy-mm-dd hh:mm:ss"));
        AFHttpClient client = ParallelInit.getAFHttpClient();
        client.post(payload.replace("?", ""), host + "/sms/route-sms/deliveryStatus");
    }

    /**
     * Check if the addressToValidate exists in the ddrRecords and has a status
     * of the given statueToValidate.
     * 
     * @param addressToValidateForStatus
     * @param statusToValidate
     * @throws Exception 
     */
    private void testDDRStatusAndSessionExistence(String addressToValidateForStatus,
        CommunicationStatus statusToValidate) throws Exception {

        //check if ddr record is marked as delivered
        DDRRecord outboundDdrRecord = null;
        for (DDRRecord ddrRecord : getAllDdrRecords(TEST_ACCOUNT_ID)) {
            if (ddrRecord.getDirection().equals("outbound")) {
                outboundDdrRecord = ddrRecord;
            }
        }
        Assert.assertThat(
            outboundDdrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(addressToValidateForStatus, null)),
            Matchers.is(statusToValidate));
    }
}