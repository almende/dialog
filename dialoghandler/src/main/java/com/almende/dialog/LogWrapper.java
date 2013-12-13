package com.almende.dialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.util.KeyServerLib;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.ClientResponse.Status;

@Path("log")
public class LogWrapper {
	
	public LogWrapper(){}

	@GET
	@Produces("application/json")
	public Response getLogs(@QueryParam("accountID") String accountId, @QueryParam("bearerToken") String bearerToken,
							@QueryParam("id") String adapterID,
							@QueryParam("type") String adapterType,
							@QueryParam("level") LogLevel level, 
							@QueryParam("end") Long endTime, 
							@QueryParam("offset") Integer offset, 
							@QueryParam("limit") Integer limit) throws Exception {
		
		// Check accountID/bearer Token against OAuth KeyServer
		if (Settings.KEYSERVER != null) {
			if (!KeyServerLib.checkAccount(accountId, bearerToken)) {
				throw new Exception("Invalid token given");
			}
		}
		ArrayList<AdapterConfig> list = AdapterConfig.findAdapters(null, null, null);
		Collection<String> adapterIDs = new HashSet<String>();
        for (AdapterConfig config: list){
        	if (config.getPublicKey().equals(accountId)){
        		adapterIDs.add( config.getConfigId() );
        	}
		}
        if(ServerUtils.isInDeployedAppspotEnvironment())
        {
		    if(adapterIDs.size()==0)
			    return Response.status(Status.BAD_REQUEST).entity("This account has no adapters").build();
        }
        else
        {
            adapterIDs = null;
        }
		
		Logger logger = new Logger();
        //TODO: remote this when logs  have adapterType in them. As of now everything is null
        adapterType = null;
		List<Log> logs = logger.find( adapterIDs, getMinSeverityLogLevelFor( level ), adapterType, endTime, offset, limit);
//        //TODO: for testing only. remove when live
//        if(logs == null || logs.isEmpty())
//        {
//            logs = logger.find( null, getMinSeverityLogLevelFor( level ), adapterType, endTime, offset, limit);
//        }
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
        Collection<LogLevel> result = null;
        if (logLevel != null)
        {
            result = new ArrayList<LogLevel>();
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
        }
	    return result;
	}
}
