package com.almende.util;

// import java.util.Date;
// import java.util.logging.Logger;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.MongoClient;

public class DatastoreThread extends Thread implements java.lang.Runnable {
	private static final Logger	LOG	= Logger.getLogger("DialogHandler");
	private boolean isTest = false;
	public static final String TEST_DB_NAME = "test-dialog";
	public static final String DB_NAME = "dialog";
	
	public DatastoreThread()
    {
	    new DatastoreThread(false);
    }
	
	public DatastoreThread(boolean isTest)
    {
	    this.isTest = isTest;
    }
	
    @Override
    public void run()
    {
        int port = 27017;
        MongoClient mongo = null;
        try
        {
            mongo = new MongoClient( "localhost", port );
        }
        catch ( IOException e )
        {
            LOG.log( Level.SEVERE, "Couldn't find mongoDB host!", e );
        }
        if ( mongo != null )
        {
            ParallelInit.datastore = mongo.getDB( isTest ? TEST_DB_NAME : DB_NAME );
            ParallelInit.datastoreActive = true;
        }
    }
}
