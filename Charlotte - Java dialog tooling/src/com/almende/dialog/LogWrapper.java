package com.almende.dialog;

import com.almende.dialog.util.KeyServerLib;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.jersey.api.client.ClientResponse.Status;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Path("log")
public class LogWrapper {
	
	public LogWrapper(){}

	@GET
	@Produces("application/json")
	public Response getLogs(@QueryParam("privateKey") String privateKey, @QueryParam("publicKey") String publicKey,
							@QueryParam("id") String adapterID,
							@QueryParam("type") String adapterType,
							@QueryParam("level") LogLevel level, 
							@QueryParam("end") Long endTime, 
							@QueryParam("offset") Integer offset, 
							@QueryParam("limit") Integer limit) {
		
		if(privateKey==null || privateKey.isEmpty())
			return Response.status(Status.BAD_REQUEST).entity("Missing private key").build();
		
		if(publicKey==null || publicKey.isEmpty())
			return Response.status(Status.BAD_REQUEST).entity("Missing public key").build();
		
		ArrayNode list = KeyServerLib.getAllowedAdapterList(publicKey, privateKey);
		ArrayList<String> adapterIDs = new ArrayList<String>();
		for(JsonNode adapter : list) {
			adapterIDs.add(adapter.get("id").asText());
		}
		
		if(adapterIDs.size()==0)
			return Response.status(Status.BAD_REQUEST).entity("This account has no adapters").build();
		
		Logger logger = new Logger();
		List<Log> logs = logger.find(adapterIDs, getMinSeverityLogLevelFor( level ), adapterType, endTime, offset, limit);
		ObjectMapper om = ParallelInit.getObjectMapper();
		String result = "";
		try {
			result =  om.writeValueAsString(logs);
		} catch(Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Failed parsing logs: "+e.getMessage()).build();
		}
		
		return Response.ok(result).build();
	}
	
	private Collection<LogLevel> getMinSeverityLogLevelFor(LogLevel logLevel)
	{
	    Collection<LogLevel> result = new ArrayList<LogLevel>();
	    switch ( logLevel )
        {
            case DEBUG:
                result = Arrays.asList( LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARNING, LogLevel.SEVERE );
                break;
            case INFO:
                result = Arrays.asList( LogLevel.INFO, LogLevel.WARNING, LogLevel.SEVERE );
                break;
            case WARNING:
                result = Arrays.asList( LogLevel.WARNING, LogLevel.SEVERE );
                break;
            case SEVERE:
                result = Arrays.asList( LogLevel.SEVERE );
                break;
        }
	    return result;
	}
}
