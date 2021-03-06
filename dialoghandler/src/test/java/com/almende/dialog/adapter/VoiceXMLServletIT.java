package com.almende.dialog.adapter;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.Log;
import com.almende.dialog.LogLevel;
import com.almende.dialog.Logger;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.jackson.JOM;
import com.askfast.commons.RestResponse;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.entity.DialogRequest;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.fasterxml.jackson.core.type.TypeReference;

@Category(IntegrationTest.class)
public class VoiceXMLServletIT extends TestFramework {

    protected static final String COMMENT_QUESTION_AUDIO = "http://audio.wav";
    protected static final String COMMENT_QUESTION_ID = "1";
    private DialogAgent dialogAgent = null;
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(VoiceXMLServletIT.class.getName());

    /**
     * this test is to check the bug which rethrows the same question when an
     * open question doesnt have an answer nor a timeout eventtype
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_WithOpenQuestion_MissingAnswerTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, url);

        //create session
        Session session = Session.createSession(adapterConfig, remoteAddressVoice);

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response newDialog = voiceXMLRESTProxy.getNewDialog("inbound", remoteAddressVoice, remoteAddressVoice,
            localFullAddressBroadsoft, null, uriInfo);
        HashMap<String, String> answerVariables = assertOpenQuestionWithDTMFType(newDialog.getEntity().toString());

        //answer the dialog
        Question retrivedQuestion = Question.fromURL(url, remoteAddressVoice, session);
        String mediaPropertyValue = retrivedQuestion.getMediaPropertyValue(MediumType.BROADSOFT,
            MediaPropertyKey.RETRY_LIMIT);

        Integer retryCount = Question.getRetryCount(answerVariables.get("sessionKey"));
        int i = 0;
        while (i++ < 10) {
            Response answerResponse = voiceXMLRESTProxy.answer(answerVariables.get("questionId"), null,
                answerVariables.get("answerInput"), answerVariables.get("sessionKey"), null, uriInfo);
            if (answerResponse.getEntity() != null) {
                if (answerResponse.getEntity()
                                  .toString()
                                  .equals(
                                      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                          + "<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">"
                                          + "<form><block><exit/></block></form></vxml>")) {
                    break;
                }
            }
            retryCount++;
        }
        assertTrue(retryCount != null);
        if (mediaPropertyValue != null) {
            assertTrue(retryCount < i);
            assertTrue(retryCount == Integer.parseInt(mediaPropertyValue));
        }
        else {
            assertTrue(retryCount <= i);
            assertEquals(new Integer(Question.DEFAULT_MAX_QUESTION_LOAD), retryCount);
        }
        //check all the ddrs created
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(ddrRecords.size(), 1);
        for (DDRRecord ddrRecord : ddrRecords) {
            assertEquals("inbound", ddrRecord.getDirection());
            assertEquals(adapterConfig.getFormattedMyAddress(), ddrRecord.getToAddress().keySet().iterator().next());
            assertEquals(PhoneNumberUtils.formatNumber(remoteAddressVoice, null), ddrRecord.getFromAddress());
            Object addressSessionKeyObject = ddrRecord.getAdditionalInfo().get(Session.SESSION_KEY);
            Map<String, String> addressSessionKey = JOM.getInstance().convertValue(addressSessionKeyObject,
                new TypeReference<Map<String, String>>() {
                });
            assertEquals(PhoneNumberUtils.formatNumber(remoteAddressVoice, null), addressSessionKey.keySet().iterator()
                                                                                                   .next());
            assertEquals(ddrRecord.getSessionKeys().iterator().next(),
                addressSessionKey.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        }
    }

    /**
     * Test to validate if wrong remote address given will not
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCall_RepeatedBroadsoftSubsciptionFailTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, url);

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));

        //used forcibly for Broadsoft.startCall() to throw an exception. 
        TestServlet.TEST_SERVLET_PATH += "test";
        VoiceXMLRESTProxy.dial(remoteAddressVoice, url, adapterConfig, TEST_ACCOUNT_ID, null,
            adapterConfig.getAccountType());
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(allDdrRecords.isEmpty(), Matchers.is(true));
        Session session = Session.getSessionByInternalKey(adapterConfig.getAdapterType(), adapterConfig.getMyAddress(),
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        assertThat(session, Matchers.nullValue());
    }

    /**
     * This test is used to simulate the situation when an outbound call is
     * triggered, but the corresponding ddrRecord is missing from the session
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCallMissingDDRTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESTION.name());

        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, url);
        adapterConfig.setXsiUser(localFullAddressBroadsoft);
        adapterConfig.setXsiSubscription(UUID.randomUUID().toString());
        adapterConfig.update();

        //setup some ddrPrices
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.8, "Test outgoing", UnitType.SECOND, null,
            null);

        //trigger an outbound call
        VoiceXMLRESTProxy.dial(remoteAddressVoice, url, adapterConfig, adapterConfig.getOwner(), null,
            adapterConfig.getAccountType());
        //fetch the session, assert that a ddrRecord is not attached still
        Session session = Session.getSessionByInternalKey(AdapterAgent.ADAPTER_TYPE_CALL, localFullAddressBroadsoft,
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        assertThat(session, notNullValue());
        assertThat(session.getDdrRecordId(), Matchers.notNullValue());

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        //mimick a fetch new dialog/ phone pickup
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response newDialog = voiceXMLRESTProxy.getNewDialog("outbound", remoteAddressVoice, remoteAddressVoice,
            localFullAddressBroadsoft, null, uriInfo);
        assertOpenQuestionWithDTMFType(newDialog.getEntity().toString());
        //a ddr must be attached to hte session
        session = Session.getSession(session.getKey());
        assertThat(session, Matchers.notNullValue());
        assertThat(session.getDdrRecordId(), Matchers.notNullValue());

        //hangup the call after 5 mins
        //send hangup ccxml with an answerTime
        adapterConfig.setXsiSubscription(UUID.randomUUID().toString());
        adapterConfig.update();

        String activeXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Event xmlns=\"http://schema.broadsoft.com/xsi-events\" " +
            "xmlns:xsi1=\"http://www.w3.org/2001/XMLSchema-instance\"><sequenceNumber>257</sequenceNumber><subscriberId>" +
            localFullAddressBroadsoft +
            "</subscriberId>" +
            "<applicationId>cc</applicationId><subscriptionId>" +
            adapterConfig.getXsiSubscription() +
            "</subscriptionId><eventData xsi1:type=\"xsi:CallEvent\" xmlns:xsi=" +
            "\"http://schema.broadsoft.com/xsi-events\"><eventName>CallSessionEvent</eventName><call><callId>callhalf-12914560105:1</callId><extTrackingId>" +
            "10669651:1</extTrackingId><personality>Originator</personality><callState>Active</callState><remoteParty><address>tel:" +
            remoteAddressVoice +
            "</address><callType>Network</callType></remoteParty><startTime>1401809063943</startTime>" +
            "<answerTime>1401809070192</answerTime><releaseTime>1401809370000</releaseTime></call></eventData></Event>";
        voiceXMLRESTProxy.receiveCCMessage(activeXML);

        //check that the ddr addres status is switched to RECEIVED
        DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), session.getAccountId());
        assertEquals(CommunicationStatus.RECEIVED,
            ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));

        String hangupXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Event xmlns=\"http://schema.broadsoft.com/xsi-events\" " +
            "xmlns:xsi1=\"http://www.w3.org/2001/XMLSchema-instance\"><sequenceNumber>257</sequenceNumber><subscriberId>" +
            localFullAddressBroadsoft +
            "</subscriberId>" +
            "<applicationId>cc</applicationId><subscriptionId>" +
            adapterConfig.getXsiSubscription() +
            "</subscriptionId><eventData xsi1:type=\"xsi:CallEvent\" xmlns:xsi=" +
            "\"http://schema.broadsoft.com/xsi-events\"><eventName>CallSessionEvent</eventName><call><callId>callhalf-12914560105:1</callId><extTrackingId>" +
            "10669651:1</extTrackingId><personality>Originator</personality><callState>Released</callState><releaseCause>Temporarily Unavailable</releaseCause>" +
            "<remoteParty><address>tel:" +
            remoteAddressVoice +
            "</address><callType>Network</callType></remoteParty><startTime>1401809063943</startTime>" +
            "<answerTime>1401809070192</answerTime><releaseTime>1401809370000</releaseTime></call></eventData></Event>";

        voiceXMLRESTProxy.receiveCCMessage(hangupXML);
        //fetch the ddrRecord again
        ddrRecord = ddrRecord.reload();
        assertThat(ddrRecord, Matchers.notNullValue());
        ddrRecord.setShouldGenerateCosts(true);
        ddrRecord.setShouldIncludeServiceCosts(true);
        assertThat(ddrRecord.getDuration(), Matchers.greaterThan(0L));
        assertThat(ddrRecord.getStart(), Matchers.is(1401809070192L));
        //check that the ddr addres status is switched to FINISHED
        assertEquals(CommunicationStatus.FINISHED,
            ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
    }

    /**
     * This test is used to simulate the situation when an outbound call is
     * triggered. Changed as on 8-June-2015. Logs are stored in the logger
     * agent. So no logs should be fetched correspond to this ddrRecordId
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCallCheckDDRHasNoLogsTest() throws Exception {

        //trigger an outbound call
        outboundPhoneCallMissingDDRTest();
        //fetch all the ddrRecords
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        //make sure that all the logs belong to atleast one ddrRecord
        //        int logsCount = 0;
        long startTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        boolean isDDRLogFound = false;
        for (DDRRecord ddrRecord : ddrRecords) {
            List<Log> logsForDDRRecord = Logger.find(TEST_ACCOUNT_ID, ddrRecord.getId(), null, null, null, null, null,
                null);
            for (Log log : logsForDDRRecord) {
                assertThat(log.getAccountId(), Matchers.is(ddrRecord.getAccountId()));
                assertThat(log.getAccountId(), Matchers.notNullValue());
                if (LogLevel.DDR.equals(log.getLevel())) {
                    isDDRLogFound = true;
                }
            }
        }
        assertThat(isDDRLogFound, Matchers.is(false));
        long endTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        log.info(String.format("Fetch by ddrRecord took: %s secs", (endTimestamp - startTimestamp) / 1000.0));

        //test the difference in timings to fetch all
        startTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        Logger.find(TEST_ACCOUNT_ID, null, null, null, null, null, null, null);
        endTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        log.info(String.format("Fetch by accountId took: %s secs", (endTimestamp - startTimestamp) / 1000.0));
    }

    /**
     * @Deprecated Every call that is triggered must have associated logs.
     *             Change as on 8-June-2015, logs are saved in teh logger agent.
     *             So this test is deprecated. Renamed the test to DDR logs must
     *             not be seen.
     * @throws Exception
     */
    @Test
    public void anyCallMustNotHaveADDRLogTypeTest() throws Exception {

        //trigger an outbound call
        outboundPhoneCallMissingDDRTest();
        //fetch all the ddrRecords
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        //make sure that all the logs belong to atleast one ddrRecord
        //        int logsCount = 0;
        long startTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        boolean isDDRLogFound = false;
        for (DDRRecord ddrRecord : ddrRecords) {
            List<Log> logsForDDRRecord = Logger.find(TEST_ACCOUNT_ID, ddrRecord.getId(), null, null, null, null, null,
                null);
            for (Log log : logsForDDRRecord) {
                assertThat(log.getAccountId(), Matchers.is(ddrRecord.getAccountId()));
                assertThat(log.getAccountId(), Matchers.notNullValue());
                if (LogLevel.DDR.equals(log.getLevel())) {
                    isDDRLogFound = true;
                }
            }
        }
        assertThat(isDDRLogFound, Matchers.is(false));
        long endTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        log.info(String.format("Fetch by ddrRecord took: %s secs", (endTimestamp - startTimestamp) / 1000.0));

        //test the difference in timings to fetch all
        startTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        Logger.find(TEST_ACCOUNT_ID, null, null, null, null, null, null, null);
        endTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        log.info(String.format("Fetch by accountId took: %s secs", (endTimestamp - startTimestamp) / 1000.0));
    }

