package com.almende.dialog.adapter;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;

@Category(IntegrationTest.class)
public class RouteSMSIT extends TestFramework {

    private static final String simpleQuestion = "How are you?";

    @Before
    public void setup() throws Exception {

        super.setup();
        new DialogAgent().setDefaultProviderSettings(AdapterType.SMS, AdapterProviders.ROUTE_SMS);
    }

    @Test
    public void outBoundSMSCallStatusCheck() throws Exception {

        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
                                                          TEST_ACCOUNT_ID, "0612345678", "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.APPOINTMENT, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner(), null);
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

        //send an outbound sms with the apointment question
        outBoundSMSCallStatusCheck();
        //flush the log
        TestServlet.clearLogObject();
        //receive an inbound sms usinwg CM
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapterByAccount(TEST_ACCOUNT_ID,
            AdapterType.SMS.toString(), null);
        AdapterConfig smsAdapter = adapters.iterator().next();
        processInboundMessage("yup", remoteAddressVoice, "0612345678", smsAdapter);
        //The processed message must be sent using the same initial adapter
        List<Session> allSessions = Session.getAllSessions();
        Session session = allSessions.iterator().next();
        Assert.assertThat(session.getAdapterConfig().getProvider(), Matchers.is(AdapterProviders.ROUTE_SMS));
        //validate the log saved
        Assert.assertNotNull(TestServlet.getLogObject(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
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

        outBoundCall(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner(), null);
        //fetch sessions
        List<Session> allSessions = Session.getAllSessions();
        //sessions for the landline and the invalid numbers must be dropped
        Assert.assertThat(allSessions.size(), Matchers.is(1));
        for (Session session : allSessions) {
            Assert.assertThat(session.getRemoteAddress(), Matchers.not(invalidNumber));
            Assert.assertThat(session.getRemoteAddress(), Matchers.not(landlineNumber));
        }

        //fetch the sms ddr records
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, null, null, null,
                                                             null, null, null);
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
                "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner(), null);
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
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, null, null, null,
                                                             null, null, null);
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
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner(), null);

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
        testDDRStatusAndSessionExistence(remoteAddressVoice, CommunicationStatus.DELIVERED, "0614765801", 1);
        testDDRStatusAndSessionExistence("0614765801", CommunicationStatus.SENT, "0614765801", 1);

        messageId = TestServlet.getLogObject(PhoneNumberUtils.formatNumber("0614765801", null)).toString();
        //make a post delivery notification request for 0614765801
        performPOSTDeliveryStatusRequest("0614765801", messageId);
        //test the dd status
        testDDRStatusAndSessionExistence(remoteAddressVoice, CommunicationStatus.DELIVERED, null, 0);
        testDDRStatusAndSessionExistence("0614765801", CommunicationStatus.DELIVERED, null, 0);
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
     * of the given statueToValidate. The number of sessions currently in the
     * system must match the given sessionsToValidate
     * 
     * @param addressToValidateForStatus
     * @param statusToValidate
     * @param sessionsToValidate
     */
    private void testDDRStatusAndSessionExistence(String addressToValidateForStatus,
        CommunicationStatus statusToValidate, String addressToValidateForSession, int sessionsToValidate) {

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
        List<Session> allSessions = Session.getAllSessions();
        Assert.assertThat(allSessions.size(), Matchers.is(sessionsToValidate));
        if (sessionsToValidate > 0) {
            Session session = allSessions.iterator().next();
            Assert.assertThat(session.getRemoteAddress(),
                Matchers.is(PhoneNumberUtils.formatNumber(addressToValidateForSession, null)));
        }
    }
}