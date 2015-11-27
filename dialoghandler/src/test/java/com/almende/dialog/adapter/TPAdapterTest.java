package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;
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
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.jackson.JOM;
import com.askfast.strowger.sdk.actions.Action;
import com.askfast.strowger.sdk.actions.Dtmf;
import com.askfast.strowger.sdk.actions.Include;
import com.askfast.strowger.sdk.actions.Play;
import com.askfast.strowger.sdk.actions.Record;
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
        strowger.addAction( new Play( Arrays.asList(URI.create("http://audio")) ) );
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

    @Test
    public void renderOpenQuestionTest() throws Exception {

        Question question = getOpenQuestion( false, false );
        AdapterConfig adapter = createTPAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion(question, adapter, session);

        log.info( "Result Open Question: " + result );
        
        StrowgerAction strowger = JOM.getInstance().readValue(result, StrowgerAction.class);
        
        List<Action> actions = strowger.getData();
        Action action = actions.get(0);
        assertThat(action, instanceOf(Dtmf.class));
        Dtmf dtmf = (Dtmf) action;        
        
        Play play = dtmf.getPlay();
        action = actions.get(1);
        assertThat(action, instanceOf(Include.class));
        Include include = (Include) action;
        
        assertThat(dtmf.getUrl().toString(), endsWith("/dialoghandler/rest/strowger/answer"));
        
        assertEquals( "http://audio", play.getLocations().get(0).toString() );
        assertThat(include.getLocation().toASCIIString(), endsWith("/dialoghandler/rest/strowger/timeout"));
    }

    @Test
    public void renderOpenAudioQuestionTest() throws Exception {

        Question question = getOpenQuestion( false, true );
        AdapterConfig adapter = createTPAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        log.info( "Result Open Question: " + result );

        StrowgerAction strowger = JOM.getInstance().readValue(result, StrowgerAction.class);
        List<Action> actions = strowger.getData();
        Action action = actions.get(0);
        assertThat(action, instanceOf(Play.class));
        Play play = (Play) action;
        
        action = actions.get(1);
        assertThat(action, instanceOf(Record.class));
        Record record = (Record) action;
        
        action = actions.get(2);
        assertThat(action, instanceOf(Include.class));
        Include include = (Include) action;

        assertEquals( "http://audio", play.getLocations().get(0).toASCIIString() );

        assertThat(record.getUrl().toASCIIString(), endsWith("/dialoghandler/rest/strowger/answer"));
        
        assertThat(include.getLocation().toASCIIString(), endsWith("/dialoghandler/rest/strowger/timeout"));
    }

    @Test
    public void renderClosedQuestionTest() throws Exception {

        Question question = getClosedQuestion( false );
        AdapterConfig adapter = createTPAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);
        StrowgerAction strowger = JOM.getInstance().readValue(result, StrowgerAction.class);
        
        List<Action> actions = strowger.getData();
        Action action = actions.get(0);
        assertThat(action, instanceOf(Dtmf.class));
        Dtmf dtmf = (Dtmf) action;
        action = actions.get(1);
        assertThat(action, instanceOf(Include.class));
        Include include = (Include) action;
        
        assertThat(dtmf.getUrl().toASCIIString(), endsWith("/dialoghandler/rest/strowger/answer"));
        
        Play play = dtmf.getPlay();
        List<URI> locations = play.getLocations();
        URI url1 = locations.get(0);
        URI url2 = locations.get(1);
        URI url3 = locations.get(2);

        assertEquals( "http://audio", url1.toASCIIString() );
        assertEquals( "http://answer1.wav", url2.toASCIIString() );
        assertEquals( "http://answer2.wav", url3.toASCIIString() );
        assertThat(include.getLocation().toASCIIString(), endsWith("/dialoghandler/rest/strowger/timeout"));
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
        else if (question.getType().equalsIgnoreCase("open")) {
            return servlet.renderOpenQuestion(question, res.prompts, session);
        }
        else if (question.getType().equalsIgnoreCase("closed")) {
            return servlet.renderClosedQuestion(question, res.prompts, session);
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
    
    private Question getClosedQuestion( boolean tts ) {

        Question question = new Question();
        question.setQuestion_id( COMMENT_QUESTION_ID );
        question.setType( "closed" );
        if ( tts ) {
            question.setQuestion_text( COMMENT_QUESTION_TEXT );
        }
        else {
            question.setQuestion_text( COMMENT_QUESTION_AUDIO );
        }

        Answer answer1 = new Answer( "http://answer1.wav", "/next" );
        Answer answer2 = new Answer( "http://answer2.wav", "/next" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( answer1,
                                                                   answer2 ) ) );

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getOpenQuestion( boolean tts, boolean audio ) {

        Question question = new Question();
        question.setQuestion_id( COMMENT_QUESTION_ID );
        question.setType( "open" );
        if ( tts ) {
            question.setQuestion_text( COMMENT_QUESTION_TEXT );
        }
        else {
            question.setQuestion_text( COMMENT_QUESTION_AUDIO );
        }

        Answer answer = new Answer( "http://answer.wav", "/next" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( answer ) ) );

        if ( audio ) {
            MediaProperty mp = new MediaProperty();
            mp.setMedium( MediumType.BROADSOFT );
            mp.addProperty( MediaPropertyKey.TYPE, "audio" );
            question.addMedia_Properties( mp );
        }

        // set the answers in the question
        question.generateIds();
        return question;
    }
}