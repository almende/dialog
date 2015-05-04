package com.almende.util;

// import java.util.Date;
// import java.util.logging.Logger;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

public class DatastoreThread extends Thread implements java.lang.Runnable {
    private static final Logger LOG = Logger.getLogger( "DialogHandler" );
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
        String url = System.getenv( "DIALOG_HANDLER_MONGO_URL" );
        if(url==null) {
            url = System.getenv( "MONGO_URL" );
        }
        String host = "localhost:27017";
        String db = (isTest ? TEST_DB_NAME : DB_NAME);
        if(url!=null) {
            MongoClientURI uri = new MongoClientURI( url );
            if(uri.getHosts().size()>0) {
                host = uri.getHosts().get( 0 );
            }
            if(uri.getDatabase()!=null) {
                db = uri.getDatabase();
            }
        }
        
        MongoClient mongo = null;
        try
        {
            mongo = new MongoClient( host );
        }
        catch ( IOException e )
        {
            LOG.log( Level.SEVERE, "Couldn't find mongoDB host!", e );
        }
        if ( mongo != null )
        {
            ParallelInit.datastore = mongo.getDB( db );
            ParallelInit.datastoreActive = true;
        }
    }
}
