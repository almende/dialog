package com.almende.dialog.accounts;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;

import com.almende.dialog.aws.AWSClient;
import com.almende.util.ParallelInit;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/recording")
public class Recording {
    
    @JsonIgnore
    private final String BASEPATH = "./blobstore/";

    private String id = null;
    private String filename = null;
    private String contentType = null;
    private String accountId = null;
    private String ddrId = null;
    private String adapterId = null;
    
    private Long creationTime = null;
    
    public Recording() {}
    
    public Recording(String id, String accountId, String contentType, String ddrId, String adapterId) {
        this(id, accountId, id+".wav", contentType, ddrId, adapterId);
    }
    
    public Recording(String id, String accountId, String filename, String contentType, String ddrId, String adapterId) {
        
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.accountId = accountId;
        this.ddrId = ddrId;
        this.adapterId = adapterId;
        
        this.creationTime = System.currentTimeMillis();
    }
    
    public static Recording createRecording(Recording recording) {
        
        MongoCollection recordings = getCollection();
        recordings.insert( recording );
        return recording;
    }
    
    public void delete() {
        ObjectNode query = ParallelInit.getObjectMapper().createObjectNode();
        query.put( "_id", id );
        
        MongoCollection recordings = getCollection();
        recordings.remove( query.toString() );
    }
    
    @GET
    @Path("{filename}")
    public Response getAudioRecording(@PathParam("filename") String filename, @DefaultValue("false") @QueryParam("download") Boolean download, 
                                      @Context HttpServletResponse res) throws IOException {
        
        String id = filename.replace( ".wav", "" );
        Recording recording = Recording.getRecording( id );
        // broadsoft file vs twilio url
        if(recording.getFilename().startsWith( "http:" )) {
            
            res.sendRedirect( recording.getFilename() );
            
        } else {
            
            AWSClient client = ParallelInit.getAWSClient();
            
            String contentType = recording.getContentType();
            if(download && recording.getFilename().endsWith(".wav")) {
                contentType = "audio/basic";
            } else if(recording.getFilename().endsWith(".wav")) {
                contentType = "audio/x-wav";
            }
            
            res.setContentType(contentType);
            
            OutputStream out = new BufferedOutputStream(res.getOutputStream());
            InputStream in = client.getFile( filename );
            
            IOUtils.copy(in, out);
            
            in.close();
            out.flush();
            
            
            /*String contentType = recording.getContentType();
            if(filename.endsWith(".wav")) {
                contentType = "audio/basic";
            }                     
            
            int size = recording.getFileSize();
            String hashCode = recording.getFileHashCode();
            
            res.setContentLength(size);
            res.setContentType(contentType);
            res.setHeader("ETag", hashCode);
            
            OutputStream out = new BufferedOutputStream(res.getOutputStream());
            InputStream in = new BufferedInputStream(new FileInputStream( BASEPATH + recording.getId()));
            
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
            }
            in.close();
            out.flush();*/
        
        }
        
        return null;
    }
    
    public static Recording getRecording(String id, String accountId) {
        ObjectNode query = ParallelInit.getObjectMapper().createObjectNode();
        query.put( "_id", id );
        query.put( "accountId", accountId );
        
        MongoCollection coll = getCollection();
        Recording recording = coll.findOne(query.toString()).as(Recording.class);
        return recording; 
    }
    
    public static Recording getRecording(String id) {
        ObjectNode query = ParallelInit.getObjectMapper().createObjectNode();
        query.put( "_id", id );
        
        MongoCollection coll = getCollection();
        Recording recording = coll.findOne(query.toString()).as(Recording.class);
        return recording; 
    }
    
    public static Recording getRecordingByFilename(String filename) {
        ObjectNode query = ParallelInit.getObjectMapper().createObjectNode();
        query.put( "filename", filename );
        
        MongoCollection coll = getCollection();
        Recording recording = coll.findOne(query.toString()).as(Recording.class);
        return recording;
    }
    
    public static Set<Recording> getRecordings(String accountId, String ddrId, String adapterId) {
        ObjectNode query = ParallelInit.getObjectMapper().createObjectNode();
        query.put( "accountId", accountId );
        
        if(ddrId!=null) {
            query.put( "ddrId", ddrId );
        }
        
        if(adapterId!=null) {
            query.put( "adapterId", adapterId );
        }
        
        MongoCollection coll = getCollection();
        MongoCursor<Recording> cursor = coll.find(query.toString()).as(Recording.class);
        Set<Recording> recordings = new HashSet<Recording>();
        for(Recording recording : cursor) {
            recordings.add( recording );
        }
        return recordings;
    }
    
    private static MongoCollection getCollection() {

        Jongo jongo = ParallelInit.getMongoManager().getJongo();
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
    
    public String getDdrId() {
        return ddrId;
    }
    
    public void setDdrId( String ddrId ) {
        this.ddrId = ddrId;
    }
    
    public String getAdapterId() {
        return adapterId;
    }
    
    public void setAdapterId( String adapterId ) {
        this.adapterId = adapterId;
    }
    
    public Long getCreationTime() {
        return creationTime;
    }
    
    public void setCreationTime( Long creationTime ) {
        this.creationTime = creationTime;
    }
}
