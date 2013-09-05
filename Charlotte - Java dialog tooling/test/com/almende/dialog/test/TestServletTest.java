package com.almende.dialog.test;

import org.junit.Assert;
import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.model.Question;
import com.sun.research.ws.wadl.HTTPMethods;

public class TestServletTest extends TestFramework
{
    @Test
    public void fetchAppointmentQuestionUsingServletRunnerTest() throws Exception
    {
        String response = fetchResponse( HTTPMethods.GET, TestServlet.TEXT_SERVLET_PATH + "?appointment=start", null );
        Question questionFromJson = Question.fromJSON( response );
        Assert.assertEquals( 2, questionFromJson.getAnswers().size() );
        Assert.assertEquals( "text://Are you available today?", questionFromJson.getQuestion_text() );
    }
}
