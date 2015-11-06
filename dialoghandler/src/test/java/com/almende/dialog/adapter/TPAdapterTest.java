package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TPAdapter.Return;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;
import com.askfast.strowger.sdk.actions.Include;
import com.askfast.strowger.sdk.actions.Play;
import com.askfast.strowger.sdk.actions.StrowgerAction;

@Category(IntegrationTest.class)
public class TPAdapterTest extends TestFramework {

    Logger log = Logger.getLogger( TwilioAdapterTest.class.getName() );

    protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    protected static final String COMMENT_QUESTION_TEXT = "text://Hello World";
    protected static final String REFERRAL_PHONE_NUMBER = "tel:0612345679";
    
    protected static final String secondRemoteAddress = "0612345678";

    @Test
    public void renderCommentQuestionTest() throws Exception {

        Question question = getCommentQuestion( false );
        AdapterConfig adapter = createTPAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);
        
        StrowgerAction strowger = new StrowgerAction();
        strowger.addAction( new Play( URI.create("http://audio") ) );
        strowger.addAction( new Include( URI.create(getAnswerUrl()) ) );
        
        assertEquals( strowger.toJson(), result );
    }
    
    /**
     * Test if a comment question is triggered while a broadcast is initiated
     * 
     * @throws Exception
     */
    @Test
    public void renderCommentQuestionWithBroadcastTest() throws Exception {

        AdapterConfig adapter = createTPAdapter();
        HashMap<String, String> addresses = new HashMap<String, String>(1);
        addresses.put(secondRemoteAddress, "");
        addresses.put(remoteAddressVoice, null);
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", COMMENT_QUESTION_AUDIO);
        dialogAgent.outboundCallWithMap(addresses, null, null, null, null, url, null, adapter.getConfigId(),
            TEST_ACCOUNT_ID, null, null);
        //validate that there should be two session
        List<Session> allSessions = Session.getAllSessions();
        Assert.assertThat(allSessions.size(), Matchers.is(2));
    }
    
    private String renderQuestion(Question question, AdapterConfig adapter, Session session) throws Exception {

        TPAdapter servlet = new TPAdapter();
        Return res = servlet.formQuestion(question, adapter.getConfigId(), remoteAddressVoice, null, session,
                                          new HashMap<String, String>());

        if (question != null && !question.getType().equalsIgnoreCase("comment"))
            question = res.question;

        if (question.getType().equalsIgnoreCase("comment")) {
            return servlet.renderComment(question, res.prompts, session);
        }

        return null;
    }
    
    private String getAnswerUrl() {
        return new TPAdapter().getAnswerUrl();
    }
    
    private Question getCommentQuestion( boolean tts ) {

        Question question = new Question();
        question.setQuestion_id( COMMENT_QUESTION_ID );
        question.setType( "comment" );
        if ( tts ) {
            question.setQuestion_text( COMMENT_QUESTION_TEXT );
        }
        else {
            question.setQuestion_text( COMMENT_QUESTION_AUDIO );
        }

        Answer answer = new Answer( "http://answer.wav", "/next" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( answer ) ) );

        // set the answers in the question
        question.generateIds();
        return question;
    }
}
