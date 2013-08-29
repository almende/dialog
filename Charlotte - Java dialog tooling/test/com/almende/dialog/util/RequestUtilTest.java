package com.almende.dialog.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.wink.common.model.wadl.HTTPMethods;
import org.junit.Assert;
import org.junit.Test;

import com.almende.dialog.TestFramework;

public class RequestUtilTest extends TestFramework
{
    @Test
    public void fetchFromURLTest()
    {
        Map<String, String> queryMap = new HashMap<String, String>();
        queryMap.put( "responder", "test@ask-cs.com" );
        queryMap.put( "requester", "dialogobject@dialog-handler.appspot.com" );
        String question_json = RequestUtil.fromURL( HTTPMethods.GET, "http://askfastmarket1.appspot.com/resource/examples/dummy2", queryMap, null, null );
        Assert.assertTrue( question_json != null );
    }
}
