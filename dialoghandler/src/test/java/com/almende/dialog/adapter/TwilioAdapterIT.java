package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.Settings;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.utils.PhoneNumberUtils;

@Category(IntegrationTest.class)
public class TwilioAdapterIT extends TestFramework {

    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";

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
        dialog.storeOrUpdate();

        //update adapter with dialog
        AdapterConfig adapterConfig = createTwilioAdapter();
        adapterConfig.setDialogId(dialog.getId());
        adapterConfig.update();

        TwilioAdapter twilioAdapter = Mockito.spy(new TwilioAdapter());

        //trigger an incoming call        
        Response newInboundResponse = twilioAdapter.getNewDialogPost(testCallId, TEST_PUBLIC_KEY, inboundAddress,
                                                                     adapterConfig.getMyAddress(), "inbound", null);
        //validate that a session is created with a ddr record
        List<Session> allSessions = Session.getAllSessions();
        Assert.assertThat(allSessions.size(), Matchers.is(2));
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null, TEST_PUBLIC_KEY, null, null, null, null, null, null,
                                                             null, null);
        Assert.assertThat(ddrRecords.size(), Matchers.is(2));
        for (Session session : allSessions) {
            DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), TEST_PUBLIC_KEY);
            Assert.assertThat(ddrRecord, Matchers.notNullValue());
        }

        //new inbound call should trigger a redirect with preconnect 
        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Play>%s</Play><Dial method=\"GET\" action=\"http://%s/dialoghandler/rest/twilio/answer\"  "
                                                                                        + "callerId =\"%s\" timeout=\"30\"><Number method=\"GET\" url=\"http://%s/dialoghandler/rest/twilio/preconnect\">"
                                                                                        + "%s</Number></Dial></Response>",
                                                        COMMENT_QUESTION_AUDIO, Settings.HOST,
                                                        adapterConfig.getMyAddress(), Settings.HOST,
                                                        PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                                          newInboundResponse.getEntity().toString());

        Mockito.when(twilioAdapter.fetchSessionFromParent(null, null, null))
                                        .thenReturn(Session.getSessionFromParentExternalId(testCallId1, testCallId));

        //select to ignore the call in the preconnect options
        Response preconnect = twilioAdapter.preconnect(adapterConfig.getMyAddress(), TEST_PUBLIC_KEY,
                                                       remoteAddressVoice, "outbound-dial", testCallId1);
        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Gather method=\"GET\" numDigits=\"1\" finishOnKey=\"\" action=\"http://%s/dialoghandler/rest/twilio/answer\" "
                                                                                        + "timeout=\"5\"><Say language=\"nl-NL\">%s</Say><Say language=\"nl-NL\">"
                                                                                        + "%s</Say><Say language=\"nl-NL\">%s</Say></Gather><Redirect method=\"GET\">"
                                                                                        + "http://%s/dialoghandler/rest/twilio/timeout</Redirect></Response>",
                                                        Settings.HOST, "Incoming call", "1", "2", Settings.HOST),
                                          preconnect.getEntity().toString());

        //answer the preconnect with the ignore reply
        Response answer = twilioAdapter.answer(null, "2", adapterConfig.getMyAddress(), remoteAddressVoice,
                                               "outbound-dial", null, "in-progress", testCallId1);
        assertEquals("<Response><Say language=\"nl-nl\">You chose 2</Say><Hangup></Hangup></Response>".toLowerCase(),
                     answer.getEntity().toString().toLowerCase());

        //mock new redirect call to the second number
        answer = twilioAdapter.answer(null, null, adapterConfig.getMyAddress(), inboundAddress, "inbound", null,
                                      "in-progress", testCallId);

        //a new referral session must have been created from testCallId as the parent external sessionId
        Session sessionFromParentExternalId = Session.getSessionFromParentExternalId(testCallId2, testCallId);
        Assert.assertThat(sessionFromParentExternalId, Matchers.notNullValue());

        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Play>%s</Play><Dial action=\"http://%s/dialoghandler/rest/twilio/answer\" method=\"GET\"  "
                                                                                        + "callerId =\"%s\" timeout=\"30\"><Number method=\"GET\" url=\"http://%s/dialoghandler/rest/twilio/preconnect\">"
                                                                                        + "%s</Number></Dial></Response>",
                                                        COMMENT_QUESTION_AUDIO, Settings.HOST,
                                                        adapterConfig.getMyAddress(), Settings.HOST,
                                                        PhoneNumberUtils.formatNumber(secondRemoteAddress, null)),
                                          answer.getEntity().toString());

        preconnect = twilioAdapter.preconnect(adapterConfig.getMyAddress(), TEST_PUBLIC_KEY, secondRemoteAddress,
                                              "outbound-dial", testCallId2);
        assertXMLGeneratedByTwilioLibrary(String.format("<Response><Gather numDigits=\"1\" finishOnKey=\"\" action=\"http://%s/dialoghandler/rest/twilio/answer\" "
                                                                                        + "method=\"GET\" timeout=\"5\"><Say language=\"nl-NL\">%s</Say><Say language=\"nl-NL\">"
                                                                                        + "%s</Say><Say language=\"nl-NL\">%s</Say></Gather><Redirect method=\"GET\">"
                                                                                        + "http://%s/dialoghandler/rest/twilio/timeout</Redirect></Response>",
                                                        Settings.HOST, "Incoming call", "1", "2", Settings.HOST),
                                          preconnect.getEntity().toString());

        answer = twilioAdapter.answer(null, "1", adapterConfig.getMyAddress(), inboundAddress, "inbound", null,
                                      "in-progress", testCallId2);
        assertXMLGeneratedByTwilioLibrary("<Response><Say language=\"nl-NL\">You chose 1</Say></Response>", answer
                                        .getEntity().toString());
    }
}
