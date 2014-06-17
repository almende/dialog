package com.almende.dialog;

import java.io.Serializable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.JOM;
import com.almende.util.twigmongo.SortDirection;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Path("/ddr")
public class DDR implements Serializable {
	
	private static final long					serialVersionUID	= 7594102523777048633L;
	
	private static ObjectMapper					om					= JOM.getInstance();
	static final Logger							LOG					= Logger.getLogger("DDR");
	static final TwigCompatibleMongoDatastore	datastore			= new TwigCompatibleMongoDatastore();
	
	@GET
	@Produces("application/json")
	public Response getDDRs(@QueryParam("account") String account,
			@QueryParam("from") String from, @QueryParam("to") String to,
			@QueryParam("adapter") String adapter) {
		ArrayNode result = om.createArrayNode();
		
		RootFindCommand<Log> cmd = datastore.find().type(Log.class);
		cmd.addFilter("level", FilterOperator.EQUAL, "DDR");
		long start = (System.currentTimeMillis() - 86400000);
		if (from != null) {
			start = Long.parseLong(from) * 1000;
		}
		cmd.addFilter("timestamp", FilterOperator.GREATER_THAN_OR_EQUAL, start);
		if (to != null) {
			long end = Long.parseLong(to) * 1000;
			if (start == end) end++;
			cmd.addFilter("timestamp", FilterOperator.LESS_THAN_OR_EQUAL, end);
		}
		cmd.addSort("timestamp", SortDirection.DESCENDING);
		
		Iterator<Log> log = cmd.now();
		
		while (log.hasNext()) {
			Log nextLog = log.next();
			
			String msg = nextLog.getMessage();
			JsonNode rec;
			try {
				rec = om.readTree(msg);
				if (account == null || rec.has("account")
						&& rec.get("account").asText().equals(account)) {
					
					if (adapter == null
							|| (rec.has("adapterType") && rec
									.get("adapterType").asText()
									.equals(adapter))) {
						
						result.add(rec);
					}
				}
			} catch (Exception e) {
				LOG.log(Level.WARNING,"Couldn't parse DDR:" + msg, e );
			}
		}
		return Response.ok(result.toString()).build();
	}
}
