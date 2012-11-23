package com.almende.dialog;

import java.io.Serializable;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogService;
import com.google.appengine.api.log.RequestLogs;

@Path("/ddr")
public class DDR implements Serializable  {
	private static final long serialVersionUID = 7594102523777048633L;
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
		System.out.println("From: "+start);
	    query.startTimeUsec(start);
	    if(to != null) {
			query.endTimeUsec(Long.parseLong(to)*1000000);
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
}
