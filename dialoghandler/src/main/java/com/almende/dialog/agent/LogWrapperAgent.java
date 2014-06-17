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
import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.ParallelInit;
import com.askfast.commons.agent.intf.LogAgentInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.jersey.api.client.ClientResponse.Status;

@Path("log")
@Access(AccessType.PUBLIC)
public class LogWrapperAgent extends Agent implements LogAgentInterface
{
	
	public LogWrapperAgent() {
	    super();
        ParallelInit.startThreads();
	}
	
	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger( LogWrapperAgent.class.getSimpleName() );
	private static final String NO_ADAPTER_MESSAGE = "This account has no adapters";
	
    @Override
    public ArrayNode getLogs( String accountId, String adapterID, String adapterType, String level, Long endTime,
        Integer offset, Integer limit ) throws Exception
    {
        LogLevel logLevel = LogLevel.fromJson( level );
        List<Log> logs = getLogsAsList( accountId, adapterID, adapterType, logLevel, endTime, offset, limit );
        return JOM.getInstance().convertValue(logs, ArrayNode.class);
    }
	
    @GET
    @Produces( "application/json" )
    public Response getLogsResponse( @QueryParam( "accountID" ) String accountId, @QueryParam( "id" ) String adapterID,
        @QueryParam( "type" ) String adapterType, @QueryParam( "level" ) LogLevel level,
        @QueryParam( "end" ) Long endTime, @QueryParam( "offset" ) Integer offset, @QueryParam( "limit" ) Integer limit )
    throws Exception
    {
        try
        {
            List<Log> logs = getLogsAsList( accountId, adapterID, adapterType, level, endTime, offset, limit );
            ObjectMapper om = ParallelInit.getObjectMapper();
            String result = "";
            try
            {
                result = om.writeValueAsString( logs );
            }
            catch ( Exception e )
            {
                return Response.status( Status.INTERNAL_SERVER_ERROR )
                    .entity( "Failed parsing logs: " + e.getMessage() ).build();
            }
            return Response.ok( result ).build();
        }
        catch ( Exception e )
        {
            if ( e.getLocalizedMessage() != null && e.getLocalizedMessage().equals( NO_ADAPTER_MESSAGE ) )
            {
                return Response.status( Status.BAD_REQUEST ).entity( "This account has no adapters" ).build();
            }
            return Response.status( Status.INTERNAL_SERVER_ERROR )
                .entity( "Something failed while trying to fetch logs. Message: " + e.getLocalizedMessage() ).build();
        }
    }
	
    private List<Log> getLogsAsList( String accountId, String adapterID, String adapterType, LogLevel level,
        Long endTime, Integer offset, Integer limit ) throws Exception
    {
        ArrayList<AdapterConfig> ownerAdapters = new ArrayList<AdapterConfig>();
        if(adapterID != null && !adapterID.isEmpty())
        {
            ownerAdapters.add( AdapterConfig.getAdapterConfig( adapterID ));
        }
        else
        {
            ownerAdapters = AdapterConfig.findAdapterByOwner( accountId, adapterType, null );
        }
        Collection<String> adapterIDs = new HashSet<String>();
        for ( AdapterConfig config : ownerAdapters )
        {
            if ( config.getOwner() != null && config.getOwner().equals( accountId ) )
            {
                adapterIDs.add( config.getConfigId() );
            }
        }
        if ( adapterIDs != null && !adapterIDs.isEmpty() )
        {
            Logger logger = new Logger();
            return logger.find( adapterIDs, getMinSeverityLogLevelFor( level ), adapterType, endTime, offset, limit );
        }
        else
        {
            log.severe( NO_ADAPTER_MESSAGE );
            return null;
        }
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
