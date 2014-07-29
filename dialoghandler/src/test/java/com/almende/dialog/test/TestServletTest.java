package com.almende.dialog.test;

import javax.ws.rs.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Question;
import com.almende.dialog.util.ServerUtils;

public class TestServletTest extends TestFramework
{
    @Test
    public void fetchAppointmentQuestionUsingServletRunnerTest() throws Exception
    {
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.APPOINTMENT.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", "start" );
        String response = fetchResponse( HttpMethod.GET, url, null );
        Question questionFromJson = Question.fromJSON( response, null, null, null );
        Assert.assertEquals( 2, questionFromJson.getAnswers().size() );
        Assert.assertEquals( "text://Are you available today?", questionFromJson.getQuestion_text() );
    }
}
