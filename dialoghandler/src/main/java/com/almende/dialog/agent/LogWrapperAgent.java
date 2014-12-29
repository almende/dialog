package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.annotation.Optional;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.ParallelInit;
import com.askfast.commons.agent.intf.LogAgentInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.jersey.api.client.ClientResponse.Status;

@Path("log")
@Access(AccessType.PUBLIC)
public class LogWrapperAgent extends Agent implements LogAgentInterface {

    public LogWrapperAgent() {

        super();
        ParallelInit.startThreads();
    }

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LogWrapperAgent.class
                                    .getSimpleName());
    private static final String NO_ADAPTER_MESSAGE = "This account has no adapters";

    @Override
    public ArrayNode getLogs(@Name("accountId") String accountId, @Name("id") @Optional String adapterID,
        @Name("type") @Optional String adapterType, @Name("level") @Optional String level,
        @Name("end") @Optional Long endTime, @Name("offset") @Optional Integer offset,
        @Name("limit") @Optional Integer limit) throws Exception {

        LogLevel logLevel = LogLevel.fromJson(level);
        List<Log> logs = getLogsAsList(accountId, adapterID, adapterType, logLevel, endTime, offset, limit);
        return JOM.getInstance().convertValue(logs, ArrayNode.class);
    }

    /**
     * Updates all the accountId in the {@link Log} documents. <br>
     * Checks if the adapter is owned by one account only. else updates null. <br>
     * This agent method makes sure there is backward compatibility for the logs
     * that do not have an accountId as a field in their document to the new
     * version which does have them.
     * 
     * @return Map<String, String> of all the logs which has been updated with
     *         null or an error. i.e <logId, null| reason if an error occured>
     * @throws Exception
     */
    public HashMap<String, String> updateLogsWithAccountIdScript() throws Exception {

        List<Log> allLogs = Logger.findAllLogs();
        HashMap<String, String> errorOrIssues = new HashMap<String, String>();
        int updateSuccess = 0;
        int updateUnSuccess = 0;
        for (Log log : allLogs) {
            //check if the adapter linked to this log has only one owner
            AdapterConfig adapterConfig = log.getAdapterConfig();
            if (adapterConfig != null && adapterConfig.getAccounts() != null &&
                adapterConfig.getAccounts().size() == 1 &&
                adapterConfig.getAccounts().contains(adapterConfig.getOwner())) {
                log.setAccountId(adapterConfig.getOwner());
                updateSuccess++;
            }
            else {
                log.setAccountId(null);
                String issueMessage = "NULL accountId is updated";
                if (adapterConfig == null) {
                    issueMessage = "updated NULL as adapterConfig is not found";
                }
                else if (adapterConfig.getAccounts() == null) {
                    issueMessage = "updated NULL as no linked accounts found";
                }
                else if (adapterConfig.getAccounts().size() != 1) {
                    issueMessage = "updated NULL as shared adapter found";
                }
                else if (adapterConfig.getAccounts().contains(adapterConfig.getOwner())) {
                    issueMessage = "updated NULL as shared account list does not contain owner";
                }
                errorOrIssues.put(log.getLogId(), issueMessage);
                updateUnSuccess++;
            }
            Logger.save(log);
        }
        errorOrIssues.put("totalSize", String.valueOf(allLogs.size()));
        errorOrIssues.put("Success", String.valueOf(updateSuccess));
        errorOrIssues.put("UnSuccess", String.valueOf(updateUnSuccess));
        log.info(String.format("updateLogsWithAccountIdScript result: totalSize: %s Success: %s UnSuccess: %s",
                               allLogs.size(), updateSuccess, updateUnSuccess));
        return errorOrIssues;
    }

    @GET
    @Produces("application/json")
    public Response getLogsResponse(@QueryParam("accountID") String accountId, @QueryParam("id") String adapterID,
        @QueryParam("type") String adapterType, @QueryParam("level") LogLevel level, @QueryParam("end") Long endTime,
        @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) throws Exception {

        try {
            List<Log> logs = getLogsAsList(accountId, adapterID, adapterType, level, endTime, offset, limit);
            ObjectMapper om = ParallelInit.getObjectMapper();
            String result = "";
            try {
                result = om.writeValueAsString(logs);
            }
            catch (Exception e) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Failed parsing logs: " + e.getMessage())
                                                .build();
            }
            return Response.ok(result).build();
        }
        catch (Exception e) {
            if (e.getLocalizedMessage() != null && e.getLocalizedMessage().equals(NO_ADAPTER_MESSAGE)) {
                return Response.status(Status.BAD_REQUEST).entity("This account has no adapters").build();
            }
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                                            .entity("Something failed while trying to fetch logs. Message: " +
                                                                                    e.getLocalizedMessage()).build();
        }
    }

    
    /**
     * Fetches all the logs corresponding to the accountId and other corresponding given parameters.
     * @param accountId if null, throws exception
     * @param adapterID if null, tries to fetch all the adapters for the given accountId. 
     * @param adapterType if null, tries to fetch all the adpaters for the given accountId.
     * @param level 
     * @param endTime
     * @param offset 
     * @param limit
     * @return a List of fetched logs
     * @throws Exception
     */
    private List<Log> getLogsAsList(String accountId, String adapterID, String adapterType, LogLevel level,
        Long endTime, Integer offset, Integer limit) throws Exception {

        Collection<String> adapterIDs = new HashSet<String>();
        if (accountId != null) {
            if (adapterID != null && !adapterID.isEmpty()) {
                AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterID);
                if (adapterConfig != null &&
                    AdapterConfig.checkIfAdapterMatchesForAccountId(Arrays.asList(accountId), adapterConfig, false) != null) {

                    adapterIDs.add(adapterConfig.getConfigId());
                }
            }
            else {
                ArrayList<AdapterConfig> accountAdapters = AdapterConfig.findAdapterByAccount(accountId, adapterType,
                                                                                              null);
                for (AdapterConfig adapterConfig : accountAdapters) {
                    adapterIDs.add(adapterConfig.getConfigId());
                }
            }
            //fetch logs for each of the adapters
            if (adapterIDs != null && !adapterIDs.isEmpty()) {
                return Logger.find(accountId, adapterIDs, getMinSeverityLogLevelFor(level), adapterType, endTime,
                                   offset, limit);
            }
            else {
                log.severe(NO_ADAPTER_MESSAGE);
                return null;
            }
        }
        else {
            log.severe("AccountId is null");
            throw new Exception("AccountId cannot be null to fetch the logs");
        }
    }

    private Collection<LogLevel> getMinSeverityLogLevelFor(LogLevel logLevel) {

        Collection<LogLevel> result = null;
        if (logLevel != null) {
            result = new ArrayList<LogLevel>();
            switch (logLevel) {
                case DEBUG:
                    result = Arrays.asList(LogLevel.DEBUG, LogLevel.DDR, LogLevel.INFO, LogLevel.WARNING,
                                           LogLevel.SEVERE);
                    break;
                case INFO:
                    result = Arrays.asList(LogLevel.INFO, LogLevel.DDR, LogLevel.WARNING, LogLevel.SEVERE);
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
