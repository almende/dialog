package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.sim.TPSimulator;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.Language;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.strowger.sdk.actions.Action;
import com.askfast.strowger.sdk.actions.Include;
import com.askfast.strowger.sdk.actions.Play;
import com.askfast.strowger.sdk.actions.StrowgerAction;

@Category(IntegrationTest.class)
public class TPAdapterIT extends TestFramework {

    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    private DialogAgent dialogAgent = null;
    private static final String TEST_MESSAGE_1 = "How are you doing? today";
    private static final String TEST_MESSAGE_2 = "Thanks";
    
    @Test
    public void inboundPhoneCall_CommentTest() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();

        String tenantKey = UUID.randomUUID().toString();
        String callSid = UUID.randomUUID().toString();
        
        TPSimulator simulator = new TPSimulator( TestFramework.host, tenantKey );
        //TwilioSimulator simulator = new TwilioSimulator(TestFramework.host, accountSid);
        

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

        //create SMS adapter
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
        assertTrue( action instanceof Play);
        Play play = (Play) action;
        assertTrue(play.getLocation().toString().startsWith( "http://tts.ask-fast.com" ));
        action = actual.getData().get(1 );
        assertTrue( action instanceof Include);
        
        simulator.nextQuestion( null );
        
        actual = simulator.getReponse();
        
        assertEquals(1, actual.getData().size());
        action = actual.getData().get( 0 );
        assertTrue( action instanceof Play);
        
        //check all the ddrs created
        List<DDRRecord> ddrRecords = getAllDdrRecords( TEST_ACCOUNT_ID );
        
        assertEquals(ddrRecords.size(), 1);
        for (DDRRecord ddrRecord : ddrRecords) {
            assertEquals("inbound", ddrRecord.getDirection());
            assertEquals(adapterConfig.getFormattedMyAddress(), ddrRecord.getToAddress().keySet().iterator().next());
            assertEquals(PhoneNumberUtils.formatNumber(remoteAddressVoice, null), ddrRecord.getFromAddress());
        }
    }
}