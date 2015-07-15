package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.Settings;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.sim.TwilioSimulator;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.askfast.commons.RestResponse;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DialogRequestDetails;
import com.askfast.commons.entity.Language;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.twilio.sdk.verbs.Gather;
import com.twilio.sdk.verbs.Hangup;
import com.twilio.sdk.verbs.Redirect;
import com.twilio.sdk.verbs.Say;
import com.twilio.sdk.verbs.TwiMLResponse;

@Category(IntegrationTest.class)
public class TwilioAdapterIT extends TestFramework {

    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    private DialogAgent dialogAgent = null;
    private static final String TEST_MESSAGE = "How are you doing? today";

    /**
     * This test checks if a sequence of people are getting called properly when
     * a preconnect is expected
     * 
     * @throws Exception
     */
    @Test
    public void sequencialCallingPreconnectTest() throws Exception {

        super.createTestDDRPrice(DDRTypeCategory.INCOMING_COMMUNICATION_COST, 0.5, "incoming calls", UnitType.SECOND,
                                 AdapterType.CALL, "");
        super.createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.5, "outgoing calls", UnitType.SECOND,
                                 AdapterType.CALL, "");

        String secondRemoteAddress = "0622222222";
        String inboundAddress = "0103030000";
        String testCallId = UUID.randomUUID().toString(); //used for the main incoming call
        String testCallId1 = UUID.randomUUID().toString(); //used for the first redirection call
        String testCallId2 = UUID.randomUUID().toString(); //used for the second redirection call

