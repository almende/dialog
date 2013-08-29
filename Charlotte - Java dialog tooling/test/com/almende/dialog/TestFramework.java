package com.almende.dialog;

import org.junit.After;
import org.junit.Before;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

public class TestFramework
{
    private final LocalServiceTestHelper helper = new LocalServiceTestHelper( new LocalDatastoreServiceTestConfig() );
    
    @Before
    public void setup()
    {
        helper.setUp();
    }
    
    @After
    public void tearDown()
    {
        helper.tearDown();
    }
}