    /**
     * Performs an outbound call request with Broadsoft. Test it with a switch
     * to Voxeo and test if everything works normally
     */
    @Test
    public void outboundWithGlobalSwitchOnTest() throws Exception {

        dialogAgent = Mockito.mock(DialogAgent.class);
        Mockito.when(dialogAgent.getGlobalProviderCredentials()).thenReturn(null);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESTION.name());

        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, url);
        adapterConfig.setXsiUser(localFullAddressBroadsoft);
        adapterConfig.setXsiSubscription(TEST_PUBLIC_KEY);
        adapterConfig.update();

        //perform an outbound call
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, "");
        Mockito.when(
            dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null, adapterConfig.getConfigId(),
                adapterConfig.getOwner(), "", adapterConfig.getAccountType())).thenCallRealMethod();
        HashMap<String, String> result = dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null,
            adapterConfig.getConfigId(), adapterConfig.getOwner(), "", adapterConfig.getAccountType());
        assertTrue(result != null);
        assertTrue(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)) != null);
        Session.drop(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));

        //set switch related test info
        String testMyAddress = "0854881001";

        Map<AdapterProviders, Map<String, AdapterConfig>> globalAdapterCredentials = new HashMap<AdapterProviders, Map<String, AdapterConfig>>();
        HashMap<String, AdapterConfig> credentials = new HashMap<String, AdapterConfig>();
        AdapterConfig adapterCredentials = new AdapterConfig();
        adapterCredentials.setAccessToken("testTest");
        adapterCredentials.setAccessTokenSecret("testTestSecret");
        adapterCredentials.setMyAddress(testMyAddress);
        adapterCredentials.setAddress(testMyAddress);
        adapterCredentials.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO);
        credentials.put(DialogAgent.ADAPTER_CREDENTIALS_GLOBAL_KEY, adapterCredentials);
        globalAdapterCredentials.put(AdapterProviders.TWILIO, credentials);

        //mock the Context
        Mockito.when(dialogAgent.getGlobalProviderCredentials()).thenReturn(globalAdapterCredentials);
        //switch calling adapter globally
        Mockito.when(dialogAgent.getGlobalAdapterSwitchSettingsForType(AdapterType.CALL)).thenReturn(
            AdapterProviders.TWILIO);
        Mockito.when(dialogAgent.getApplicationId()).thenReturn(UUID.randomUUID().toString());

        //initiate outbound request again

        result = dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null,
            adapterConfig.getConfigId(), adapterConfig.getOwner(), "", adapterConfig.getAccountType());
        assertTrue(result != null);
        assertTrue(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)) != null);
        Session session = Session.getSession(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertTrue(session != null);
        assertThat(session.getLocalAddress(), Matchers.not(testMyAddress));
        assertThat(session.getLocalAddress(), Matchers.is(adapterConfig.getMyAddress()));
        assertEquals(AdapterProviders.TWILIO.toString(), session.getAllExtras().get(AdapterConfig.ADAPTER_PROVIDER_KEY)
                                                                .toString());
        assertEquals(AdapterType.CALL.toString().toLowerCase(), session.getType().toLowerCase());
    }

    /**
     * Performs an outbound call request with Broadsoft. Test it with a switch
     * to Voxeo and test if everything works normally
     */
    @Test
    public void outboundWithSpecificAdapterSwitchOnTest() throws Exception {

        //perform a global switch test
        outboundWithGlobalSwitchOnTest();

        //fetch the session
        Session session = Session.getSessionByInternalKey(AdapterAgent.ADAPTER_TYPE_CALL, localFullAddressBroadsoft,
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        //add a specific adapter switch
        String testMyAddress = "0854881002";
        Map<AdapterProviders, Map<String, AdapterConfig>> globalSwitchProviderCredentials = dialogAgent.getGlobalProviderCredentials();
        Map<String, AdapterConfig> credentials = globalSwitchProviderCredentials.get(AdapterProviders.TWILIO);
        AdapterConfig adapterCredentials = new AdapterConfig();
        adapterCredentials.setAccessToken("testTest");
        adapterCredentials.setMyAddress(testMyAddress);
        adapterCredentials.setAddress(testMyAddress);
        adapterCredentials.setAccessTokenSecret("testTestSecret");
        adapterCredentials.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO);
        credentials.put(session.getAdapterID(), adapterCredentials);
        globalSwitchProviderCredentials.put(AdapterProviders.TWILIO, credentials);

        //mock the Context
        Mockito.when(dialogAgent.getGlobalProviderCredentials()).thenReturn(globalSwitchProviderCredentials);

        //initiate outbound request again
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESTION.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, "");
        HashMap<String, String> result = dialogAgent.outboundCallWithMap(addressMap, null, null, null, null, url, null,
            session.getAdapterID(), session.getAdapterConfig().getOwner(), "", session.getAccountType());
        assertTrue(result != null);
        assertTrue(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)) != null);
        session = Session.getSession(result.get(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertTrue(session != null);
        assertThat(session.getLocalAddress(), Matchers.not(testMyAddress));
        assertThat(session.getLocalAddress(), Matchers.is(localFullAddressBroadsoft));
        assertEquals(AdapterType.CALL.toString().toLowerCase(), session.getType().toLowerCase());
        assertEquals(AdapterProviders.TWILIO.toString().toLowerCase(),
            session.getAllExtras().get(AdapterConfig.ADAPTER_PROVIDER_KEY).toString().toLowerCase());
    }

    /**
     * Perform an outbound call, receive a callback on an answer, make sure
     * sensitive information are not seen in the payload
     */
    @Test
    public void eventCallBackPayloadTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESTION.name());

        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, url);
        adapterConfig.setXsiUser(localFullAddressBroadsoft);
        adapterConfig.setXsiSubscription(TEST_PUBLIC_KEY);
        adapterConfig.update();

        //trigger an outbound call
        String sessionKey1 = VoiceXMLRESTProxy.dial(remoteAddressVoice, url, adapterConfig, adapterConfig.getOwner(),
            null, adapterConfig.getAccountType());
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        voiceXMLRESTProxy.timeout(UUID.randomUUID().toString(), sessionKey1, null);

        //validate that the session has it
        Session session = Session.getSession(sessionKey1);
        assertThat(session.getAllExtras().get(AdapterConfig.ADAPTER_PROVIDER_KEY),
            Matchers.is(AdapterProviders.BROADSOFT.toString()));

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        //mimick a fetch new dialog/ phone pickup
        String sessionKey2 = VoiceXMLRESTProxy.dial(remoteAddressVoice, url, adapterConfig, adapterConfig.getOwner(),
            null, adapterConfig.getAccountType());
        voiceXMLRESTProxy.answer(UUID.randomUUID().toString(), null, "1", sessionKey2, null, uriInfo);

        //validate that the session has it
        session = Session.getSession(sessionKey2);
        assertThat(session.getAllExtras().get(AdapterConfig.ADAPTER_PROVIDER_KEY),
            Matchers.is(AdapterProviders.BROADSOFT.toString()));
    }

    /**
     * This test is to check if the inbound functionality works correctly for a
     * dialog with the wrong credentials for the secured url access
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_WithSecuredDialogAccessFailTest() throws Exception {

        Response securedDialogResponse = performSecuredInboundCall("wrongUserName", "testpassword", null, null, null);
        Document doc = getXMLDocumentBuilder(securedDialogResponse.getEntity().toString());
        String ttsURL = doc.getElementsByTagName("audio").item(0).getAttributes().getNamedItem("src").getTextContent();
        URIBuilder uriBuilder = new URIBuilder(ttsURL);
        for (NameValuePair queryParams : uriBuilder.getQueryParams()) {
            if (queryParams.getName().equals("text")) {
                assertThat(queryParams.getValue(), Matchers.is("Er is iets mis gegaan met het ophalen van uw dialoog"));
                continue;
            }
            else if (queryParams.getName().equals("codec")) {
                assertThat(queryParams.getValue(), Matchers.is("WAV"));
                continue;
            }
            else if (queryParams.getName().equals("speed")) {
                assertThat(queryParams.getValue(), Matchers.is("0"));
                continue;
            }
            else if (queryParams.getName().equals("format")) {
                assertThat(queryParams.getValue(), Matchers.is("8khz_8bit_mono"));
                continue;
            }
            else if (queryParams.getName().equals("type")) {
                assertThat(queryParams.getValue(), Matchers.is(".wav"));
                continue;
            }
            else if (queryParams.getName().equals("lang")) {
                assertThat(queryParams.getValue(), Matchers.is("nl-nl"));
                continue;
            }
            else if (queryParams.getName().equals("askFastAccountId")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_ACCOUNT_ID));
                continue;
            }
            assertTrue(String.format("query not found: %s=%s", queryParams.getName(), queryParams.getValue()), false);
        }
    }

    /**
     * This test is to check if the inbound functionality works for a dialog
     * with the right credentials for the secured url access
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_WithSecuredDialogAccessSuccessTest() throws Exception {

        Response securedDialogResponse = performSecuredInboundCall("testuserName", "testpassword", null, null, null);
        assertTrue(securedDialogResponse != null);
        assertOpenQuestionWithDTMFType(securedDialogResponse.getEntity().toString());
    }

    /**
     * This test is to check if the inbound functionality works for a dialog
     * with the right credentials for the secured url access, but no ddr records
     * are created when a test flag is set to true
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_WithSecuredDialogWithTestFlagNoDDRTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.INCOMING_COMMUNICATION_COST, 0.1, "Test incoming costs", UnitType.MINUTE,
            null, null);
        Response securedDialogResponse = performSecuredInboundCall("testuserName", "testpassword", null, null, false);
        assertTrue(securedDialogResponse != null);
        assertOpenQuestionWithDTMFType(securedDialogResponse.getEntity().toString());

        //validate that ddr records are created when isTest is set to false
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        Assert.assertThat(ddrRecords.size(), Matchers.equalTo(1));

        //trigger a second incoming call with test flag to be true. flush all sessions, adapters and ddrRecords
        setup();

        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.INCOMING_COMMUNICATION_COST, 0.1, "Test incoming costs", UnitType.MINUTE,
            null, null);
        securedDialogResponse = performSecuredInboundCall("testuserName", "testpassword", null, null, true);
        assertTrue(securedDialogResponse != null);
        assertOpenQuestionWithDTMFType(securedDialogResponse.getEntity().toString());

        //validate that ddr records are created when isTest is set to false
        ddrRecords = getAllDdrRecords(TEST_PUBLIC_KEY);
        Assert.assertThat(ddrRecords.size(), Matchers.equalTo(0));
    }

    /**
     * This test is to check if the outbound functionality works correctly for a
     * dialog with the wrong credentials for the secured url access
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCall_WithSecuredDialogAccessFailTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        url = ServerUtils.getURLWithQueryParams(url, "secured", "true");
        Dialog dialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);
        dialog.setUserName("wrongUserName");
        dialog.setPassword("testpassword");
        dialog.setUseBasicAuth(true);
        dialog.storeOrUpdate();
        boolean exceptionThrown = false;
        try {
            performSecuredOutBoundCall(dialog);
        }
        catch (Exception e) {
            exceptionThrown = true;
        }
        assertEquals(true, exceptionThrown);
        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(0));
    }

    /**
     * This test is to check if the inbound functionality works for a dialog
     * with the right credentials for the secured url access along with TTSInfo
     * given in the dialog
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_WithSecuredDialogAndTTSInfoTest() throws Exception {

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        ttsInfo.setVoiceUsed("testtest");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.SIMPLE_COMMENT.name());
        String message = "How are you doing? today";
        url = ServerUtils.getURLWithQueryParams(url, "question", message);
        Response securedDialogResponse = performSecuredInboundCall("testuserName", "testpassword", ttsInfo, url, null);
        assertTrue(securedDialogResponse != null);
        //make sure that the tts source generated has a service and voice
        Document doc = getXMLDocumentBuilder(securedDialogResponse.getEntity().toString());
        String ttsURL = doc.getElementsByTagName("audio").item(0).getAttributes().getNamedItem("src").getTextContent();
        URIBuilder uriBuilder = new URIBuilder(ttsURL);
        for (NameValuePair queryParams : uriBuilder.getQueryParams()) {
            if (queryParams.getName().equals("text")) {
                assertThat(queryParams.getValue(), Matchers.is(message));
                continue;
            }
            else if (queryParams.getName().equals("codec")) {
                assertThat(queryParams.getValue(), Matchers.is("WAV"));
                continue;
            }
            else if (queryParams.getName().equals("speed")) {
                assertThat(queryParams.getValue(), Matchers.is("0"));
                continue;
            }
            else if (queryParams.getName().equals("format")) {
                assertThat(queryParams.getValue(), Matchers.is("8khz_8bit_mono"));
                continue;
            }
            else if (queryParams.getName().equals("type")) {
                assertThat(queryParams.getValue(), Matchers.is(".wav"));
                continue;
            }
            else if (queryParams.getName().equals("lang")) {
                assertThat(queryParams.getValue(), Matchers.is("nl-nl"));
                continue;
            }
            else if (queryParams.getName().equals("voice")) {
                assertThat(queryParams.getValue(), Matchers.is("testtest"));
                continue;
            }
            else if (queryParams.getName().equals("service")) {
                assertThat(queryParams.getValue(), Matchers.is("ACAPELA"));
                continue;
            }
            else if (queryParams.getName().equals("askFastAccountId")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_ACCOUNT_ID));
                continue;
            }
            assertTrue(String.format("query not found: %s=%s", queryParams.getName(), queryParams.getValue()), false);
        }
    }

    @Test
    public void inboundPhoneCall_ForTTSServiceCostProcessingTest() throws Exception {

        phoneCall_ForTTSServiceCostProcessingTest("inbound");
    }

    @Test
    public void outboundPhoneCall_ForTTSServiceCostProcessingTest() throws Exception {

        phoneCall_ForTTSServiceCostProcessingTest("outbound");
    }

    /**
     * This test is to check if tts service charges are attached to the inbound
     * functionality when the ttsAccountId is found. No tts costs must be seen
     * 
     * @throws Exception
     */
    public void phoneCall_ForTTSServiceCostProcessingTest(String direction) throws Exception {

        DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
        ddrRecordAgent.createDDRPriceWithNewDDRType("TTS service costs", DDRTypeCategory.TTS_SERVICE_COST.name(), null,
            null, 0.01, null, null, null, null, null, null, null);
        ddrRecordAgent.createDDRPriceWithNewDDRType("TTS service costs", DDRTypeCategory.TTS_COST.name(), null, null,
            0.01, null, null, null, null, null, null, null);

        String ttsAccountId = UUID.randomUUID().toString();
        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        ttsInfo.setVoiceUsed("testtest");
        ttsInfo.setTtsAccountId(ttsAccountId);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.SIMPLE_COMMENT.name());
        String message = "How are you doing? today";
        url = ServerUtils.getURLWithQueryParams(url, "question", message);
        String securedDialogResponse = null;
        if (direction.equals("inbound")) {
            securedDialogResponse = performSecuredInboundCall("testuserName", "testpassword", ttsInfo, url, null).getEntity()
                                                                                                                 .toString();
        }
        else {
            Dialog dialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);
            dialog.setUserName("testuserName");
            dialog.setPassword("testpassword");
            dialog.setUseBasicAuth(true);
            dialog.setTtsInfo(ttsInfo);
            dialog.storeOrUpdate();
            securedDialogResponse = performSecuredOutBoundCall(dialog);
            //mock the Context
            UriInfo uriInfo = Mockito.mock(UriInfo.class);
            Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
            new VoiceXMLRESTProxy().getNewDialog("outbound", remoteAddressVoice, remoteAddressVoice,
                localFullAddressBroadsoft, null, uriInfo);
        }
        assertTrue(securedDialogResponse != null);
        //check if ddr is created for ttsprocessing
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        int ttsServiceChargesAttached = 0;
        for (DDRRecord ddrRecord : ddrRecords) {
            Assert.assertFalse(ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.TTS_COST));
            if (ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.TTS_SERVICE_COST)) {
                ttsServiceChargesAttached++;
            }
        }
        assertEquals(1, ttsServiceChargesAttached);
    }

    @Test
    public void outboundPhoneCall_InvalidReferralTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();
        String invalidNumber = "+3161234567";
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.REFERRAL.name());
        url = ServerUtils.getURLWithQueryParams(url, "address", invalidNumber); //invalid address
        url = ServerUtils.getURLWithQueryParams(url, "question", "Hello...");

        //create a dialog
        Dialog dialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);

        performSecuredOutBoundCall(dialog);
        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response newDialog = voiceXMLRESTProxy.getNewDialog("outbound", remoteAddressVoice, remoteAddressVoice,
            localFullAddressBroadsoft, null, uriInfo);
        assertThat(newDialog.getEntity().toString(), Matchers.not(Matchers.containsString(invalidNumber)));
        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(0));
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(ddrRecords.size(), Matchers.is(1));
        int ddrInfoCount = 0;
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        for (String infoKey : ddrRecord.getAdditionalInfo().keySet()) {
            if (invalidNumber.equals(infoKey)) {
                assertThat(ddrRecord.getAdditionalInfo().get(infoKey).toString(), Matchers.is("Invalid address"));
                ddrInfoCount++;
            }
        }
        assertThat(ddrInfoCount, Matchers.is(1));
    }

    /**
     * Test to check if the ddr records are linked when a referral is triggered
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_ReferralHasLinkedDDRRecordsTest() throws Exception {

        String referralNumber = "0611111111";
        new DDRRecordAgent().generateDefaultDDRTypes();
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.REFERRAL.name());
        url = ServerUtils.getURLWithQueryParams(url, "address", referralNumber);
        url = ServerUtils.getURLWithQueryParams(url, "question", "Hello...");

        //perform a secured inbound call
        performSecuredInboundCall(null, null, null, url, false);

        List<Session> allSessions = Session.getAllSessions();
        assertThat(allSessions.size(), Matchers.is(2));

        //fetch the parent session (initial call that triggers the inbound call)
        Session parentSession = null;
        for (Session session : allSessions) {
            if (session.getDirection().equals("inbound")) {
                parentSession = session;
            }
        }
        assertThat(parentSession, Matchers.notNullValue());
        DDRRecord parentDDRRecord = parentSession.getDDRRecord();
        assertThat(parentDDRRecord, Matchers.notNullValue());

        //assert that the parent ddrRecord has child ddrRecords linked
        assertThat(parentDDRRecord.getChildIds().size(), Matchers.is(1));
        ArrayList<DDRRecord> childDDRRecords = parentDDRRecord.getChildDDRRecords();
        assertThat(childDDRRecords.size(), Matchers.is(1));
        assertThat(childDDRRecords.iterator().next().getParentId(), Matchers.is(parentDDRRecord.getId()));
    }

    /**
     * This test is to check if tts charges are attached to the inbound
     * functionality when the ttsAccountId is not found. No tts service costs
     * must be seen
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_ForTTSCostProcessingTest() throws Exception {

        DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
        ddrRecordAgent.createDDRPriceWithNewDDRType("TTS service costs", DDRTypeCategory.TTS_SERVICE_COST.name(), null,
            null, 0.01, null, null, null, null, null, null, null);
        ddrRecordAgent.createDDRPriceWithNewDDRType("TTS service costs", DDRTypeCategory.TTS_COST.name(), null, null,
            0.01, null, null, null, null, null, null, null);

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        ttsInfo.setVoiceUsed("testtest");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.SIMPLE_COMMENT.name());
        String message = "How are you doing? today";
        url = ServerUtils.getURLWithQueryParams(url, "question", message);
        Response securedDialogResponse = performSecuredInboundCall("testuserName", "testpassword", ttsInfo, url, null);
        assertTrue(securedDialogResponse != null);
        //check if ddr is created for ttsprocessing
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        int ttsChargesAttached = 0;
        for (DDRRecord ddrRecord : ddrRecords) {
            Assert.assertFalse(ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.TTS_SERVICE_COST));
            if (ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.TTS_COST)) {
                ttsChargesAttached++;
            }
        }
        assertEquals(1, ttsChargesAttached);
    }

    @Test
    public void ttsUrlTest() throws Exception {

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        ttsInfo.setVoiceUsed("testtest");

        AdapterConfig adapterConfig = createBroadsoftAdapter();
        Session session = Session.createSession(adapterConfig, remoteAddressVoice);
        String ttsurl = ServerUtils.getTTSURL(ttsInfo, "simple test", session);
        assertThat(ttsurl, Matchers.not(Matchers.containsString("&amp")));
    }

    /**
     * This test is to check if the outbound functionality works for a dialog
     * with the right credentials for the secured url access
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCall_WithSecuredDialogAccessSuccessTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        url = ServerUtils.getURLWithQueryParams(url, "secured", "true");
        Dialog dialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);
        dialog.setUserName("testuserName");
        dialog.setPassword("testpassword");
        dialog.setUseBasicAuth(true);
        dialog.storeOrUpdate();
        String sessionKey = performSecuredOutBoundCall(dialog);
        assertThat(sessionKey, Matchers.notNullValue());
    }

    /**
     * test if a wrong dtmf entry repeats a question when the dtmf in the
     * answers are of wrong order. Answeres with dtmfKey://3 and 4 must not take
     * 1 as an answer and must repeat the question
     * 
     * @throws Exception
     */
    @Test
    public void inboundCallWithWrongOrderDtmfTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.APPOINTMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");
        url = ServerUtils.getURLWithQueryParams(url, "byDtmf", "true");
        url = ServerUtils.getURLWithQueryParams(url, "yesDtmf", "3");
        url = ServerUtils.getURLWithQueryParams(url, "noDtmf", "4");
        Dialog dialog = Dialog.createDialog("Test dialog", url, TEST_PUBLIC_KEY);

        //create mail adapter
        AdapterConfig adapterConfig = createBroadsoftAdapter();
        adapterConfig.setDialogId(dialog.getId());
        adapterConfig.update();

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        voiceXMLRESTProxy.getNewDialog("inbound", remoteAddressVoice, remoteAddressVoice, localFullAddressBroadsoft,
            null, uriInfo);

        //fetch current sesison
        Session session = Session.getSessionByInternalKey(adapterConfig.getAdapterType(), adapterConfig.getMyAddress(),
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        assertNotNull(session);
        //trigger answer 
        Response answer = voiceXMLRESTProxy.answer("1", null, "1", session.getKey(), null, uriInfo);
        assertTrue(
            String.format("%s doesnt contain %s", answer.getEntity().toString(), TestServlet.APPOINTMENT_MAIN_QUESTION),
            answer.getEntity().toString().contains(URLEncoder.encode(TestServlet.APPOINTMENT_MAIN_QUESTION, "UTF-8")));
    }

    /**
     * test if a wrong dtmf entry repeats a question when the dtmf is a digit
     * 
     * @throws Exception
     */
    @Test
    public void inboundCallWith12AnswersPressed1DtmfTest() throws Exception {

        inboundCallWithDTMFAsAnswerTest("1");
    }

    /**
     * test if a wrong dtmf entry repeats a question when the dtmf is a digit
     * 
     * @throws Exception
     */
    @Test
    public void inboundCallWith12AnswersPressed9DtmfTest() throws Exception {

        inboundCallWithDTMFAsAnswerTest("9");
    }

    /**
     * test if a wrong dtmf entry repeats a question when the dtmf is a digit
     * 
     * @throws Exception
     */
    @Test
    public void inboundCallWith12AnswersPressed0DtmfTest() throws Exception {

        inboundCallWithDTMFAsAnswerTest("0");
    }

    /**
     * test if a wrong dtmf entry repeats a question when the dtmf is a digit
     * 
     * @throws Exception
     */
    @Test
    public void inboundCallWith12AnswersPressedSharpDtmfTest() throws Exception {

        inboundCallWithDTMFAsAnswerTest("#");
    }

    /**
     * test if a wrong dtmf entry repeats a question when the dtmf is a digit
     * 
     * @throws Exception
     */
    @Test
    public void inboundCallWith12AnswersPressedStarDtmfTest() throws Exception {

        inboundCallWithDTMFAsAnswerTest("*");
    }

    /**
     * Test if the
     * {@link DialogAgent#outboundCallWithDialogRequest(com.askfast.commons.entity.DialogRequest)}
     * gives an error code if the question is not fetched by the dialog agent
     * 
     * @throws UnsupportedEncodingException
     */
    @Test
    public void outboundCallWithoutQuestionTest() throws Exception {

        dialogAgent = new DialogAgent();
        //setup bad question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH + "wrongURL", "questionType",
            QuestionInRequest.TWELVE_INPUT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");

        //create mail adapter
        AdapterConfig adapterConfig = createBroadsoftAdapter();

        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.SECOND, AdapterType.CALL,
            null);

        DialogRequest details = new DialogRequest();
        details.setAccountID(adapterConfig.getOwner());
        details.setAdapterID(adapterConfig.getConfigId());
        details.setAddress(remoteAddressVoice);
        details.setBearerToken(UUID.randomUUID().toString());
        details.setMethod("outboundCall");
        details.setUrl(url);
        RestResponse outboundCallResponse = dialogAgent.outboundCallWithDialogRequest(details);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), outboundCallResponse.getCode());
        assertThat(outboundCallResponse.getMessage(), Matchers.is(DialogAgent.getQuestionNotFetchedMessage(url)));

        //verify that the session is not saved
        assertEquals(0, Session.getAllSessions().size());
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(1, ddrRecords.size());
        assertEquals(CommunicationStatus.ERROR,
            ddrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertEquals(1, ddrRecords.iterator().next().getStatusPerAddress().size());
    }

    /**
     * Test if the
     * {@link DialogAgent#outboundCallWithDialogRequest(com.askfast.commons.entity.DialogRequest)}
     * gives an error code if the question is fetched by the dialog agent but
     * the telephone numbers are a mix of valid and invalid numbers
     * 
     * @throws UnsupportedEncodingException
     */
    @Test
    public void outboundCallWithQuestionInvalidAndValidAddressTest() throws Exception {

        dialogAgent = dialogAgent != null ? dialogAgent : new DialogAgent();
        //setup bad question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.TWELVE_INPUT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");

        //create mail adapter
        AdapterConfig adapterConfig = createBroadsoftAdapter();

        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.SECOND, AdapterType.CALL,
            null);

        DialogRequest details = new DialogRequest();
        details.setAccountID(adapterConfig.getOwner());
        details.setAdapterID(adapterConfig.getConfigId());
        details.setAddressList(Arrays.asList("0611223", remoteAddressVoice));
        details.setBearerToken(UUID.randomUUID().toString());
        details.setUrl(url);
        RestResponse outboundCallResponse = dialogAgent.outboundCallWithDialogRequest(details);
        assertEquals(Status.CREATED.getStatusCode(), outboundCallResponse.getCode());

        //verify that the session is not saved
        assertEquals(1, Session.getAllSessions().size());
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(1, ddrRecords.size());
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        assertEquals(CommunicationStatus.ERROR,
            ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber("0611223", null)));
        assertEquals(CommunicationStatus.SENT,
            ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertEquals(2, ddrRecords.iterator().next().getStatusPerAddress().size());

    }
    
    /**
     * CHeck if an outbound call is not triggered for a number that is added to blacklist group
     * @throws Exception 
     */
    @Test
    public void outboundCallNotTriggeredForBlackListNumberTest() throws Exception {

        //create broadsoft adapter
        AdapterConfig adapterConfig = createBroadsoftAdapter();
        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.SECOND, AdapterType.CALL,
            null);
        //trigger call
        triggerOutboundCallForAddresses(Arrays.asList("0611223", remoteAddressVoice), Arrays.asList(remoteAddressVoice),
            adapterConfig, null);

        //validate that no sessions are created. 
        assertThat(Session.getAllSessions().size(), Matchers.is(0));
        //validate ddr records for Rejected status
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(allDdrRecords.size(), Matchers.is(1));
        assertThat(allDdrRecords.iterator().next().getStatusForAddress(
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null)), Matchers.is(CommunicationStatus.REJECTED));
    }

    /**
     * Test if the
     * {@link DialogAgent#outboundCallWithDialogRequest(com.askfast.commons.entity.DialogRequest)}
     * gives an error code if the question is fetched by the dialog agent but
     * the telephone number is invalid
     * 
     * @throws UnsupportedEncodingException
     */
    @Test
    public void outboundCallWithQuestionInvalidAddressTest() throws Exception {

        dialogAgent = new DialogAgent();
        //setup bad question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.TWELVE_INPUT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");

        //create mail adapter
        AdapterConfig adapterConfig = createBroadsoftAdapter();

        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.SECOND, AdapterType.CALL,
            null);

        DialogRequest details = new DialogRequest();
        details.setAccountID(adapterConfig.getOwner());
        details.setAdapterID(adapterConfig.getConfigId());
        details.setAddress("0611223"); //invalid address
        details.setBearerToken(UUID.randomUUID().toString());
        details.setUrl(url);
        RestResponse outboundCallResponse = dialogAgent.outboundCallWithDialogRequest(details);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), outboundCallResponse.getCode());

        //verify that the session is not saved
        assertEquals(0, Session.getAllSessions().size());
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertEquals(1, ddrRecords.size());
        assertEquals(CommunicationStatus.ERROR,
            ddrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber("0611223", null)));
        assertEquals(1, ddrRecords.iterator().next().getStatusPerAddress().size());
        //no session must be created
        assertEquals(0, Session.getAllSessions().size());
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

        String sharedAccountId = UUID.randomUUID().toString();

        //create POST PAID CALL adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, null);
        adapterConfig.setAccountType(AccountType.POST_PAID);
        adapterConfig.addAccount(sharedAccountId);
        adapterConfig.update();

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);

        //trigger an outbound call
        String sessionKey = VoiceXMLRESTProxy.dial(remoteAddressVoice, url, adapterConfig, sharedAccountId, null,
            AccountType.TRIAL);
        //process the call
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response dialogResponse = voiceXMLRESTProxy.getNewDialog("outbound", remoteAddressVoice, remoteAddressVoice,
            localFullAddressBroadsoft, null, uriInfo);

        //check the dialogResponse. This must have a link to the trial audio
        Assert.assertThat(dialogResponse.getEntity().toString(), Matchers.containsString(String.format(
            "<audio src=\"http://localhost:%s/dialoghandler/en_trial_message.wav\"/>", jettyPort)));

        //check the session and validate the question for the trial audio
        Session session = Session.getSession(sessionKey);
        Assert.assertThat(session.getAccountType(), Matchers.is(AccountType.TRIAL));
    }

    /**
     * check if a trial message is not played when an inbound call is initiated from
     * a trial account sharing an adapter with POST PAID type owner account.
     * 
     * @throws Exception
     */
    @Test
    public void trialMessageNotPlayedWithSharingPostPaidAccountInboundTest() throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        
        //create a dialog
        dialogAgent = dialogAgent != null ? dialogAgent : new DialogAgent();
        dialogAgent.createDialog(TEST_PUBLIC_KEY, "Test secured dialog", url);
        Dialog dialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);
        dialog.storeOrUpdate();
        
        String sharedAccountId = UUID.randomUUID().toString();
        //create POST PAID CALL adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, null);
        adapterConfig.setAccountType(AccountType.POST_PAID);
        adapterConfig.addAccount(sharedAccountId);
        adapterConfig.setDialogId(dialog.getId());
        adapterConfig.update();

        //trigger inbound call
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response dialogResponse = voiceXMLRESTProxy.getNewDialog("inbound", remoteAddressVoice, remoteAddressVoice,
            localFullAddressBroadsoft, null, uriInfo);

        //check the dialogResponse. This must have a link to the trial audio
        Assert.assertThat(dialogResponse.getEntity().toString(),
            Matchers.not(Matchers.containsString(String.format("trial_message\"/>", jettyPort))));

        //check the session and validate the question for the trial audio
        Session session = Session.getSessionByInternalKey("call", localFullAddressBroadsoft,
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        Assert.assertThat(session.getAccountType(), Matchers.not(AccountType.TRIAL));
    }

    /**
     * test if a wrong dtmf entry repeats a question when the dtmf in the
     * answers are of wrong order. Answeres with dtmfKey://3 and 4 must not take
     * 1 as an answer and must repeat the question
     * 
     * @throws Exception
     */
    private void inboundCallWithDTMFAsAnswerTest(String answerDtmf) throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.TWELVE_INPUT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");
        Dialog dialog = Dialog.createDialog("Test dialog", url, TEST_PUBLIC_KEY);

        //create mail adapter
        AdapterConfig adapterConfig = createBroadsoftAdapter();
        adapterConfig.setDialogId(dialog.getId());
        adapterConfig.update();

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        voiceXMLRESTProxy.getNewDialog("inbound", remoteAddressVoice, remoteAddressVoice, localFullAddressBroadsoft,
            null, uriInfo);

        //fetch current sesison
        Session session = Session.getSessionByInternalKey(adapterConfig.getAdapterType(), adapterConfig.getMyAddress(),
            PhoneNumberUtils.formatNumber(remoteAddressVoice, null));
        assertNotNull(session);
        //trigger answer 
        Response answer = voiceXMLRESTProxy.answer(answerDtmf, null, answerDtmf, session.getKey(), null, uriInfo);
        assertTrue(String.format("%s doesnt contain %s", answer.getEntity().toString(), "You pressed: " + answerDtmf),
            answer.getEntity().toString().contains(URLEncoder.encode("You pressed: " + answerDtmf, "UTF-8")));
    }

    /**
     * @throws UnsupportedEncodingException
     * @throws Exception
     * @throws URISyntaxException
     */
    private Response performSecuredInboundCall(String username, String password, TTSInfo ttsInfo, String url,
        Boolean isTest) throws Exception {

        if (url == null) {
            url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
            url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
            url = ServerUtils.getURLWithQueryParams(url, "secured", "true");
        }

        //create a dialog
        dialogAgent = dialogAgent != null ? dialogAgent : new DialogAgent();
        dialogAgent.createDialog(TEST_PUBLIC_KEY, "Test secured dialog", url);
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);
        createDialog.setUserName(username);
        createDialog.setPassword(password);
        createDialog.setUseBasicAuth(true);
        createDialog.setTtsInfo(ttsInfo);
        createDialog.storeOrUpdate();

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, url);
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        //mock the Context
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        return voiceXMLRESTProxy.getNewDialog("inbound", remoteAddressVoice, remoteAddressVoice,
            localFullAddressBroadsoft, isTest, uriInfo);
    }

    /**
     * @throws UnsupportedEncodingException
     * @throws Exception
     * @throws URISyntaxException
     */
    private String performSecuredOutBoundCall(Dialog dialog) throws UnsupportedEncodingException, Exception,
        URISyntaxException {

        //create a dialog
        dialogAgent = dialogAgent != null ? dialogAgent : new DialogAgent();
        //create CALL adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.BROADSOFT,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft, null);
        //trigger an outbound call
        return VoiceXMLRESTProxy.dial(remoteAddressVoice, dialog.getId(), adapterConfig, adapterConfig.getOwner(),
            null, adapterConfig.getAccountType());
    }

    /**
     * @param result
     * @throws Exception
     */
    protected HashMap<String, String> assertOpenQuestionWithDTMFType(String result) throws Exception {

        HashMap<String, String> variablesForAnswer = new HashMap<String, String>();

        Document doc = getXMLDocumentBuilder(result);
        Node vxml = doc.getFirstChild();
        Node answerInputNode = vxml.getChildNodes().item(0);
        Node questionIdNode = vxml.getChildNodes().item(1);
        Node sessionKeyNode = vxml.getChildNodes().item(2);
        Node form = vxml.getChildNodes().item(3);

        Node field = form.getFirstChild();

        assertNotNull(doc);
        assertEquals(doc.getChildNodes().getLength(), 1);
        assertEquals(vxml.getNodeName(), "vxml");
        assertEquals("form", form.getNodeName());
        assertEquals("answerInput", answerInputNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("questionId", questionIdNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("sessionKey", sessionKeyNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("property", field.getNodeName());

        field = form.getChildNodes().item(1);
        assertEquals("form", form.getNodeName());
        assertEquals("answerInput", answerInputNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("questionId", questionIdNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("sessionKey", sessionKeyNode.getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("field", field.getNodeName());
        assertEquals(5, field.getChildNodes().getLength());

        if (answerInputNode.getAttributes().getNamedItem("expr") != null) {
            variablesForAnswer.put("answerInput", answerInputNode.getAttributes().getNamedItem("expr").getNodeValue()
                                                                 .replace("'", ""));
        }
        if (questionIdNode.getAttributes().getNamedItem("expr") != null) {
            variablesForAnswer.put("questionId", questionIdNode.getAttributes().getNamedItem("expr").getNodeValue()
                                                               .replace("'", ""));
        }
        if (sessionKeyNode.getAttributes().getNamedItem("expr") != null) {
            variablesForAnswer.put("sessionKey", sessionKeyNode.getAttributes().getNamedItem("expr").getNodeValue()
                                                               .replace("'", ""));
        }
        return variablesForAnswer;
    }
}
