package com.almende.dialog.accounts;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/recording")
public class Recording {

    private String id = null;
    private String filename = null;
    private String contentType = null;
    private String accountId = null;
    
    private Long creationTime = null;
    
    public Recording() {}
    
    public Recording(String accountId, String id, String contentType) {
        
        this.id = id;
        this.filename = filename+".wav";
        this.contentType = contentType;
        this.accountId = accountId;
        
        this.creationTime = System.currentTimeMillis();
    }
    
    public static Recording createRecording(Recording recording) {
        
        MongoCollection recordings = getCollection();
        recordings.insert( recording );
        return recording;
    }
    
    @GET
    @Path("filename")
    public static Recording getRecording(@PathParam("filename") String filename) {
        ObjectNode query = ParallelInit.om.createObjectNode();
        query.put( "filename", filename );
        
        MongoCollection coll = getCollection();
        Recording recording = coll.findOne(query.toString()).as(Recording.class);
        return recording;
    }
    
    private static MongoCollection getCollection() {

        Jongo jongo = ParallelInit.mm.getJongo();
        return jongo.getCollection("recording");
    }
    
    public String getId() {
        return id;
    }
    
    public void setId( String id ) {
        this.id = id;
    }
    
    public String getFilename() {
        return filename;
    }
    
    public void setFilename( String filename ) {
        this.filename = filename;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType( String contentType ) {
        this.contentType = contentType;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId( String accountId ) {
        this.accountId = accountId;
    }
    
    public Long getCreationTime() {
        return creationTime;
    }
    
    public void setCreationTime( Long creationTime ) {
        this.creationTime = creationTime;
    }
}
