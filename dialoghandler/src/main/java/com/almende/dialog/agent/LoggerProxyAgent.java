package com.almende.dialog.agent;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.protocol.jsonrpc.annotation.Access;
import com.almende.eve.protocol.jsonrpc.annotation.AccessType;
import com.almende.eve.protocol.jsonrpc.annotation.Name;
import com.almende.eve.protocol.jsonrpc.annotation.Optional;
import com.askfast.commons.entity.HttpLog;
import com.askfast.commons.entity.RequestLog;
import com.askfast.commons.entity.ResponseLog;
import com.askfast.commons.intf.LoggerAgentInterface;
import com.askfast.commons.utils.EnvironmentUtil;

/**
 * Simple proxy agent which forwards the request from dialog agent to the
 * loggerAgent
 * 
 * @author shravan
 */
@Access(AccessType.PUBLIC)
public class LoggerProxyAgent extends Agent {

    public static final String HTTP_LOGGER_EVE_PATH = "eve_logger.yaml";

    public HttpLog createLog(@Name("request") RequestLog request, @Name("response") ResponseLog response,
        @Name("sessionKey") String sessionKey, @Name("accountId") String accountId,
        @Name("ddrRecordId") @Optional String ddrRecordId, @Name("isSuccessful") @Optional Boolean isSuccessful) {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            return getLoggerAgentInterface().createLog(request, response, sessionKey, accountId, ddrRecordId,
                                                       isSuccessful);
        }
        return null;
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

        String url = getConfig().get("environment", EnvironmentUtil.getEnvironment(), "http_logger_agent_url");
        if (url != null) {
            return createAgentProxy(URI.create(url), LoggerAgentInterface.class);
        }
        return null;
    }
}