        String preconnectUrl = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                                 QuestionInRequest.CLOSED_YES_NO.name());
        preconnectUrl = ServerUtils.getURLWithQueryParams(preconnectUrl, "question", "Incoming call");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.REFERRAL.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        url = ServerUtils.getURLWithQueryParams(url, "address", remoteAddressVoice);
        url = ServerUtils.getURLWithQueryParams(url, "preconnect", preconnectUrl);
        url = ServerUtils.getURLWithQueryParams(url, "answerText", "Connecting next number");
        url = ServerUtils.getURLWithQueryParams(url, "next",
                                                ServerUtils.getURLWithQueryParams(url, "address", secondRemoteAddress));

        //create dialog
        Dialog dialog = new Dialog("Sequencial dialog", url);
        dialog.setOwner(TEST_PUBLIC_KEY);
        dialog.storeOrUpdate();

        //update adapter with dialog
        AdapterConfig adapterConfig = createTwilioAdapter();
        adapterConfig.setDialogId(dialog.getId());
        adapterConfig.update();

        TwilioAdapter twilioAdapter = Mockito.spy(new TwilioAdapter());

        //trigger an incoming call        
        Response newInboundResponse = twilioAdapter.getNewDialogPost(testCallId, TEST_PUBLIC_KEY, inboundAddress,
                                                                     adapterConfig.getMyAddress(), "inbound", null,
                                                                     null);
        //validate that a session is created with a ddr record
        List<Session> allSessions = Session.getAllSessions();
        Assert.assertThat(allSessions.size(), Matchers.is(2));
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        Assert.assertThat(ddrRecords.size(), Matchers.is(2));
        for (Session session : allSessions) {
            DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), TEST_PUBLIC_KEY);
            Assert.assertThat(ddrRecord, Matchers.notNullValue());
            if(DDRTypeCategory.INCOMING_COMMUNICATION_COST.equals(ddrRecord.getTypeCategory())) {

                assertThat(ddrRecord.getToAddress().keySet().iterator().next(),
                           Matchers.is(adapterConfig.getFormattedMyAddress()));
                assertTrue(ddrRecord.getFromAddress().equals(PhoneNumberUtils.formatNumber(inboundAddress, null)));
            }
            else {
                assertThat(ddrRecord.getFromAddress(), Matchers.is(adapterConfig.getFormattedMyAddress()));
                assertTrue(ddrRecord.getToAddress().keySet()
                                    .contains(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
            }
        }

        //new inbound call should trigger a redirect with preconnect 
        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Play>%s</Play><Dial method=\"GET\" action=\"http://%s/dialoghandler/rest/twilio/answer\"  "
                                                            + "callerId =\"%s\" timeout=\"30\"><Number method=\"GET\" url=\"http://%s/dialoghandler/rest/twilio/preconnect\">"
                                                            + "%s</Number></Dial></Response>", COMMENT_QUESTION_AUDIO,
                                                        Settings.HOST, adapterConfig.getMyAddress(), Settings.HOST,
                                                        PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                                          newInboundResponse.getEntity().toString());

        Mockito.when(twilioAdapter.fetchSessionFromParent(null, null, null, null, null))
               .thenReturn(Session.getSessionFromParentExternalId(testCallId1, testCallId,
                                                                  PhoneNumberUtils.formatNumber(remoteAddressVoice,
                                                                                                null)));

        //select to ignore the call in the preconnect options
        Response preconnect = twilioAdapter.preconnect(adapterConfig.getMyAddress(), TEST_PUBLIC_KEY,
                                                       remoteAddressVoice, "outbound-dial", testCallId1, testCallId);
        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Gather method=\"GET\" numDigits=\"1\" finishOnKey=\"\" action=\"http://%s/dialoghandler/rest/twilio/answer\" "
                                                            + "timeout=\"5\"><Say language=\"nl-nl\">%s</Say><Say language=\"nl-nl\">"
                                                            + "%s</Say><Say language=\"nl-nl\">%s</Say></Gather><Redirect method=\"GET\">"
                                                            + "http://%s/dialoghandler/rest/twilio/timeout</Redirect></Response>",
                                                        Settings.HOST, "Incoming call", "1", "2", Settings.HOST),
                                          preconnect.getEntity().toString());

        //answer the preconnect with the ignore reply
        Response answer = twilioAdapter.answer(null, "2", adapterConfig.getMyAddress(), remoteAddressVoice,
                                               "outbound-api", null, null, null, testCallId1, null);
        assertEquals("<Response><Say language=\"nl-nl\">You chose 2</Say><Hangup></Hangup></Response>".toLowerCase(),
                     answer.getEntity().toString().toLowerCase());
        twilioAdapter.receiveCCMessage(testCallId1, adapterConfig.getMyAddress(), remoteAddressVoice, "outbound-api",
                                       "completed");
        
        //mock new redirect call to the second number
        answer = twilioAdapter.answer(null, null, adapterConfig.getMyAddress(), inboundAddress, "inbound", null, null,
                                      null, testCallId, null);

        //a new referral session must have been created from testCallId as the parent external sessionId
        Session sessionFromParentExternalId = Session.getSessionFromParentExternalId(testCallId2,
                                                                                     testCallId,
                                                                                     PhoneNumberUtils.formatNumber(secondRemoteAddress,
                                                                                                                   null));
        Assert.assertThat(sessionFromParentExternalId, Matchers.notNullValue());

        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Play>%s</Play><Dial action=\"http://%s/dialoghandler/rest/twilio/answer\" method=\"GET\"  "
                                                            + "callerId =\"%s\" timeout=\"30\"><Number method=\"GET\" url=\"http://%s/dialoghandler/rest/twilio/preconnect\">"
                                                            + "%s</Number></Dial></Response>", COMMENT_QUESTION_AUDIO,
                                                        Settings.HOST, adapterConfig.getMyAddress(), Settings.HOST,
                                                        PhoneNumberUtils.formatNumber(secondRemoteAddress, null)),
                                          answer.getEntity().toString());

        preconnect = twilioAdapter.preconnect(adapterConfig.getMyAddress(), TEST_PUBLIC_KEY, secondRemoteAddress,
                                              "outbound-dial", testCallId2, testCallId);
        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Gather numDigits=\"1\" finishOnKey=\"\" action=\"http://%s/dialoghandler/rest/twilio/answer\" "
                                                            + "method=\"GET\" timeout=\"5\"><Say language=\"nl-nl\">%s</Say><Say language=\"nl-nl\">"
                                                            + "%s</Say><Say language=\"nl-nl\">%s</Say></Gather><Redirect method=\"GET\">"
                                                            + "http://%s/dialoghandler/rest/twilio/timeout</Redirect></Response>",
                                                        Settings.HOST, "Incoming call", "1", "2", Settings.HOST),
                                          preconnect.getEntity().toString());

        answer = twilioAdapter.answer(null, "1", adapterConfig.getMyAddress(), inboundAddress, "inbound", null, null,
                                      null, testCallId2, null);
        assertXMLGeneratedByTwilioLibrary("<Response><Say language=\"nl-nl\">You chose 1</Say></Response>",
                                          answer.getEntity().toString());
        ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null, null, null);
        Assert.assertThat(ddrRecords.size(), Matchers.is(3));
        int statusCount = 0;
        for (DDRRecord ddrRecord : ddrRecords) {

            Assert.assertThat(ddrRecord, Matchers.notNullValue());
            if (DDRTypeCategory.INCOMING_COMMUNICATION_COST.equals(ddrRecord.getTypeCategory())) {

                assertThat(ddrRecord.getToAddress().keySet().iterator().next(),
                           Matchers.is(adapterConfig.getFormattedMyAddress()));
                assertTrue(ddrRecord.getFromAddress().equals(PhoneNumberUtils.formatNumber(inboundAddress, null)));
                assertEquals(CommunicationStatus.RECEIVED,
                             ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(localAddressBroadsoft, null)));
            }
            else if (DDRTypeCategory.OUTGOING_COMMUNICATION_COST.equals(ddrRecord.getTypeCategory())) {
                assertThat(ddrRecord.getFromAddress(), Matchers.is(adapterConfig.getFormattedMyAddress()));
                if (ddrRecord.getToAddress().keySet().contains(PhoneNumberUtils.formatNumber(remoteAddressVoice, null))) {
                    assertEquals(CommunicationStatus.MISSED,
                                 ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
                    statusCount++;
                }
                else if (ddrRecord.getToAddress().keySet()
                                  .contains(PhoneNumberUtils.formatNumber(secondRemoteAddress, null))) {
                    assertEquals(CommunicationStatus.RECEIVED,
                                 ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(secondRemoteAddress, null)));
                    statusCount++;
                }
            }
        }
        assertEquals(2, statusCount);
    }

    @Test
    public void inboundPhoneCall_CommentTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();

        String accountSid = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        TwilioSimulator simulator = new TwilioSimulator(TestFramework.host, accountSid);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_UNITEDSTATES.getCode());

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TWILIO,
                                                          TEST_PUBLIC_KEY, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        String resp = simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);
        System.out.println(resp);
        TwiMLResponse expected = new TwiMLResponse();
        Say say = new Say(TEST_MESSAGE);
        say.setLanguage(Language.ENGLISH_UNITEDSTATES.getCode());
        expected.append(say);

        assertXMLGeneratedByTwilioLibrary(expected.toXML(), resp);
        //check all the ddrs created
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertEquals(ddrRecords.size(), 1);
        for (DDRRecord ddrRecord : ddrRecords) {
            assertEquals("inbound", ddrRecord.getDirection());
            assertEquals(adapterConfig.getFormattedMyAddress(), ddrRecord.getToAddress().keySet().iterator().next());
            assertEquals(PhoneNumberUtils.formatNumber(remoteAddressVoice, null), ddrRecord.getFromAddress());
        }
    }
    
    @Test
    public void inboundPhoneCall_InvalidReferralTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();
        String invalidNumber = "%2b3161234567";
        String accountSid = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        TwilioSimulator simulator = new TwilioSimulator(TestFramework.host, accountSid);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.REFERRAL.name());
        url = ServerUtils.getURLWithQueryParams(url, "address", invalidNumber); //invalid address
        url = ServerUtils.getURLWithQueryParams(url, "question", "Hello...");

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TWILIO,
                                                          TEST_PUBLIC_KEY, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        String initiateInboundCall = simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);
        assertThat(initiateInboundCall,
                   Matchers.not(Matchers.containsString(URLDecoder.decode(invalidNumber, "UTF-8"))));
        List<Session> allSessions = Session.getAllSessions();
        //all sessions must now be flushed
        assertThat(allSessions.size(), Matchers.is(0));
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertThat(ddrRecords.size(), Matchers.is(1));
        int ddrInfoCount = 0;
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        for (String infoKey : ddrRecord.getAdditionalInfo().keySet()) {
            if (URLDecoder.decode(invalidNumber, "UTF-8").equals(infoKey)) {
                assertThat(ddrRecord.getAdditionalInfo().get(infoKey).toString(), Matchers.is("Invalid address"));
                ddrInfoCount++;
            }
        }
        assertThat(ddrInfoCount, Matchers.is(1));
    }

    @Test
    public void inboundPhoneCall_ClosedTest() throws Exception {

        //new DDRRecordAgent().generateDefaultDDRTypes();

        String accountSid = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        TwilioSimulator simulator = new TwilioSimulator(TestFramework.host, accountSid);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.CLOSED_YES_NO.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_UNITEDSTATES.getCode());

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);

        //create Twilio adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TWILIO,
                                                          TEST_PUBLIC_KEY, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        String resp = simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);

        String lang = Language.ENGLISH_UNITEDSTATES.getCode();
        TwiMLResponse expected = new TwiMLResponse();
        Gather gather = new Gather();
        gather.getAttributes().put("action", TestFramework.host + "/rest/twilio/answer");
        gather.getAttributes().put("numDigits", "1");
        gather.getAttributes().put("finishOnKey", "");
        gather.getAttributes().put("method", "GET");
        gather.getAttributes().put("timeout", "5");
        Say say = new Say(TEST_MESSAGE);
        say.setLanguage(lang);
        gather.append(say);
        say = new Say("1");
        say.setLanguage(lang);
        gather.append(say);
        say = new Say("2");
        say.setLanguage(lang);
        gather.append(say);
        expected.append(gather);
        Redirect redirect = new Redirect(TestFramework.host + "/rest/twilio/timeout");
        redirect.getAttributes().put("method", "GET");
        expected.append(redirect);

        assertXMLGeneratedByTwilioLibrary(expected.toXML(), resp);

        resp = simulator.nextQuestion("1");

        expected = new TwiMLResponse();
        say = new Say("You chose 1");
        say.setLanguage(lang);
        expected.append(say);

        assertXMLGeneratedByTwilioLibrary(expected.toXML(), resp);
    }
    
    /**
     * Test to check if answers with mixed dtmf key input works. E.g. should
     * work with both index based and dtmfKey:// prefix key based
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_ClosedWithMixedDTMFKeyTest() throws Exception {

        //new DDRRecordAgent().generateDefaultDDRTypes();

        String accountSid = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        TwilioSimulator simulator = new TwilioSimulator(TestFramework.host, accountSid);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.CLOSED_YES_NO.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        url = ServerUtils.getURLWithQueryParams(url, "prefix1", "null");
        url = ServerUtils.getURLWithQueryParams(url, "prefix2", "dtmfKey://");
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_UNITEDSTATES.getCode());

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);

        //create Twilio adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TWILIO,
                                                          TEST_PUBLIC_KEY, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        String resp = simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);

        String lang = Language.ENGLISH_UNITEDSTATES.getCode();
        TwiMLResponse expected = new TwiMLResponse();
        Gather gather = new Gather();
        gather.getAttributes().put("action", TestFramework.host + "/rest/twilio/answer");
        gather.getAttributes().put("numDigits", "1");
        gather.getAttributes().put("finishOnKey", "");
        gather.getAttributes().put("method", "GET");
        gather.getAttributes().put("timeout", "5");
        Say say = new Say(TEST_MESSAGE);
        say.setLanguage(lang);
        gather.append(say);
        expected.append(gather);
        Redirect redirect = new Redirect(TestFramework.host + "/rest/twilio/timeout");
        redirect.getAttributes().put("method", "GET");
        expected.append(redirect);

        assertXMLGeneratedByTwilioLibrary(expected.toXML(), resp);

        resp = simulator.nextQuestion("1");
        //it should just repeat the question.. as the question is a mix of dtmf and null answer texts
        assertXMLGeneratedByTwilioLibrary(expected.toXML(), resp);
        
        //but pressing 2 as it matches the dtmf key should work
        resp = simulator.nextQuestion("2");
        expected = new TwiMLResponse();
        say = new Say("You chose 2");
        say.setLanguage(lang);
        expected.append(say);
        expected.append(new Hangup());
        assertXMLGeneratedByTwilioLibrary(expected.toXML(), resp);
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

        new DDRRecordAgent().generateDefaultDDRTypes();

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        String ttsAccountId = UUID.randomUUID().toString();
        ttsInfo.setTtsAccountId(ttsAccountId);
        ttsInfo.setVoiceUsed("testtest");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        Response securedDialogResponse = performSecuredCall("inbound", "testuserName", "testpassword", ttsInfo, url,
                                                            null);
        assertTrue(securedDialogResponse != null);
        //make sure that the tts source generated has a service and voice
        Document doc = getXMLDocumentBuilder(securedDialogResponse.getEntity().toString());
        String ttsURL = doc.getFirstChild().getFirstChild().getTextContent();
        URIBuilder uriBuilder = new URIBuilder(URLDecoder.decode(ttsURL, "UTF-8").replace(" ", "%20"));
        for (NameValuePair queryParams : uriBuilder.getQueryParams()) {
            if (queryParams.getName().equals("text")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_MESSAGE));
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
            else if (queryParams.getName().equals("id")) {
                assertThat(queryParams.getValue(), Matchers.is(ttsAccountId));
                continue;
            }
            else if (queryParams.getName().equals("askFastAccountId")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_PUBLIC_KEY));
                continue;
            }
            assertTrue(String.format("query not found: %s=%s", queryParams.getName(), queryParams.getValue()), false);
        }
    }
    
    /**
     * /**
     * This test is to check if the inbound functionality works for a dialog
     * with the right credentials for the secured url access, but no ddr records
     * are created when a test flag is set to true
     * 
     * @throws Exception
     */
    @Test
    public void inboundPhoneCall_DoesNotCreateDDRWithFlagTest() throws Exception {

        inboundPhoneCall_WithSecuredDialogAndTTSInfoTest();
        //validate that ddr records are created when isTest is set to false
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        Assert.assertThat(ddrRecords.size(), Matchers.equalTo(1));
        
        //trigger a second incoming call with test flag to be true. flush all sessions, adapters and ddrRecords
        setup();
        
        //trigger a second inbound call with test flag set to true
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.INCOMING_COMMUNICATION_COST, 0.1, "Test incoming costs", UnitType.MINUTE,
                           null, null);
        new DDRRecordAgent().generateDefaultDDRTypes();

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        String ttsAccountId = UUID.randomUUID().toString();
        ttsInfo.setTtsAccountId(ttsAccountId);
        ttsInfo.setVoiceUsed("testtest");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        performSecuredCall("inbound", "testuserName", "testpassword", ttsInfo, url, true);

        //validate that ddr records are created when isTest is set to false
        ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null, null, null);
        Assert.assertThat(ddrRecords.size(), Matchers.equalTo(0));
    }

    /**
     * This test is to check if the outbound functionality works for a dialog
     * with the right credentials for the secured url access along with TTSInfo
     * given in the dialog
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCall_WithSecuredDialogAndTTSInfoTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        ttsInfo.setVoiceUsed("testtest");
        String ttsAccountId = UUID.randomUUID().toString();
        ttsInfo.setTtsAccountId(ttsAccountId);
        ttsInfo.setSpeed("0");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        Response securedDialogResponse = performSecuredCall("outbound", "testuserName", "testpassword", ttsInfo, url,
                                                            null);
        assertNotNull(securedDialogResponse);
        //make sure that the tts source generated has a service and voice
        Document doc = getXMLDocumentBuilder(securedDialogResponse.getEntity().toString());
        String ttsURL = doc.getFirstChild().getFirstChild().getTextContent();
        URIBuilder uriBuilder = new URIBuilder(URLDecoder.decode(ttsURL, "UTF-8").replace(" ", "%20"));
        for (NameValuePair queryParams : uriBuilder.getQueryParams()) {
            if (queryParams.getName().equals("text")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_MESSAGE));
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
            else if (queryParams.getName().equals("id")) {
                assertThat(queryParams.getValue(), Matchers.is(ttsAccountId));
                continue;
            }
            else if (queryParams.getName().equals("askFastAccountId")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_PUBLIC_KEY));
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
        Response securedDialogResponse = performSecuredCall(direction, "testuserName", "testpassword", ttsInfo, url,
                                                            null);
        assertTrue(securedDialogResponse != null);
        //check if ddr is created for ttsprocessing
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
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
    public void inboundPhoneCall_ForTTSCostProcessingTest() throws Exception {
        phoneCall_ForTTSCostProcessingTest("inbound");
    }
    
    @Test
    public void outboundPhoneCall_ForTTSCostProcessingTest() throws Exception {
        phoneCall_ForTTSCostProcessingTest("outbound");
    }
    
    /**
     * This test is to check if tts charges are attached to the inbound
     * functionality when the ttsAccountId is not found. No tts service costs
     * must be seen
     * 
     * @throws Exception
     */
    public void phoneCall_ForTTSCostProcessingTest(String direction) throws Exception {

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
        Response securedDialogResponse = performSecuredCall("inbound", "testuserName", "testpassword", ttsInfo,
                                                                   url, null);
        assertTrue(securedDialogResponse != null);
        //check if ddr is created for ttsprocessing
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        int ttsChargesAttached = 0;
        for (DDRRecord ddrRecord : ddrRecords) {
            Assert.assertFalse(ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.TTS_SERVICE_COST));
            if (ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.TTS_COST)) {
                ttsChargesAttached++;
            }
        }
        assertEquals(1, ttsChargesAttached);
    }

    /**
     * This test is to check if the outbound functionality works for a dialog
     * with language as English. But adapter is set to French, and question is
     * set to Dutch still parses TTS in English
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCall_WithEnglishLanguageTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        ttsInfo.setVoiceUsed("testtest");
        ttsInfo.setSpeed("0");
        String ttsAccountId = UUID.randomUUID().toString();
        ttsInfo.setTtsAccountId(ttsAccountId);
        ttsInfo.setLanguage(Language.ENGLISH_UNITEDSTATES);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        Response securedDialogResponse = performSecuredCall("outbound", "testuserName", "testpassword", ttsInfo,
                                                                   url, null);
        assertTrue(securedDialogResponse != null);
        //make sure that the tts source generated has a service and voice
        Document doc = getXMLDocumentBuilder(securedDialogResponse.getEntity().toString());
        String ttsURL = doc.getFirstChild().getFirstChild().getTextContent();
        URIBuilder uriBuilder = new URIBuilder(URLDecoder.decode(ttsURL, "UTF-8").replace(" ", "%20"));
        for (NameValuePair queryParams : uriBuilder.getQueryParams()) {
            if (queryParams.getName().equals("text")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_MESSAGE));
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
                assertThat(queryParams.getValue(), Matchers.is("en-us"));
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
            else if (queryParams.getName().equals("id")) {
                assertThat(queryParams.getValue(), Matchers.is(ttsAccountId));
                continue;
            }
            else if (queryParams.getName().equals("askFastAccountId")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_PUBLIC_KEY));
                continue;
            }
            assertTrue(String.format("query not found: %s=%s", queryParams.getName(), queryParams.getValue()), false);
        }
    }

    /**
     * This test is to check if the outbound functionality works for a dialog
     * with language as French. But adapter is set to English and question
     * parsed in Dutch, parses TTS in French
     * 
     * @throws Exception
     */
    @Test
    public void outboundPhoneCall_WithEnglishTTSAndDiffLanguageInQuestionTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();

        TTSInfo ttsInfo = new TTSInfo();
        ttsInfo.setProvider(TTSProvider.ACAPELA);
        ttsInfo.setVoiceUsed("testtest");
        ttsInfo.setSpeed("0");
        String ttsAccountId = UUID.randomUUID().toString();
        ttsInfo.setTtsAccountId(ttsAccountId);
        ttsInfo.setLanguage(Language.DUTCH);

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.FRENCH_FRANCE.getCode());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        Response securedDialogResponse = performSecuredCall("outbound", "testuserName", "testpassword", ttsInfo,
                                                                   url, null);
        assertTrue(securedDialogResponse != null);
        //make sure that the tts source generated has a service and voice
        Document doc = getXMLDocumentBuilder(securedDialogResponse.getEntity().toString());
        String ttsURL = doc.getFirstChild().getFirstChild().getTextContent();
        URIBuilder uriBuilder = new URIBuilder(URLDecoder.decode(ttsURL, "UTF-8").replace(" ", "%20"));
        for (NameValuePair queryParams : uriBuilder.getQueryParams()) {
            if (queryParams.getName().equals("text")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_MESSAGE));
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
                assertThat(queryParams.getValue(), Matchers.is("fr-fr"));
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
            else if (queryParams.getName().equals("id")) {
                assertThat(queryParams.getValue(), Matchers.is(ttsAccountId));
                continue;
            }
            else if (queryParams.getName().equals("askFastAccountId")) {
                assertThat(queryParams.getValue(), Matchers.is(TEST_PUBLIC_KEY));
                continue;
            }
            assertTrue(String.format("query not found: %s=%s", queryParams.getName(), queryParams.getValue()), false);
        }
    }

    /**
     * This test is to check if the outbound functionality will create ddr
     * records for tts processing.
     * 
     * @throws Exception
     */
    @Test
    public void ttsInvokesCharges() throws Exception {

        //create a ddrPrice
        createTestDDRPrice(DDRTypeCategory.TTS_COST, 0.1, "ddr price for tts", UnitType.PART, null, null);
        createTestDDRPrice(DDRTypeCategory.TTS_SERVICE_COST, 0.01, "ddr price for tts service", UnitType.PART, null,
                           null);
        outboundPhoneCall_WithEnglishTTSAndDiffLanguageInQuestionTest();
        DDRType ddrType = DDRType.getDDRType(DDRTypeCategory.TTS_SERVICE_COST);
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null,
                                                             Arrays.asList(ddrType.getTypeId()), null, null, null,
                                                             null, null, null);
        assertThat(ddrRecords.size(), Matchers.is(1));
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        ddrRecord.setShouldGenerateCosts(true);
        assertThat(ddrRecord.getTotalCost(), Matchers.is(0.01));
    }

    /**
     * This test is to check if the outbound functionality does not invoke ddr
     * charges as there is no ddrPrice attached for the tts provider
     * 
     * @throws Exception
     */
    @Test
    public void ttsDoesNotInvokesCharges() throws Exception {

        //create a ddrPrice for voice rss. 
        DDRPrice ddrPrice = createTestDDRPrice(DDRTypeCategory.TTS_COST, 0.1, "ddr price for tts", UnitType.PART, null,
                                               null);
        ddrPrice.setKeyword(TTSProvider.VOICE_RSS.toString());
        ddrPrice.createOrUpdate();

        //invoke an outbound call with acapella tts
        outboundPhoneCall_WithEnglishTTSAndDiffLanguageInQuestionTest();
        DDRType ddrType = DDRType.getDDRType(DDRTypeCategory.TTS_COST);
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null,
                                                             Arrays.asList(ddrType.getTypeId()), null, null, null,
                                                             null, null, null);
        //make sure there is no costs involved, as the ddr price attached is for VoiceRSS tts and not acapela
        assertThat(ddrRecords.size(), Matchers.is(0));
    }
    
    /**
     * Test if the
     * {@link DialogAgent#outboundCallWithDialogRequest(com.askfast.commons.entity.DialogRequestDetails)}
     * gives an error code if the question is not fetched by the dialog agent
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
        AdapterConfig adapterConfig = createTwilioAdapter();
        adapterConfig.update();
        
        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.SECOND, AdapterType.CALL,
                           null);
        
        DialogRequestDetails details = new DialogRequestDetails();
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
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertEquals(1, ddrRecords.size());
        assertEquals(CommunicationStatus.ERROR,
                     ddrRecords.iterator().next()
                               .getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertEquals(1, ddrRecords.iterator().next().getStatusPerAddress().size());
    }
    
    /**
     * Test if the
     * {@link DialogAgent#outboundCallWithDialogRequest(com.askfast.commons.entity.DialogRequestDetails)}
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
        AdapterConfig adapterConfig = createTwilioAdapter();

        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.SECOND, AdapterType.CALL,
                           null);

        DialogRequestDetails details = new DialogRequestDetails();
        details.setAccountID(adapterConfig.getOwner());
        details.setAdapterID(adapterConfig.getConfigId());
        details.setAddress("0611223"); //invalid address
        details.setBearerToken(UUID.randomUUID().toString());
        details.setMethod("outboundCall");
        details.setUrl(url);
        RestResponse outboundCallResponse = dialogAgent.outboundCallWithDialogRequest(details);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), outboundCallResponse.getCode());

        //verify that the session is not saved
        assertEquals(0, Session.getAllSessions().size());
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertEquals(1, ddrRecords.size());
        assertEquals(CommunicationStatus.ERROR,
                     ddrRecords.iterator().next().getStatusForAddress(PhoneNumberUtils.formatNumber("0611223", null)));
        assertEquals(1, ddrRecords.iterator().next().getStatusPerAddress().size());
    }
    
    /**
     * Test if the
     * {@link DialogAgent#outboundCallWithDialogRequest(com.askfast.commons.entity.DialogRequestDetails)}
     * gives an error code if the question is fetched by the dialog agent but
     * the telephone numbers are a mix of valid and invalid numbers
     * 
     * @throws UnsupportedEncodingException
     */
    @Test
    public void outboundCallWithQuestionInvalidAndValidAddressTest() throws Exception {

        dialogAgent = new DialogAgent();
        //setup bad question url
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.TWELVE_INPUT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "start");

        //create mail adapter
        AdapterConfig adapterConfig = createTwilioAdapter();

        //setup to generate ddrRecords
        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.SECOND, AdapterType.CALL,
                           null);

        DialogRequestDetails details = new DialogRequestDetails();
        details.setAccountID(adapterConfig.getOwner());
        details.setAdapterID(adapterConfig.getConfigId());
        details.setAddressList(Arrays.asList("0611223", remoteAddressVoice));
        details.setBearerToken(UUID.randomUUID().toString());
        details.setMethod("outboundCallWithList");
        details.setUrl(url);
        RestResponse outboundCallResponse = dialogAgent.outboundCallWithDialogRequest(details);
        assertEquals(Status.CREATED.getStatusCode(), outboundCallResponse.getCode());

        //verify that the session is not saved
        assertEquals(1, Session.getAllSessions().size());
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        assertEquals(1, ddrRecords.size());
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        assertEquals(CommunicationStatus.ERROR,
                     ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber("0611223", null)));
        assertEquals(CommunicationStatus.SENT,
                     ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertEquals(2, ddrRecords.iterator().next().getStatusPerAddress().size());
        
        //finalize the call. fetch the callSid from the session
        Session sessionForValidNumber = Session.getSessionByInternalKey(adapterConfig.getAdapterType(),
                                                                        adapterConfig.getMyAddress(),
                                                                        PhoneNumberUtils.formatNumber(remoteAddressVoice,
                                                                                                      null));
        //update with some answer timestamp
        sessionForValidNumber.setAnswerTimestamp(String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
        sessionForValidNumber.storeSession();
        
        assertNotNull(sessionForValidNumber);
        new TwilioAdapter().receiveCCMessage(sessionForValidNumber.getExternalSession(),
                                             sessionForValidNumber.getLocalAddress(),
                                             sessionForValidNumber.getRemoteAddress(),
                                             sessionForValidNumber.getDirection(), "completed");
        //validate the ddrRecords again
        ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                             null, null);
        assertEquals(1, ddrRecords.size());
        ddrRecord = ddrRecords.iterator().next();
        assertEquals(CommunicationStatus.ERROR,
                     ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber("0611223", null)));
        assertEquals(CommunicationStatus.FINISHED,
                     ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)));
        assertEquals(2, ddrRecords.iterator().next().getStatusPerAddress().size());
    }

    /**
     * @throws UnsupportedEncodingException
     * @throws Exception
     * @throws URISyntaxException
     */
    private Response performSecuredCall(String direction, String username, String password, TTSInfo ttsInfo,
        String url, Boolean isTest) throws Exception {

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
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TWILIO,
                                                          TEST_PUBLIC_KEY, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        //mock the Context
        String remoteAddress = remoteAddressVoice;
        String localAddress = localAddressBroadsoft;
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(new URI(TestServlet.TEST_SERVLET_PATH));
        String callSid = UUID.randomUUID().toString();
        if (direction.equals("outbound")) {
            HashMap<String, String> outboundCall = dialogAgent.outboundCall(remoteAddressVoice, "test", null,
                                                                            createDialog.getId(), null,
                                                                            adapterConfig.getConfigId(),
                                                                            TEST_PUBLIC_KEY, null);
            String sessionKey = outboundCall.values().iterator().next();
            Session session = Session.getSession(sessionKey);
            callSid = session.getExternalSession();
        }
        else {
            String tmpLocalId = new String(localAddress);
            localAddress = new String(remoteAddress);
            remoteAddress = tmpLocalId;
        }
        return new TwilioAdapter().getNewDialog(callSid, UUID.randomUUID().toString(), localAddress, remoteAddress,
                                                direction, null, isTest);
    }
}
