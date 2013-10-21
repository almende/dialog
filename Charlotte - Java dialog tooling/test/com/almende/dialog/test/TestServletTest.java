package com.almende.dialog.test;

import org.junit.Assert;
import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.model.Question;
import com.almende.dialog.test.TestServlet.QuestionInRequest;
import com.almende.dialog.util.ServerUtils;
import com.thetransactioncompany.cors.HTTPMethod;

public class TestServletTest extends TestFramework
{
    @Test
    public void fetchAppointmentQuestionUsingServletRunnerTest() throws Exception
    {
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.APPOINTMENT.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", "start" );
        String response = fetchResponse( HTTPMethod.GET, url, null );
        Question questionFromJson = Question.fromJSON( response, null );
        Assert.assertEquals( 2, questionFromJson.getAnswers().size() );
        Assert.assertEquals( "text://Are you available today?", questionFromJson.getQuestion_text() );
    }
}
