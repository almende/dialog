package com.almende.util;

import com.askfast.commons.db.MongoManager;


public class MongoThread extends Thread {
    
    private final String host = "localhost";
    private final int port = 27017;
    private final String database = "dialog";

    @Override
    public void run() {
        String url = System.getProperty( "DIALOG_HANDLER_MONGO_URL" );
        if(url==null) {
            url = System.getProperty( "MONGO_URL" );
        }
        ParallelInit.mm = MongoManager.getInstance();
        if(url!=null) {
            ParallelInit.mm.init( url );
            ParallelInit.mongoActive = true;
            System.out.println("Use MONGO_DIALOG_HANDLER_URL: "+url);
        } else {
            
            ParallelInit.mm.init( host, port, database );
            ParallelInit.mongoActive = true;
        }
    }
}
