package com.almende.dialog.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Map;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.util.jackson.JOM;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class QuestionTest extends TestFramework {

    @Test
    public void parseEmptyQuestionJSON() {
        Question question;
        
        String json = "";
        question = Question.fromJSON(json);
        assertNull(question);
        
        json = "{}";
        question = Question.fromJSON(json);
        assertNotNull(question);
    }
    
    @Test
    public void parseReferralQuestionJSON() {
        
        Question question;
        
        ObjectNode json1 = JOM.createObjectNode();
        json1.put( "question_id", 1 );
        json1.put( "question_text", "http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav" );
        json1.put( "type", "referral" );
        json1.set( "url", JOM.createNullNode() );
        json1.set( "requester", JOM.createNullNode() );
        
        //String json1 = "{\"question_id\":1,\"question_text\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav\",\"type\":\"referral\",\"url\":null,\"requester\":null}";
        
        question = Question.fromJSON(json1.toString());
        assertNotNull(question.getQuestion_text());
        assertEquals(question.getQuestion_text(),"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav");
        assertNull(question.getAnswers());
        assertNull(question.getEvent_callbacks());
        assertNull(question.getMedia_properties());
        
        ArrayNode urls = JOM.createArrayNode();
        urls.add( "tel:0612345678" );
        urls.add( "tel:0612345679" );
        
        ObjectNode json2 = JOM.createObjectNode();
        json2.put( "question_id", 1 );
        json2.put( "question_text", "http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav" );
        json2.put( "type", "referral" );
        json2.set( "url", urls );
        json2.set( "requester", JOM.createNullNode() );
        
        //String json2 = "{\"question_id\":1,\"question_text\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav\",\"type\":\"open\",\"url\":null,\"requester\":null,\"answers\":[{\"answer_id\":1,\"answer_text\":null,\"callback\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio_open_question.php?function=next\"}],\"event_callbacks\":[],\"media_properties\":[{\"medium\":\"BROADsofT\",\"properties\":{\"tYPe\":\"audio\"}}]}";
        
        question = Question.fromJSON(json2.toString());
        assertNotNull(question.getQuestion_text());
        assertEquals(question.getQuestion_text(), "http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav");
        assertNull(question.getAnswers());
        assertTrue(question.getType().equalsIgnoreCase("Referral"));
        assertEquals(question.getUrl().size(), 2);
        
        String url = "tel:0612345678";
        
        ObjectNode json3 = JOM.createObjectNode();
        json3.put( "question_id", 1 );
        json3.put( "question_text", "http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav" );
        json3.put( "type", "referral" );
        json3.put( "url", url );
        json3.set( "requester", JOM.createNullNode() );
        
        question = Question.fromJSON(json3.toString());
        assertNotNull(question.getQuestion_text());
        assertEquals(question.getQuestion_text(), "http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav");
        assertNull(question.getAnswers());
        assertTrue(question.getType().equalsIgnoreCase("Referral"));
        assertEquals(question.getUrl().size(), 1);
        
        /*Map<MediaProperty.MediaPropertyKey,String> properties = question.getMediaPropertyByType(MediumType.BROADSOFT);
        assertEquals(properties.size(),1);
        
        assertTrue(properties.get(MediaPropertyKey.TYPE).equalsIgnoreCase("AuDiO"));*/
    }
    
    @Test
    public void parseOpenQuestionJSON() {
        
        Question question;
        
        String json1 = "{\"question_id\":1,\"question_text\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav\",\"type\":\"open\",\"url\":null,\"requester\":null}";
        
        question = Question.fromJSON(json1);
        assertNotNull(question.getQuestion_text());
        assertEquals(question.getQuestion_text(),"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav");
        assertNull(question.getAnswers());
        assertNull(question.getEvent_callbacks());
        assertNull(question.getMedia_properties());
        
        String json2 = "{\"question_id\":1,\"question_text\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav\",\"type\":\"open\",\"url\":null,\"requester\":null,\"answers\":[{\"answer_id\":1,\"answer_text\":null,\"callback\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio_open_question.php?function=next\"}],\"event_callbacks\":[],\"media_properties\":[{\"medium\":\"BROADsofT\",\"properties\":{\"tYPe\":\"audio\"}}]}";
        
        question = Question.fromJSON(json2);
        assertNotNull(question.getQuestion_text());
        assertEquals(question.getQuestion_text(), "http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav");
        assertEquals(question.getAnswers().size(),1);
        assertTrue(question.getType().equalsIgnoreCase("Open"));
        
        Map<MediaProperty.MediaPropertyKey,String> properties = question.getMediaPropertyByType(MediumType.BROADSOFT);
        assertEquals(properties.size(),1);
        
        assertTrue(properties.get(MediaPropertyKey.TYPE).equalsIgnoreCase("AuDiO"));
    }
    
    @Test
    public void parseOpenQuestionWithMinMaxDtmfInputMediaPropertiesTest()
    {
        String questionText = "{\"preferred_language\":\"en\",\"question_id\":\"1\",\"question_text\":\"text://How are you doing\","
            + "\"type\":\"open\",\"answers\":[{\"answer_id\":\"6b321a81-6fdf-4f6e-8739-001c8413c883\",\"answer_text\":\"\","
            + "\"callback\":\"http://askfastmarket1.appspot.com/resource/question?url=comment\"}],\"event_callbacks\":[],"
            + "\"media_properties\":[{\"medium\":\"BROADSOFT\",\"properties\":{\"ANSWER_INPUT_MIN_LENGTH\":\"3\","
            + "\"ANSWER_INPUT_MAX_LENGTH\":\"3\"}}]}";
        Question fromJSON = Question.fromJSON(questionText);
        assertEquals( "3",
            fromJSON.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.ANSWER_INPUT_MIN_LENGTH ) );
        assertEquals( "3",
            fromJSON.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH ) );

        assertEquals( "en", fromJSON.getPreferred_language() );
        assertEquals( 1, fromJSON.getMedia_properties().size() );
        assertEquals( "http://askfastmarket1.appspot.com/resource/question?url=comment", fromJSON.getAnswers()
            .iterator().next().getCallback() );
        
        assertTrue( fromJSON.toJSON().contains( "ANSWER_INPUT_MIN_LENGTH\":\"3\"" ) );
    }
}
