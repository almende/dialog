package com.almende.dialog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.nio.channels.Channels;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.FancyFileWriter;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileReadChannel;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogService;
import com.google.appengine.api.log.RequestLogs;

@SuppressWarnings("deprecation")
@Path("/ddr")
public class DDR implements Serializable  {
	
	private static final long serialVersionUID = 7594102523777048633L;
	private static final String FILENAME = "ddr";
	private static final String BUCKET = "ddr";
	
	private static ObjectMapper om = ParallelInit.getObjectMapper();
	static final Logger log = Logger.getLogger("DDR");
	LogService ls = ParallelInit.getLogService();
	LogQuery query;
	
	public DDR(){
		query = LogQuery.Builder.withDefaults();
	    query.includeAppLogs(true);
	}
	@GET
	@Produces("application/json")
	public Response getDDRs(@QueryParam("account") String account, @QueryParam("from") String from, @QueryParam("to") String to,
							@QueryParam("adapter") String adapter){
		
		ArrayNode result = om.createArrayNode();
		long start = (System.currentTimeMillis()-86400000)*1000; 
		if(from != null) {
			start = Long.parseLong(from)*1000000;
		}
		//System.out.println("From: "+start);
	    query.startTimeUsec(start);
	    if(to != null) {
	    	long end = Long.parseLong(to)*1000000; 
	    	if(start==end)
	    		end++;
			query.endTimeUsec(end);
		}
	    Iterable<RequestLogs> records = ls.fetch(query);
	    for (RequestLogs record : records) { 
	    	for (AppLogLine appLog : record.getAppLogLines()) {
	    		String msg =appLog.getLogMessage(); 
	    		if (msg.startsWith("com.almende.dialog.DDRWrapper log:")){
					//log.warning("checking record:"+msg);
	    			JsonNode rec;
					try {
						rec = om.readTree(msg.substring(35));
						if (account==null || rec.has("account") && rec.get("account").asText().equals(account)){
							
							if(adapter==null || (rec.has("adapterType") && rec.get("adapterType").asText().equals(adapter))) {
								
								result.add(rec);
							}
						}
	    			} catch (Exception e){log.warning("Couldn't parse DDR:"+msg);}
	    		}
	    	}
	    }
		return Response.ok(result.toString()).build();
	}
	

	
	@GET
	@Path("/write")
	public Response writeDDR() throws IOException {
		long last = -1;
		String lastDDR = StringStore.getString("lastDDR");
		if(lastDDR!=null) {
			log.info("Loaded last ddr: "+lastDDR);
			last = Long.parseLong(lastDDR);
		}
		
		long start = (System.currentTimeMillis()-86400000)*1000; 
		log.info("Start is: "+start);
		if(last != -1) {
			start = last;
		}
		log.info("Starting query from: "+start);
	    query.startTimeUsec(start);
	    Iterable<RequestLogs> records = ls.fetch(query);
	    if(records.iterator().hasNext()) {
	    	int count=0;
	    	Format formatter = new SimpleDateFormat("yyyyMMdd");
		    String file = FILENAME+"_"+formatter.format(new Date())+".json";
		    
		    String content = readFile(BUCKET, file);
		    FancyFileWriter writer= new FancyFileWriter(BUCKET, file);
		    writer.append(content);
	    
		    long newlast=0;
		    for (RequestLogs record : records) { 
		    	for (AppLogLine appLog : record.getAppLogLines()) {
		    		if(appLog.getTimeUsec()>newlast)
		    			newlast=appLog.getTimeUsec();
		    		String msg =appLog.getLogMessage(); 
		    		if (msg.startsWith("com.almende.dialog.DDRWrapper log:")){
						log.warning("checking record:"+appLog.getTimeUsec());
						try {
							msg = msg.substring(35);
							writer.append(msg);
							count++;
		    			} catch (Exception e){log.warning("Couldn't parse DDR:"+msg);}
		    		}
		    	}
		    }
		    writer.closeFinally();
		    if(newlast!=0) {
		    	log.info("Storing new last: "+newlast);
		    	StringStore.storeString("lastDDR", newlast+"");
		    }
		    return Response.ok("ok: wrote "+count+" ddrs").build();
	    }
	    
	    return Response.ok("ok: no ddrs").build();
	}
	
	private String readFile(String bucket, String file) throws IOException {
		
		boolean lockForRead = false;
		String filename = "/gs/"+bucket+"/"+file;
		AppEngineFile readableFile = new AppEngineFile(filename);
		FileService fileService = FileServiceFactory.getFileService();
		String result="";
		try {
			FileReadChannel readChannel = fileService.openReadChannel(readableFile, lockForRead);
			
			// Read the file in whichever way you'd like
			BufferedReader reader = new BufferedReader(Channels.newReader(readChannel, "UTF8"));
			String line = reader.readLine();
			while(line!=null) {
				result+=line+"\n";
				line = reader.readLine();
			}
	
			readChannel.close();
		} catch(Exception e){
		}
		
		return result;
	}
}
