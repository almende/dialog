package com.almende.dialog.agent;

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

import com.almende.dialog.Log;
import com.almende.dialog.LogLevel;
import com.almende.dialog.Logger;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.util.ParallelInit;
import com.askfast.commons.agent.intf.LogAgentInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.ClientResponse.Status;

@Path("log")
@Access(AccessType.PUBLIC)
public class LogWrapperAgent extends Agent implements LogAgentInterface
{
	
	public LogWrapperAgent() {
	}
	
    @Override
    public String getLogs( String accountId, String adapterID, String adapterType, String level, Long endTime,
        Integer offset, Integer limit ) throws Exception
    {
        LogLevel logLevel = LogLevel.fromJson( level );
        return getLogs( accountId, adapterID, adapterType, logLevel, endTime, offset, limit ).getEntity().toString();
    }
	
	@GET
	@Produces("application/json")
	public Response getLogs(@QueryParam("accountID") String accountId,
			@QueryParam("id") String adapterID,
			@QueryParam("type") String adapterType,
			@QueryParam("level") LogLevel level,
			@QueryParam("end") Long endTime,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) throws Exception {
		
		ArrayList<AdapterConfig> list = AdapterConfig.findAdapters(null, null,
				null);
		Collection<String> adapterIDs = new HashSet<String>();
		for (AdapterConfig config : list) {
			if (config.getOwner().equals(accountId)) {
				adapterIDs.add(config.getConfigId());
			}
		}
		if (ServerUtils.isInDeployedAppspotEnvironment()) {
			if (adapterIDs.size() == 0) return Response
					.status(Status.BAD_REQUEST)
					.entity("This account has no adapters").build();
		} else {
			adapterIDs = null;
		}
		
		Logger logger = new Logger();
		// TODO: remote this when logs have adapterType in them. As of now
		// everything is null
		adapterType = null;
		List<Log> logs = logger.find(adapterIDs,
				getMinSeverityLogLevelFor(level), adapterType, endTime, offset,
				limit);
		// //TODO: for testing only. remove when live
		// if(logs == null || logs.isEmpty())
		// {
		// logs = logger.find( null, getMinSeverityLogLevelFor( level ),
		// adapterType, endTime, offset, limit);
		// }
		ObjectMapper om = ParallelInit.getObjectMapper();
		String result = "";
		try {
			result = om.writeValueAsString(logs);
		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR)
					.entity("Failed parsing logs: " + e.getMessage()).build();
		}
		
		return Response.ok(result).build();
	}
	
	private Collection<LogLevel> getMinSeverityLogLevelFor(LogLevel logLevel) {
		Collection<LogLevel> result = null;
		if (logLevel != null) {
			result = new ArrayList<LogLevel>();
			switch (logLevel) {
				case DEBUG:
					result = Arrays.asList(LogLevel.DEBUG, LogLevel.DDR, LogLevel.INFO,
							LogLevel.WARNING, LogLevel.SEVERE);
					break;
				case INFO:
					result = Arrays.asList(LogLevel.INFO, LogLevel.DDR, LogLevel.WARNING,
							LogLevel.SEVERE);
					break;
				case WARNING:
					result = Arrays.asList(LogLevel.WARNING, LogLevel.SEVERE);
					break;
				case SEVERE:
					result = Arrays.asList(LogLevel.SEVERE);
					break;
				case DDR:
					result = Arrays.asList(LogLevel.DDR);
					break;
			}
		}
		return result;
	}
}
