package com.almende.util;

import com.almende.dialog.db.MongoManager;

public class MongoThread extends Thread {
    
    private final String host = "localhost";
    private final int port = 27017;
    private final String database = "dialog";

    @Override
    public void run() {
        ParallelInit.mm = MongoManager.getInstance();
        ParallelInit.mm.init( host, port, database );
        ParallelInit.mongoActive = true;
    }
}
