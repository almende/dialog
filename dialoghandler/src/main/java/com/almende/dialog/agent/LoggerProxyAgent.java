package com.almende.dialog.agent;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.protocol.jsonrpc.annotation.Access;
import com.almende.eve.protocol.jsonrpc.annotation.AccessType;
import com.almende.eve.protocol.jsonrpc.annotation.Name;
import com.almende.eve.protocol.jsonrpc.annotation.Optional;
import com.almende.util.jackson.JOM;
import com.askfast.commons.entity.HttpLog;
import com.askfast.commons.entity.RequestLog;
import com.askfast.commons.entity.ResponseLog;
import com.askfast.commons.intf.LoggerAgentInterface;
import com.askfast.commons.utils.EnvironmentUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simple proxy agent which forwards the request from dialog agent to the
 * loggerAgent
 * 
 * @author shravan
 */
@Access(AccessType.PUBLIC)
public class LoggerProxyAgent extends Agent {

    private static final Logger log = Logger.getLogger( LoggerProxyAgent.class.getName() );
    public static final String HTTP_LOGGER_EVE_PATH = "eve_logger.yaml";

    public void createLog(@Name("request") RequestLog request, @Name("response") ResponseLog response,
        @Name("sessionKey") String sessionKey, @Name("accountId") String accountId,
        @Name("ddrRecordId") @Optional String ddrRecordId, @Name("isSuccessful") @Optional Boolean isSuccessful) {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            
            ObjectMapper om = JOM.getInstance();
            
            ObjectNode params = om.createObjectNode();
            params.set( "request", om.convertValue( request, ObjectNode.class ) );
            params.set( "response", om.convertValue( response, ObjectNode.class ) );
            params.put( "sessionKey", sessionKey );
            params.put( "accountId", accountId );
            params.put( "ddrRecordId", ddrRecordId );
            params.put( "isSuccessful", isSuccessful );
            
            try {
                URI url = getLoggerAgentURL();
                call( url, "createLog", params );
            } catch(IOException e) {
                log.warning("Failed to create log e: ");
                e.printStackTrace();
            }
        }
    }

    public ArrayList<HttpLog> getLogs(@Name("id") @Optional String logId, @Name("accountId") String accountId,
            @Name("sessionKeys") @Optional Collection<String> sessionKeys,
            @Name("ddrRecordId") @Optional String ddrRecordId) throws Exception {

        return getLoggerAgentInterface().getLogs(logId, accountId, sessionKeys, ddrRecordId);
    }

    public void deleteLogs(@Name("id") String logId, @Name("accountId") String accountId) {

        getLoggerAgentInterface().deleteLogs(logId, accountId);
    }

    private LoggerAgentInterface getLoggerAgentInterface() {

        URI url = getLoggerAgentURL();
        if (url != null) {
            return createAgentProxy(url, LoggerAgentInterface.class);
        }
        log.warning( "No logger agent url found!" );
        return null;
    }
    
    private URI getLoggerAgentURL() {
        String url = getConfig().get("environment", EnvironmentUtil.getEnvironment(), "http_logger_agent_url");
        if (url != null) {
            return URI.create(url);
        }
        log.warning( "No logger agent url found!" );
        return null;
    }
}
