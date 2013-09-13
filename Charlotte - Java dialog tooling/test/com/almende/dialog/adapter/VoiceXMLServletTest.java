package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.VoiceXMLRESTProxy.Return;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.MediaProperty.MediumType;

public class VoiceXMLServletTest extends TestFramework {

    protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio.wav";

    private Question getCommentQuestion() {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("comment");
        question.setQuestion_text(COMMENT_QUESTION_AUDIO);

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));

        // set the answers in the question
        question.generateIds();
        return question;
    }

    private Question getOpenDTMFQuestion() {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType( "open" );
        question.setQuestion_text(COMMENT_QUESTION_AUDIO);

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getOpenAudioQuestion() {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType( "open" );
        question.setQuestion_text(COMMENT_QUESTION_AUDIO);

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));
        
        MediaProperty property = new MediaProperty();
        property.setMedium(MediumType.BROADSOFT);
        property.addProperty(MediaPropertyKey.TYPE, "AudIO");
        
        question.addMedia_Properties(property);

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private String renderQuestion(Question question, AdapterConfig adapter, String sessionKey) throws Exception {
        
        VoiceXMLRESTProxy servlet = new VoiceXMLRESTProxy();
        Return res = servlet.formQuestion(question, adapter.getConfigId(),
                remoteAddressVoice);
        
        if(question.getType().equalsIgnoreCase("comment")) {
            return servlet.renderComment(res.question, res.prompts, sessionKey);            
        } else if(question.getType().equalsIgnoreCase("referral")) {
            
        } else if(question.getType().equalsIgnoreCase("open")) {
            return servlet.renderOpenQuestion(res.question, res.prompts, sessionKey);
        } else if(question.getType().equalsIgnoreCase("closed")) {
            
        }
        
        return null;
    }

    @Test
    public void renderCommentQuestionTest() throws Exception {
        
        Question question = getCommentQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        Document doc = getXMLDocumentBuilder(result);
        Node vxml = doc.getFirstChild();
        Node form = vxml.getFirstChild();

        Node block = form.getFirstChild();
        Node prompt = block.getFirstChild();
        Node _goto = prompt.getNextSibling();

        assertNotNull(doc);
        assertEquals(doc.getChildNodes().getLength(), 1);
        assertEquals(vxml.getNodeName(), "vxml");
        assertEquals(form.getNodeName(), "form");

        assertEquals(block.getChildNodes().getLength(), 2);
        assertEquals(COMMENT_QUESTION_AUDIO, prompt.getFirstChild()
                .getAttributes().getNamedItem("src").getNodeValue());

        assertEquals("/vxml/answer?question_id=" + COMMENT_QUESTION_ID
                + "&sessionKey=" + sessionKey, _goto.getAttributes()
                .getNamedItem("next").getNodeValue());
    }

    @Test
    public void renderReferralQuestionTest() {

    }

    @Test
    public void renderClosedQuestionTest() {

    }

    @Test
    public void renderOpenQuestionWithTypeDTMFTest() throws Exception {

        Question question = getOpenDTMFQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);

        Document doc = getXMLDocumentBuilder(result);
        Node vxml = doc.getFirstChild();
        Node form = vxml.getChildNodes().item(3);

        Node field = form.getFirstChild();
        
        assertNotNull(doc);
        assertEquals(doc.getChildNodes().getLength(), 1);
        assertEquals(vxml.getNodeName(), "vxml");
        assertEquals(form.getNodeName(), "form");
        assertEquals(field.getNodeName(), "field");
        
        assertEquals(field.getChildNodes().getLength(), 4);
    }

    @Test
    public void renderOpenQuestionWithTypeAudioTest() throws Exception {

        Question question = getOpenAudioQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        log(result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node vxml = doc.getFirstChild();
        Node form = vxml.getFirstChild();
        
        Node record = form.getFirstChild();
        Node subdialog = record.getNextSibling();
        
        assertNotNull(doc);
        assertEquals(doc.getChildNodes().getLength(), 1);
        assertEquals(vxml.getNodeName(), "vxml");
        assertEquals(form.getNodeName(), "form");
        assertNotEquals(form.getChildNodes().getLength(), 4);
        assertEquals(record.getNodeName(), "record");
        assertEquals(subdialog.getNodeName(), "subdialog");
    }
}
