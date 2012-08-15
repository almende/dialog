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
	public Response getDDRs(@QueryParam("account") String account){
		if (account == null || account.equals("")) return Response.status(Response.Status.BAD_REQUEST).build();
		
		ArrayNode result = om.createArrayNode();
	    query.startTimeUsec((System.currentTimeMillis()-86400000)*1000);
	    Iterable<RequestLogs> records = ls.fetch(query);
	    for (RequestLogs record : records) { 
	    	for (AppLogLine appLog : record.getAppLogLines()) {
	    		String msg =appLog.getLogMessage(); 
	    		if (msg.startsWith("com.almende.dialog.DDRWrapper log:")){
					log.warning("checking record:"+msg);
	    			JsonNode rec;
					try {
						rec = om.readTree(msg.substring(35));
						if (rec.has("account") && rec.get("account").asText().equals(account)){
							log.warning("Adding record:"+msg);
							result.add(rec);
						}
	    			} catch (Exception e){log.warning("Couldn't parse DDR:"+msg);}
	    		}
	    	}
	    }
		return Response.ok(result.toString()).build();
	}
}
