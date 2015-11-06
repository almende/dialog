package com.almende.dialog.example.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.hamcrest.Matchers;
import org.junit.Assert;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.AnswerPost;
import com.almende.dialog.model.EventPost;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.Language;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * this Servlet is used in the unit tests
 * @author Shravan
 */
public class TestServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    public static final String APPOINTMENT_MAIN_QUESTION = "Are you available today?";
    public static final String OPEN_QUESTION_URL_WITH_SPACES = "/URL WITH SPACES";
    public static final String PLAIN_TEXT_QUESTION = "/PLAIN%20TEXT";
    public static final String APPOINTMENT_YES_ANSWER = "Yup";
    public static final String APPOINTMENT_NO_ANSWER = "Nope";
    public static final String APPOINTMENT_FREE_ANSWER = "Free";
    public static final String APPOINTMENT_SECOND_QUESION = "How long are you available? (in mins)";
    public static final String APPOINTMENT_REJECT_RESPONSE = "Thanks for responding to the invitation!";
    public static final String APPOINTMENT_ACCEPTANCE_RESPONSE = "Thanks for accepting the invitation!";
    public static String TEST_SERVLET_PATH;
    
    //used for local caching of question for testing
    public static String responseQuestionString = "" ;
    private static Map<String, Object> logObject;
    
    private static final Logger log = Logger.getLogger( TestServlet.class.getSimpleName() );
    
    /**
     * simple enum to generate different questions formats
     * @author Shravan
     */
    public enum QuestionInRequest {
        SECURED,
        APPOINTMENT,
        TWELVE_INPUT,
        SIMPLE_COMMENT,
        OPEN_QUESTION,
        OPEN_QUESION_WITHOUT_ANSWERS,
        CLOSED_YES_NO,
        URL_QUESTION_TEXT,
        PLAIN_TEXT_QUESION,
        REFERRAL,
        EXIT;
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            validatePayload(req.getRequestURL() + "?" + req.getQueryString(), null);
        }
        catch (Exception e1) {
            throw new ServletException(e1);
        }
        String result = "";
        String questionType = req.getParameter("questionType");
        if (req.getParameter("secured") != null) {
            if (req.getHeader("Authorization") != null) {
                log.info("Login via Authorization header");
                String header = req.getHeader("Authorization");
                assert header.substring(0, 6).equals("Basic ");
                String basicAuthEncoded = header.substring(6);
                String userPass = new String(new Base64().decode(basicAuthEncoded));
                String[] userPassArray = userPass.split(":");
                if (!"testusername".equalsIgnoreCase(userPassArray[0]) ||
                    !"testpassword".equalsIgnoreCase(userPassArray[1])) {
                    result = null;
                    throw new ServletException("Insecure test servlet access");
                }
            }
        }
        result = getQuestionFromRequest(req, questionType);
        //store all the questions loaded in the TestFramework
        if (result != null && !result.isEmpty()) {
            try {
                storeResponseQuestionInThread(getResponseQuestionWithOptionsInString(result));
            }
            catch (Exception e) {
                Assert.fail("Exception is not expected to be thrown. " + e.getLocalizedMessage());
            }
        }
        if (result == null || result.isEmpty() &&
            OPEN_QUESTION_URL_WITH_SPACES.equals(URLDecoder.decode(req.getPathInfo(), "UTF-8"))) {
            result = getJsonSimpleOpenQuestion(req.getParameter("question"), req.getParameter("lang"));
        }
        else if (result == null || result.isEmpty() &&
                 req.getPathInfo().startsWith(URLDecoder.decode(PLAIN_TEXT_QUESTION, "UTF-8"))) {
            result = URLDecoder.decode(req.getPathInfo().substring(URLDecoder.decode(PLAIN_TEXT_QUESTION, "UTF-8")
                                                                                                   .length() + 1),
                                       "UTF-8");
        }
        TestServlet.logForTest("question", result);
        resp.getWriter().write(result);
        resp.setHeader("Content-Type", MediaType.APPLICATION_JSON);
    }

    /**
     * @param req
     * @param result
     * @param questionType
     * @return
     * @throws UnsupportedEncodingException
     */
    public String getQuestionFromRequest(HttpServletRequest req, String questionType)
        throws UnsupportedEncodingException {

        String result = "";
        if (questionType != null) {
            switch (QuestionInRequest.valueOf(questionType)) {
                case APPOINTMENT:
                    result = getAppointmentQuestion(req.getParameter("question"),
                                                    Boolean.parseBoolean(req.getParameter("byDtmf")),
                                                    req.getParameter("yesDtmf"), req.getParameter("noDtmf"));
                    break;
                case TWELVE_INPUT:
                    result = getTwelveAnswerQuestion(req.getParameter("question"));
                    break;
                case SIMPLE_COMMENT:
                    result = getJsonSimpleCommentQuestion(req.getParameter("question"), req.getParameter("lang"), req.getParameter("callback"));
                    break;
                case OPEN_QUESTION:
                    result = getJsonSimpleOpenQuestion(req.getParameter("question"), req.getParameter("lang"));
                    break;
                case PLAIN_TEXT_QUESION:
                    result = req.getParameter("question");
                    break;
                case OPEN_QUESION_WITHOUT_ANSWERS:
                    result = getJsonSimpleOpenQuestionWithoutAnswers(req.getParameter("question"));
                    break;
                case REFERRAL:
                    result = getReferralQuestion(req.getParameter("address"), req.getParameter("question"),
                                                 req.getParameter("preconnect"), req.getParameter("answerText"),
                                                 req.getParameter("next"));
                    break;
                case EXIT:
                    result = getExitQuestion(req.getParameter("question"));
                    break;
                case CLOSED_YES_NO:
                    String preMessage = req.getParameter("preMessage");
                    if(preMessage!=null) {
                        String callback = TEST_SERVLET_PATH + "?questionType=" +
                        QuestionInRequest.CLOSED_YES_NO + "&question=" + req.getParameter("question");
                        
                        if(req.getParameter("lang") != null) {
                            callback += "&lang=" + req.getParameter("lang");
                        }
                        
                        if(req.getParameter("prefix1") != null) {
                            callback += "&prefix1=" + req.getParameter("prefix1");
                        }
                        
                        if(req.getParameter("prefix2") != null) {
                            callback += "&prefix2=" + req.getParameter("prefix2");
                        }
                        result = getJsonSimpleCommentQuestion( preMessage, req.getParameter("lang"), callback );
                    } else {
                        Map<String, String> answerAndCallback = new LinkedHashMap<String, String>();
                        String prefix1 = req.getParameter("prefix1") != null ? req.getParameter("prefix1") : "text://";
                        prefix1 = prefix1.equals("null") ? null : prefix1;
                        String answerText1 = prefix1 != null ? prefix1 + "1" : null;
                        String prefix2 = req.getParameter("prefix2") != null ? req.getParameter("prefix2") : "text://";
                        prefix2 = prefix2.equals("null") ? null : prefix2;
                        String answerText2 = prefix2 != null ? prefix2 + "2" : null;
                        answerAndCallback.put(answerText1, TEST_SERVLET_PATH + "?questionType=" +
                            QuestionInRequest.SIMPLE_COMMENT + "&question=" + "You chose 1");
                        answerAndCallback.put(answerText2, TEST_SERVLET_PATH + "?questionType=" + QuestionInRequest.EXIT +
                            "&question=You chose 2");
                        result = getClosedQuestion(req.getParameter("question"), answerAndCallback);
                    }
                default:
                    break;
            }
        }
        return result;
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String result = "";
        String appointmentTag = req.getParameter("appointment");
        StringBuffer jb = new StringBuffer();
        String line = null;
        BufferedReader reader = req.getReader();
        while ((line = reader.readLine()) != null) {
            jb.append(line);
        }
        logForTest("postPayload", jb.toString());
        try {
            validatePayload(req.getRequestURL() + "?" + req.getQueryString(), new String(jb.toString()));
        }
        catch (Exception e1) {
            e1.printStackTrace();
            Assert.fail("POST payload retrieval failed. Message: " + e1.getLocalizedMessage());
            result = null;
            throw new ServletException(e1);
        }
        if (appointmentTag != null) {
            result = getAppointmentQuestion(appointmentTag, Boolean.parseBoolean(req.getParameter("byDtmf")),
                                            req.getParameter("yesDtmf"), req.getParameter("noDtmf"));
            //store all the questions loaded in the TestFramework
            try {
                storeResponseQuestionInThread(getResponseQuestionWithOptionsInString(result));
            }
            catch (Exception e) {
                Assert.fail("Exception is not expected to be thrown. " + e.getLocalizedMessage());
            }
        }
        else if (req.getParameter("questionType") != null) {
            result = getQuestionFromRequest(req, req.getParameter("questionType"));
        }
        resp.getWriter().write(result);
        resp.setHeader("Content-Type", MediaType.APPLICATION_JSON);
    }

    @Override
    protected void doPut( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        super.doPut( req, resp );
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        TestServlet.logForTest("url", req.getServletPath() + req.getPathInfo());
    }
    
    public static String getJsonSimpleCommentQuestion(String questionText, String language, String callback) {

        Question question = new Question();
        question.setQuestion_id("1");
        question.setType("comment");
        if(language!=null) {
            question.setPreferred_language(Language.getByValue(language).getCode());
        }
        try {
            question.setQuestion_text("text://" + URLDecoder.decode(questionText, "UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            Assert.fail(e.getLocalizedMessage());
        }
        if(callback!=null) {
            List<Answer> answers = Arrays.asList( new Answer( "", callback )  );
            question.setAnswers( new ArrayList<Answer>(answers) );
        }
        question.generateIds();
        
        return question.toJSON();
    }
    
    public static void storeResponseQuestionInThread(String questionText)
    {
        if(questionText != null && !questionText.isEmpty())
        {
            responseQuestionString = questionText;
        }
    }
    
    /**
     * cache stuff for local unit testing
     * @param log
     */
    public static void logForTest(String key, Object log)
    {
        TestServlet.log.info( "LogForTest: "+ log.toString() );
        logObject = logObject != null ? logObject : new HashMap<String, Object>();
        logObject.put(key, log);
    }

    public static Object getLogObject(String key) {

        return logObject != null ? logObject.get(key) : null;
    }
    
    public static void clearLogObject() {

        logObject = null;
    }
    
    private String getJsonSimpleOpenQuestionWithoutAnswers( String questionText )
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "open" );
        if(questionText.startsWith( "http://" ))
        {
            question.setQuestion_text( questionText );
        }
        else
        {
            question.setQuestion_text( "text://" + questionText );
        }
        question.generateIds();
        try
        {
            return ServerUtils.serialize( question );
        }
        catch ( Exception e )
        {
            Assert.fail("exception not expected. "+ e.getLocalizedMessage());
            return null;
        }
    }
    
    private String getJsonSimpleOpenQuestion(String questionText, String language) throws UnsupportedEncodingException {

        Question question = new Question();
        question.setQuestion_id("1");
        question.setType("open");
        if (questionText.startsWith("http://")) {
            question.setQuestion_text(questionText);
        }
        else {
            question.setQuestion_text("text://" + questionText);
        }
        String callback = ServerUtils.getURLWithQueryParams(TEST_SERVLET_PATH, "questionType",
                                                            QuestionInRequest.SIMPLE_COMMENT.name());
        callback = ServerUtils.getURLWithQueryParams(callback, "question", "Simple%20Comment");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(new Answer("Test answer", callback))));
        question.generateIds();
        question.setPreferred_language(Language.getByValue(language).getCode());
        question.addEventCallback(UUID.randomUUID().toString(), "timeout", callback);
        try {
            return ServerUtils.serialize(question);
        }
        catch (Exception e) {
            Assert.fail("exception not expected. " + e.getLocalizedMessage());
            return null;
        }
    }
    
    public static String getJsonAppointmentQuestion(boolean byDtmf, String yesDTMf, String noDtmf)
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "closed" );
        question.setQuestion_text( "text://" + APPOINTMENT_MAIN_QUESTION );
        question.addEventCallback( null, "delivered", TEST_SERVLET_PATH );
        Answer yesAnswer = null;
        Answer noAnswer = null; 

        if (!byDtmf) {
            yesAnswer = new Answer("text://" + APPOINTMENT_YES_ANSWER, TEST_SERVLET_PATH + "?appointment=" +
                APPOINTMENT_YES_ANSWER);
            noAnswer = new Answer("text://" + APPOINTMENT_NO_ANSWER, TEST_SERVLET_PATH + "?appointment=" +
                APPOINTMENT_NO_ANSWER);
        }
        else {
            yesDTMf = yesDTMf != null ? yesDTMf : "1";
            noDtmf = noDtmf != null ? noDtmf : "2";
            yesAnswer = new Answer("dtmfKey://" + yesDTMf, TEST_SERVLET_PATH + "?appointment=" + APPOINTMENT_YES_ANSWER);
            noAnswer = new Answer("dtmfKey://" + noDtmf, TEST_SERVLET_PATH + "?appointment=" + APPOINTMENT_NO_ANSWER);
        }
        
        //set the answers in the question
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( yesAnswer, noAnswer ) ));
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getJsonAppointmentYesQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "open" );
        question.setQuestion_text( "text://"+ APPOINTMENT_SECOND_QUESION );
        
        Answer openAnswer = new Answer( "text://", TEST_SERVLET_PATH + "?appointment="+ APPOINTMENT_FREE_ANSWER );
        
        //set the answers in the question
        question.setAnswers( new ArrayList<Answer>(Arrays.asList( openAnswer )));
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getJsonAppointmentNoQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "comment" );
        question.setQuestion_text( "text://" + APPOINTMENT_REJECT_RESPONSE );
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getJsonAppointmentFreeQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "comment" );
        question.setQuestion_text( "text://"+ APPOINTMENT_ACCEPTANCE_RESPONSE );
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getClosedQuestion(String questionText, Map<String, String> answerAndCallback)
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "closed" );
        question.setQuestion_text( "text://" + questionText );
        Collection<Answer> allAnswers = new ArrayList<Answer>();
        for (String answer : answerAndCallback.keySet()) {
            allAnswers.add(new Answer(answer, answerAndCallback.get(answer)));
        }
        //set the answers in the question
        question.setAnswers(new ArrayList<Answer>(allAnswers));
        question.generateIds();
        return question.toJSON();
    }
    
    public String getReferralQuestion(String address, String message, String preconnect, String answerText,
        String answerNext) {

        Question question = new Question();
        address = address != null ? address : "";
        if (address != null && !address.isEmpty()) {
            if (preconnect != null) {
                try {
                    preconnect = preconnect.replace(" ", URLEncoder.encode(" ", "UTF-8"));
                }
                catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            question.setType("referral");
            question.setUrl("tel:" + address);
            if (message != null) {
                question.setQuestion_text(message);
            }
            if (answerNext != null) {
                question.setAnswers(new ArrayList<Answer>(Arrays.asList(new Answer(answerText, answerNext))));
            }
            if (preconnect != null && !preconnect.isEmpty()) {
                question.addEventCallback(UUID.randomUUID().toString(), "preconnect", preconnect);
                //add use preconnect media property
                MediaProperty mediaProperty = new MediaProperty();
                mediaProperty.setMedium(MediaProperty.MediumType.BROADSOFT);
                mediaProperty.addProperty(MediaProperty.MediaPropertyKey.USE_PRECONNECT, "true");
                question.addMedia_Properties(mediaProperty);
            }
        }
        return question.toJSON();
    }
    
    public String getExitQuestion(String message) {

        Question question = new Question();
        question.setType("exit");
        if (message != null) {
            question.setQuestion_text(message);
        }
        return question.toJSON();
    }
    
    /**
     * returns a String format of a question. used for testing.
     * E.g. Are you available today?
            [ Yup | Nope ]
     * @param questionJSON
     * @return
     * @throws Exception 
     */
    public static String getResponseQuestionWithOptionsInString(String questionJSON) throws Exception {

        Question question = ServerUtils.deserialize(questionJSON, false, Question.class);
        if (question != null) {
            String result = question.getQuestion_expandedtext(null);
            if (question.getAnswers() != null && question.getType().equals("closed")) {
                result = question.getTextWithAnswerTexts(null);
            }
            return result;
        }
        else {
            return questionJSON;
        }
    }
    
    /**
     * @param appointmentTag
     * @return
     */
    private String getAppointmentQuestion(String appointmentTag, boolean byDtmf, String yesDtmf, String noDtmf) {

        String result;
        if (appointmentTag.equals("start")) {
            result = getJsonAppointmentQuestion(byDtmf, yesDtmf, noDtmf);
        }
        else if (appointmentTag.equals(APPOINTMENT_YES_ANSWER)) {
            result = getJsonAppointmentYesQuestion();
        }
        else if (appointmentTag.equals(APPOINTMENT_NO_ANSWER)) {
            result = getJsonAppointmentNoQuestion();
        }
        else if (appointmentTag.equals(APPOINTMENT_FREE_ANSWER)) {
            result = getJsonAppointmentFreeQuestion();
        }
        else {
            result = getJsonAppointmentQuestion(byDtmf, yesDtmf, noDtmf);
        }
        return result;
    }
    
    /**
     * @param key
     * @return
     * @throws UnsupportedEncodingException 
     */
    private String getTwelveAnswerQuestion(String key) throws UnsupportedEncodingException {

        String result;
        switch (key) {
            case "start":
                Question question = new Question();
                question.setQuestion_id("1");
                question.setType("closed");
                question.setQuestion_text("text://" + APPOINTMENT_MAIN_QUESTION);
                question.addEventCallback(null, "delivered", TEST_SERVLET_PATH);
                ArrayList<Answer> answers = new ArrayList<Answer>();
                for (int answerCount = 1; answerCount < 13; answerCount++) {
                    String dtmfKey = String.valueOf(answerCount);
                    if (answerCount == 10) {
                        dtmfKey = "*";
                    }
                    else if (answerCount == 11) {
                        dtmfKey = "0";
                    }
                    else if (answerCount == 12) {
                        dtmfKey = "#";
                    }
                    answers.add(new Answer("dtmfKey://" + dtmfKey, TEST_SERVLET_PATH + "?questionType=" +
                        QuestionInRequest.TWELVE_INPUT + "&question=" + URLEncoder.encode(dtmfKey, "UTF-8")));
                }
                question.setAnswers(answers);
                question.generateIds();
                result = question.toJSON();
                break;
            default:
                result = getJsonSimpleCommentQuestion("You pressed: " + key, null, null);
                break;
        }
        return result;
    }
    
    /**
     * Make sure that the payload does not contact any senstive information
     * 
     * @param payload
     * @throws Exception
     */
    private void validatePayload(String url, String payload) throws Exception {

        //ignore all calls from test broadsoft subscriptions
        if (!url.contains(Broadsoft.XSI_ACTIONS)) {
            boolean firstAsserted = false;
            boolean secondAsserted = false;
            boolean thirdAsserted = false;
            //validate that every callback has a sessionKey and responder attached to it
            List<Session> allSessions = Session.getAllSessions();
            if (allSessions != null && !allSessions.isEmpty()) {
                
                Session currentSession = allSessions.iterator().next();
                for (NameValuePair nameValuePair : new URIBuilder(url).getQueryParams()) {
                    if (nameValuePair.getName().equals("responder")) {
                        firstAsserted = true;
                        Assert.assertTrue(nameValuePair.getValue() != null);
                        if (!currentSession.getType().equalsIgnoreCase(AdapterType.EMAIL.getName())) {
                            Assert.assertTrue("Responder is not expected to have sip address",
                                              !nameValuePair.getValue().contains("@"));
                        }
                    }
                    if (nameValuePair.getName().equals("requester")) {
                        if (!currentSession.getType().equalsIgnoreCase(AdapterType.EMAIL.getName())) {
                            Assert.assertTrue("Requester is not expected to have sip address",
                                              !nameValuePair.getValue().contains("@"));
                        }
                        Assert.assertTrue(!nameValuePair.getValue().isEmpty());
                        secondAsserted = true;
                    }
                    if (nameValuePair.getName().equals("sessionKey")) {
                        Assert.assertTrue(!nameValuePair.getValue().isEmpty());
                        thirdAsserted = true;
                    }
                }
                //responder and sessionKeys are only not expected for SMSDeliveryStatus callbacks 
                if (!firstAsserted || !secondAsserted || !thirdAsserted) {
                    log.info("-------------" + url + "--------");
                }
                Assert.assertTrue(firstAsserted);
                Assert.assertTrue(secondAsserted);
                Assert.assertTrue(thirdAsserted);
                if (payload != null) {
                    firstAsserted = false;
                    AnswerPost answerEntity = ServerUtils.deserialize(payload, false, AnswerPost.class);
                    if (answerEntity != null) {
                        Assert.assertThat(answerEntity.getExtras(), Matchers.notNullValue());
                        Assert.assertThat(answerEntity.getExtras().get(AdapterConfig.ACCESS_TOKEN_KEY),
                                          Matchers.nullValue());
                        Assert.assertThat(answerEntity.getExtras().get(AdapterConfig.ACCESS_TOKEN_SECRET_KEY),
                                          Matchers.nullValue());
                        Assert.assertThat(answerEntity.getExtras().get(AdapterConfig.ADAPTER_PROVIDER_KEY),
                                          Matchers.nullValue());
                        Assert.assertThat(answerEntity.getExtras().get(AdapterConfig.XSI_PASSWORD_KEY),
                                          Matchers.nullValue());
                        Assert.assertThat(answerEntity.getExtras().get(AdapterConfig.XSI_USER_KEY),
                                          Matchers.nullValue());
                        Assert.assertThat(answerEntity.getExtras().get(Dialog.DIALOG_BASIC_AUTH_HEADER_KEY),
                                          Matchers.nullValue());
                        firstAsserted = true;
                    }
                    else {
                        EventPost eventEntity = ServerUtils.deserialize(payload, false, EventPost.class);
                        if (eventEntity != null) {
                            Map<String, Object> eventExtras = ServerUtils.deserialize(ServerUtils.serialize(eventEntity.getExtras()),
                                                                                      new TypeReference<Map<String, Object>>() {
                                                                                      });
                            Assert.assertThat(eventExtras, Matchers.notNullValue());
                            Assert.assertThat(eventExtras.get(AdapterConfig.ACCESS_TOKEN_KEY), Matchers.nullValue());
                            Assert.assertThat(eventExtras.get(AdapterConfig.ACCESS_TOKEN_SECRET_KEY),
                                              Matchers.nullValue());
                            Assert.assertThat(eventExtras.get(AdapterConfig.ADAPTER_PROVIDER_KEY), Matchers.nullValue());
                            Assert.assertThat(eventExtras.get(AdapterConfig.XSI_PASSWORD_KEY), Matchers.nullValue());
                            Assert.assertThat(eventExtras.get(AdapterConfig.XSI_USER_KEY), Matchers.nullValue());
                            Assert.assertThat(eventExtras.get(Dialog.DIALOG_BASIC_AUTH_HEADER_KEY),
                                              Matchers.nullValue());
                            firstAsserted = true;
                        }
                    }
                    Assert.assertTrue(firstAsserted);
                }
            }
        }
    }
}
