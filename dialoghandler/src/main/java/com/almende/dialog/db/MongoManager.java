package com.almende.dialog.db;

import java.net.UnknownHostException;

import org.jongo.Jongo;
import org.jongo.marshall.jackson.JacksonMapper;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.ServerAddress;

public class MongoManager {
    
    private static MongoManager mm = null;
    private MongoClient mongo = null;
    private DB db = null;
    private Jongo jongo = null;
    
    private MongoManager() {
    }
    
    public static MongoManager getInstance() {
        if ( mm == null ) {
            mm = new MongoManager();
        }

        return mm;
    }
    
    public void init( String url) {
        try {
            MongoClientURI uri = new MongoClientURI( url );
            mongo = new MongoClient( uri );
            db = mongo.getDB( uri.getDatabase() );
            jongo = new Jongo( db, new JacksonMapper.Builder().addModifier(new CustomMapperModifier()).build() );
        }
        catch ( UnknownHostException e ) {
            e.printStackTrace();
        }
    }
    
    public void init(String host, int port, String database) {              
        try {
            mongo = new MongoClient( new ServerAddress( host, port ) );
            db = mongo.getDB( database );
            jongo = new Jongo( db, new JacksonMapper.Builder().addModifier(new CustomMapperModifier()).build() );
        }
        catch ( UnknownHostException e ) {
            e.printStackTrace();
        }
    }
    
    public DB getDatabase() {
        return db;
    }
    
    public Jongo getJongo() {
        return jongo;
    }
}
