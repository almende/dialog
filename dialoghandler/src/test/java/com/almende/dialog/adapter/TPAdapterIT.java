package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.sim.TPSimulator;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.jackson.JOM;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.Language;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.strowger.sdk.actions.Action;
import com.askfast.strowger.sdk.actions.Dtmf;
import com.askfast.strowger.sdk.actions.Hangup;
import com.askfast.strowger.sdk.actions.Include;
import com.askfast.strowger.sdk.actions.Play;
import com.askfast.strowger.sdk.actions.StrowgerAction;
import com.askfast.strowger.sdk.model.Call;
import com.askfast.strowger.sdk.model.ControlResult;

@Category(IntegrationTest.class)
public class TPAdapterIT extends TestFramework {

    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    private static final String TEST_MESSAGE_1 = "How are you doing? today";
    private static final String TEST_MESSAGE_2 = "Thanks";
    
    private static final String TEST_MESSAGE = "How are you doing? today";
    
    @Test
    public void inboundPhoneCall_CommentTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();

        String tenantKey = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        
        TPSimulator simulator = new TPSimulator( TestFramework.host, tenantKey );        

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE_1);
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_UNITEDSTATES.getCode());
        
        String callback = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                QuestionInRequest.SIMPLE_COMMENT.name());
        callback = ServerUtils.getURLWithQueryParams(callback, "question", TEST_MESSAGE_2);
        
        url = ServerUtils.getURLWithQueryParams(url, "callback", callback);

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_PUBLIC_KEY);

        //create TP adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TP,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);
                
        StrowgerAction actual = simulator.getReponse();
        
        assertEquals(2, actual.getData().size());
        Action action = actual.getData().get( 0 );
        assertThat( action, instanceOf(Play.class));
        Play play = (Play) action;
        assertEquals(1, play.getLocations().size());
        assertThat(play.getLocations().get(0).toASCIIString(), startsWith("http://tts.ask-fast.com"));
        
        action = actual.getData().get(1 );
        assertThat( action, instanceOf(Include.class));
                
        simulator.nextQuestion( null );
        
        actual = simulator.getReponse();
        
        assertEquals(1, actual.getData().size());
        action = actual.getData().get( 0 );
        assertThat( action, instanceOf(Play.class));
        
        //check all the ddrs created
        List<DDRRecord> ddrRecords = getAllDdrRecords( TEST_ACCOUNT_ID );
        
        assertEquals(ddrRecords.size(), 1);
        for (DDRRecord ddrRecord : ddrRecords) {
            assertEquals("inbound", ddrRecord.getDirection());
            assertEquals(adapterConfig.getFormattedMyAddress(), ddrRecord.getToAddress().keySet().iterator().next());
            assertEquals(PhoneNumberUtils.formatNumber(remoteAddressVoice, null), ddrRecord.getFromAddress());
        }
    }
    
    @Test
    public void inboundPhoneCall_ClosedTest() throws Exception {

        //new DDRRecordAgent().generateDefaultDDRTypes();

        String tenantKey = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        //TwilioSimulator simulator = new TwilioSimulator(TestFramework.host, accountSid);
        TPSimulator simulator = new TPSimulator( TestFramework.host, tenantKey );

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.CLOSED_YES_NO.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_UNITEDSTATES.getCode());

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_ACCOUNT_ID);

        //create Twilio adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TP,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);
        
        StrowgerAction actual = simulator.getReponse();
        List<Action> actions = actual.getData();
        assertEquals(2, actions.size());
        Action action = actions.get( 0 );
        assertThat( action, instanceOf(Dtmf.class));
        Dtmf dtmf = (Dtmf) action;
        
        assertEquals(TestFramework.host + "/rest/strowger/answer", dtmf.getUrl().toASCIIString());
        assertEquals(1, dtmf.getMaxDigits().intValue());
        assertEquals("", dtmf.getFinishOnKey());
        assertEquals(5, dtmf.getTimeout().intValue());
        
        Play play = dtmf.getPlay();
        URI url1 = play.getLocations().get(0);
        URI url2 = play.getLocations().get(1);
        URI url3 = play.getLocations().get(2);
        assertThat(url1.toASCIIString(), containsString("text="+URLEncoder.encode(TEST_MESSAGE, "UTF-8")));
        assertThat(url2.toASCIIString(), containsString("text=1"));
        assertThat(url3.toASCIIString(), containsString("text=2"));
        
        action = actions.get( 1 );
        assertThat( action, instanceOf(Include.class));
        Include include = (Include) action;
        assertEquals( TestFramework.host + "/rest/strowger/timeout", include.getLocation().toASCIIString());

          
        simulator.nextQuestion("1");
        
        actual = simulator.getReponse();
        
        actions = actual.getData();
        assertEquals(1, actions.size());
        action = actions.get( 0 );
        assertThat( action, instanceOf(Play.class));
        play = (Play) action;
        
        URI testurl = play.getLocations().get(0);
        
        assertThat(testurl.toASCIIString(), containsString("text="+URLEncoder.encode("You chose 1", "UTF-8")));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void inboundPhoneCall_ClosedTest_WithTimeout() throws Exception {

        String tenantKey = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        //TwilioSimulator simulator = new TwilioSimulator(TestFramework.host, accountSid);
        TPSimulator simulator = new TPSimulator( TestFramework.host, tenantKey );

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.CLOSED_YES_NO.name());
        url = ServerUtils.getURLWithQueryParams(url, "preMessage", "Hi there");
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_UNITEDSTATES.getCode());

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_ACCOUNT_ID);

        //create Twilio adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TP,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);
        
        StrowgerAction actual = simulator.getReponse();
        List<Action> actions = actual.getData();
        assertEquals(2, actions.size());
        Action action = actions.get( 0 );
        assertThat( action, instanceOf(Play.class));
        Play play = (Play) action;
        
        action = actions.get( 1 );
        assertThat( action, instanceOf(Include.class));
        Include include = (Include) action;
        assertEquals( TestFramework.host + "/rest/strowger/answer", include.getLocation().toASCIIString());
        
        simulator.nextQuestion(null);
        
        // Load the premessage
        actual = simulator.getReponse();
        actions = actual.getData();
        assertEquals(2, actions.size());
        action = actions.get( 0 );
        assertThat( action, instanceOf(Dtmf.class));
        Dtmf dtmf = (Dtmf) action;
        
        assertEquals(TestFramework.host + "/rest/strowger/answer", dtmf.getUrl().toASCIIString());
        assertEquals(1, dtmf.getMaxDigits().intValue());
        assertEquals("", dtmf.getFinishOnKey());
        assertEquals(5, dtmf.getTimeout().intValue());
        
        play = dtmf.getPlay();
        URI url1 = play.getLocations().get(0);
        URI url2 = play.getLocations().get(1);
        URI url3 = play.getLocations().get(2);
        
        assertThat(url1.toASCIIString(), containsString("text="+URLEncoder.encode(TEST_MESSAGE)));
        assertThat(url2.toASCIIString(), containsString("text=1"));
        assertThat(url3.toASCIIString(), containsString("text=2"));
        
        action = actions.get( 1 );
        assertThat( action, instanceOf(Include.class));
        include = (Include) action;
        assertEquals( TestFramework.host + "/rest/strowger/timeout", include.getLocation().toASCIIString());
     
        // trigger the timeout
        simulator.timeout();
        
        // expect the last message
        actual = simulator.getReponse();
        actions = actual.getData();
        assertEquals(2, actions.size());
        action = actions.get( 0 );
        assertThat( action, instanceOf(Dtmf.class));
        dtmf = (Dtmf) action;
        
        assertEquals(TestFramework.host + "/rest/strowger/answer", dtmf.getUrl().toASCIIString());
        assertEquals(1, dtmf.getMaxDigits().intValue());
        assertEquals("", dtmf.getFinishOnKey());
        assertEquals(5, dtmf.getTimeout().intValue());
        
        play = dtmf.getPlay();
        url1 = play.getLocations().get(0);
        url2 = play.getLocations().get(1);
        url3 = play.getLocations().get(2);
        
        assertThat(url1.toASCIIString(), containsString("text="+URLEncoder.encode(TEST_MESSAGE)));
        assertThat(url2.toASCIIString(), containsString("text=1"));
        assertThat(url3.toASCIIString(), containsString("text=2"));
        
        action = actions.get( 1 );
        assertThat( action, instanceOf(Include.class));
        include = (Include) action;
        
        assertEquals( TestFramework.host + "/rest/strowger/timeout", include.getLocation().toASCIIString());

        // send the actual result
        simulator.nextQuestion("1");

        actual = simulator.getReponse();
        actions = actual.getData();
        assertEquals(1, actions.size());
        action = actions.get( 0 );
        assertThat( action, instanceOf(Play.class));
        play = (Play) action;
        
        URI testurl = play.getLocations().get(0);
        
        assertThat(testurl.toASCIIString(), containsString("text="+URLEncoder.encode("You chose 1")));
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

        String tenantKey = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        TPSimulator simulator = new TPSimulator( TestFramework.host, tenantKey );

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.CLOSED_YES_NO.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", TEST_MESSAGE);
        url = ServerUtils.getURLWithQueryParams(url, "prefix1", "null");
        url = ServerUtils.getURLWithQueryParams(url, "prefix2", "dtmfKey://");
        url = ServerUtils.getURLWithQueryParams(url, "lang", Language.ENGLISH_UNITEDSTATES.getCode());

        //create a dialog
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_ACCOUNT_ID);

        //create Twilio adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TP,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft,
                                                          localAddressBroadsoft, url);
        adapterConfig.setPreferred_language(Language.ENGLISH_UNITEDSTATES.getCode());
        adapterConfig.setDialogId(createDialog.getId());
        adapterConfig.update();

        simulator.initiateInboundCall(callSid, remoteAddressVoice, localAddressBroadsoft);

        StrowgerAction actual = simulator.getReponse();
        List<Action> actions = actual.getData();
        assertEquals(2, actions.size());
        Action action = actions.get( 0 );
        assertThat( action, instanceOf(Dtmf.class));
        Dtmf dtmf = (Dtmf) action;
        
        assertEquals(TestFramework.host + "/rest/strowger/answer", dtmf.getUrl().toASCIIString());
        assertEquals(1, dtmf.getMaxDigits().intValue());
        assertEquals("", dtmf.getFinishOnKey());
        assertEquals(5, dtmf.getTimeout().intValue());
        
        Play play = dtmf.getPlay();
        URI url1 = play.getLocations().get(0);
        assertThat(url1.toASCIIString(), containsString("text="+URLEncoder.encode(TEST_MESSAGE, "UTF-8")));
        
        action = actions.get( 1 );
        assertThat( action, instanceOf(Include.class));
        Include include = (Include) action;
        assertEquals( TestFramework.host + "/rest/strowger/timeout", include.getLocation().toASCIIString());

        simulator.nextQuestion("1");
        //it should just repeat the question.. as the question is a mix of dtmf and null answer texts
        actual = simulator.getReponse();
        actions = actual.getData();
        assertEquals(2, actions.size());
        action = actions.get( 0 );
        assertThat( action, instanceOf(Dtmf.class));
        dtmf = (Dtmf) action;
        
        assertEquals(TestFramework.host + "/rest/strowger/answer", dtmf.getUrl().toASCIIString());
        assertEquals(1, dtmf.getMaxDigits().intValue());
        assertEquals("", dtmf.getFinishOnKey());
        assertEquals(5, dtmf.getTimeout().intValue());
        
        play = dtmf.getPlay();
        url1 = play.getLocations().get(0);
        assertThat(url1.toASCIIString(), containsString("text="+URLEncoder.encode(TEST_MESSAGE, "UTF-8")));
        
        action = actions.get( 1 );
        assertThat( action, instanceOf(Include.class));
        include = (Include) action;
        assertEquals( TestFramework.host + "/rest/strowger/timeout", include.getLocation().toASCIIString());
        
        //but pressing 2 as it matches the dtmf key should work
        simulator.nextQuestion("2");
        
        actual = simulator.getReponse();
        actions = actual.getData();
        assertEquals(2, actions.size());
        action = actions.get( 0 );
        assertThat( action, instanceOf(Play.class));
        play = (Play) action;
        action = actions.get( 1 );
        assertThat( action, instanceOf(Hangup.class));
        
        URI testurl = play.getLocations().get(0);
        assertThat(testurl.toASCIIString(), containsString("text="+URLEncoder.encode("You chose 2", "UTF-8")));
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
        Response securedDialogResponse = performSecuredCall(TPAdapter.INBOUND, "testuserName", "testpassword", ttsInfo, url, "in-progress", 
                                                            null);
        
        System.out.println("Resp: " + securedDialogResponse.getEntity().toString());
        StrowgerAction strowger = JOM.getInstance().readValue(securedDialogResponse.getEntity().toString(), StrowgerAction.class);
        List<Action> actions = strowger.getData();
        assertEquals(1, actions.size());
        Action action = actions.get( 0 );
        assertThat( action, instanceOf(Play.class));
        Play play = (Play) action;
        URI uri = play.getLocations().get(0);
        
        assertNotNull(securedDialogResponse);
        //make sure that the tts source generated has a service and voice
        //Document doc = getXMLDocumentBuilder();
        String ttsURL = uri.toASCIIString();
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
                assertThat(queryParams.getValue(), Matchers.is(TEST_ACCOUNT_ID));
                continue;
            }
            //assertTrue(String.format("query not found: %s=%s", queryParams.getName(), queryParams.getValue()), false);
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
        List<DDRRecord> ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
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
        performSecuredCall("incoming", "testuserName", "testpassword", ttsInfo, url, "in-progress", true);

        //validate that ddr records are created when isTest is set to false
        ddrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
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
        Response securedDialogResponse = performSecuredCall(TPAdapter.OUTBOUND, "testuserName", "testpassword", ttsInfo, url, "in-progress",
                                                            null);
        assertNotNull(securedDialogResponse);
        //make sure that the tts source generated has a service and voice
        StrowgerAction strowger = JOM.getInstance().readValue(securedDialogResponse.getEntity().toString(), StrowgerAction.class);
        List<Action> actions = strowger.getData();
        assertEquals(1, actions.size());
        Action action = actions.get( 0 );
        assertThat( action, instanceOf(Play.class));
        Play play = (Play) action;
        URI uri = play.getLocations().get(0);
        
        URIBuilder uriBuilder = new URIBuilder(URLDecoder.decode(uri.toASCIIString(), "UTF-8").replace(" ", "%20"));
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
                assertThat(queryParams.getValue(), Matchers.is(TEST_ACCOUNT_ID));
                continue;
            }
        }
    }
    
    /**
     * @throws UnsupportedEncodingException
     * @throws Exception
     * @throws URISyntaxException
     */
    private Response performSecuredCall(String direction, String username, String password, TTSInfo ttsInfo,
        String url, String callStatus, Boolean isTest) throws Exception {

        if (url == null) {
            url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                    QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name());
            url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
            url = ServerUtils.getURLWithQueryParams(url, "secured", "true");
        }

        //create a dialog
        dialogAgent = dialogAgent != null ? dialogAgent : new DialogAgent();
        dialogAgent.createDialog(TEST_PUBLIC_KEY, "Test secured dialog", url);
        Dialog createDialog = Dialog.createDialog("Test secured dialog", url, TEST_ACCOUNT_ID);
        createDialog.setUserName(username);
        createDialog.setPassword(password);
        createDialog.setUseBasicAuth(true);
        createDialog.setTtsInfo(ttsInfo);
        createDialog.storeOrUpdate();

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, AdapterProviders.TP,
                                                          TEST_ACCOUNT_ID, localAddressBroadsoft,
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
        if (direction.equals("outgoing")) {
            HashMap<String, String> outboundCall = dialogAgent.outboundCall(remoteAddressVoice, "test", null,
                createDialog.getId(), null, adapterConfig.getConfigId(), TEST_ACCOUNT_ID, null,
                adapterConfig.getAccountType());
            String sessionKey = outboundCall.values().iterator().next();
            Session session = Session.getSession(sessionKey);
            callSid = session.getExternalSession();
        }
        else {
            String tmpLocalId = new String(localAddress);
            localAddress = new String(remoteAddress);
            remoteAddress = tmpLocalId;
        }
        Call call = new Call();
        call.setCalled(remoteAddress);
        call.setCaller(localAddress);
        call.setCallId(callSid);
        call.setCallType(direction);
        
        ControlResult res = new ControlResult(call);
        
        return new TPAdapter().getNewDialogPost(isTest, res.toJson());
    }
}