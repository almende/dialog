package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;

import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TPAdapter.Return;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.askfast.strowger.sdk.actions.Include;
import com.askfast.strowger.sdk.actions.Play;
import com.askfast.strowger.sdk.actions.StrowgerAction;

public class TPAdapterTest extends TestFramework {

    Logger log = Logger.getLogger( TwilioAdapterTest.class.getName() );

    protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    protected static final String COMMENT_QUESTION_TEXT = "text://Hello World";
    protected static final String REFERRAL_PHONE_NUMBER = "tel:0643002549";
    
    protected static final String secondRemoteAddress = "0612345678";

    @Test
    public void renderCommentQuestionTest() throws Exception {

        Question question = getCommentQuestion( false );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);
        
        StrowgerAction strowger = new StrowgerAction();
        strowger.addAction( new Play( URI.create("http://audio") ) );
        strowger.addAction( new Include( URI.create(getAnswerUrl()) ) );
        
        assertEquals( strowger.toJson(), result );
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
