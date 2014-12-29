package com.almende.dialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jongo.Aggregate;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.JacksonMapper;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.mongodb.DB;

public class Logger {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Logger.class.getName());
    private static final int adapter_chunk_size = 20;

    public Logger() {
    }

    public void severe(String adapterID, String message, String ddrRecordId, String sessionKey) {

        severe(adapterID, null, message, ddrRecordId, sessionKey);
    }

    public void severe(String adapterID, String adapterType, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.SEVERE, adapterID, adapterType, message, ddrRecordId, sessionKey);
    }

    public void severe(AdapterConfig adapter, String message, Session session) {

        this.log(LogLevel.SEVERE, adapter, message, session);
    }

    public void warning(String adapterID, String message, String ddrRecordId, String sessionKey) {

        warning(adapterID, null, message, ddrRecordId, sessionKey);
    }

    public void warning(String adapterID, String adapterType, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.WARNING, adapterID, adapterType, message, ddrRecordId, sessionKey);
    }
    
    public void warning(AdapterConfig adapter, String message, Session session) {

        this.log(LogLevel.WARNING, adapter, message, session);
    }

    public void info(String adapterID, String message, String ddrRecordId, String sessionKey) {

        info(adapterID, null, message, ddrRecordId, sessionKey);
    }

    public void info(String adapterID, String adapterType, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.INFO, adapterID, adapterType, message, ddrRecordId, sessionKey);
    }

    public void info(AdapterConfig adapter, String message, Session session) {

        this.log(LogLevel.INFO, adapter, message, session);
    }
    
    public void ddr(AdapterConfig adapter, DDRRecord ddrRecord, Session session) {

        this.log(LogLevel.DDR, adapter, ServerUtils.serializeWithoutException(ddrRecord), session);
    }

    public void debug(String adapterID, String message, String ddrRecordId, String sessionKey) {

        debug(adapterID, null, message, ddrRecordId, sessionKey);
    }

    public void debug(String adapterID, String adapterType, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.DEBUG, adapterID, adapterType, message, ddrRecordId, sessionKey);
    }

    public void debug(AdapterConfig adapter, String message, Session session) {

        this.log(LogLevel.DEBUG, adapter, message, session);
    }

    /**
     * Logs something only if a session and a trackingToken is found
     * corresponding to the sessionKey
     * 
     * @param level
     * @param adapterId
     * @param adapterType
     * @param message
     * @param ddrRecordId
     * @param sessionKey
     */
    public void log(LogLevel level, String adapterId, String adapterType, String message, String ddrRecordId,
        String sessionKey) {

        if (sessionKey != null) {
            Log devLog = new Log(level, adapterId, adapterType, message, ddrRecordId, sessionKey);
            if (devLog.getSessionKey() != null) {
                MongoCollection collection = getCollection();
                collection.insert(devLog);
//                //TODO: remove in live 
                log.info("New log added: "+ ServerUtils.serializeWithoutException(devLog));
            }
        }
    }

    /**
     * Logs something only if a session and a trackingToken is found in the session
     * @param level
     * @param adapter
     * @param message
     * @param session
     */
    public void log(LogLevel level, AdapterConfig adapter, String message, Session session) {

        if (session != null && session.getTrackingToken() != null) {
            MongoCollection collection = getCollection();
            Log devLog = new Log(level, adapter, message, session);
            collection.insert(devLog);
            //TODO: remove in live 
            log.info("New log added: "+ ServerUtils.serializeWithoutException(devLog));
        }
    }
    
    /**
     * Updates this log in the mongo database
     * @param devLog
     */
    public static void save(Log devLog) {

        MongoCollection collection = getCollection();
        collection.save(devLog);
        //TODO: remove in live 
        log.info("Log updated " + ServerUtils.serializeWithoutException(devLog));
    }

    /**
     * Returns all the logs that match to the given parameters. AccountId is a
     * mandatory field. This is an expensive step as it fetches the logs in 2
     * steps: first loads all the trackingTokens by descending timestamp. Then
     * fetches all the logs corresponding to this tracking token in batches, so
     * that related tracking token are grouped together
     * 
     * @param accountId AccountId is a mandatory field
     * @param adapters
     * @param levels
     * @param adapterType
     * @param endTime
     * @param offset
     * @param limit
     * @return
     */
    public static List<Log> find(String accountId, Collection<String> adapters, Collection<LogLevel> levels,
        String adapterType, Long endTime, Integer offset, Integer limit) {

        List<Log> resultLogs = new ArrayList<Log>();
        try {
            if (adapters != null) {
                log.info(String.format("Initial adapters size %s", adapters.size()));
                int startingIndex = 0;
                int endIndex = adapters.size() <= adapter_chunk_size ? adapters.size() : adapter_chunk_size;
                for (int chunkCount = 0; chunkCount <= (adapters.size() / adapter_chunk_size); chunkCount++) {
                    log.info(String.format("chunkCount %s startIndex: %s endIndex %s", chunkCount, startingIndex,
                                           endIndex));
                    List<String> chunkedAdapters = new ArrayList<String>(adapters).subList(startingIndex, endIndex);
                    List<Log> logsWithLimit = getLogsWithLimit(accountId, chunkedAdapters, levels, adapterType,
                                                               endTime, offset, limit);
                    if (logsWithLimit != null && !logsWithLimit.isEmpty()) {
                        resultLogs.addAll(logsWithLimit);
                    }
                    //update indexes
                    startingIndex = startingIndex + adapter_chunk_size;
                    endIndex = adapters.size() >= endIndex + adapter_chunk_size ? endIndex + adapter_chunk_size
                                                                               : adapters.size();
                }
            }
            else {
                resultLogs = getLogsWithLimit(accountId, adapters, levels, adapterType, endTime, offset, limit);
            }
        }
        catch (Exception e) {
            log.severe("Exception seen while trying to fetch logs. Message: " + e.getMessage());
        }
        return resultLogs;
    }
    
    /**
     * Fetches all the logs in the mongoDb.
     * @return
     */
    public static List<Log> findAllLogs() {

        ArrayList<Log> result = new ArrayList<Log>();
        MongoCollection collection = getCollection();
        MongoCursor<Log> logCursor = collection.find().as(Log.class);
        while (logCursor.hasNext()) {
            result.add(logCursor.next());
        }
        return result;
    }

    /**
     * fetches the logs based on the initial {@link Logger#adapter_chunk_size}
     * adapters. AccountId is a mandatory field <br>
     * creates a jongo aggregate query similar to: <br>
     * ( [ { $match: { adapterID: {$in:
     * ["f9a398e0-ba5d-11e3-a019-08edb99eaa2d"]}, timestamp: {$lte:
     * 1396449705737} } }, { $group: { _id: {level: "$level", id:"$logId",
     * adapterId: "$adapterID", adapterType: "$adapterType", message:"$message",
     * timestamp: "$timestamp"} } }, { $sort : {timestamp: -1} }, { $skip: 0 },
     * { $limit: 4 } ] )
     * 
     * @return
     * @throws Exception
     */
    private static List<Log> getLogsWithLimit(String accountId, Collection<String> adapters, Collection<LogLevel> levels,
        String adapterType, Long endTime, Integer offset, Integer limit) throws Exception {

        //initially collect all the match queries
        String matchQuery = "";
        if (accountId != null) {
            matchQuery += "accountId:\"" + accountId + "\"";
        }
        else {
            throw new Exception("AccountId is not expected to be null.");
        }
        
        if (adapters != null) {
            int endIndex = adapters.size() <= adapter_chunk_size - 1 ? adapters.size() : adapter_chunk_size;
            List<String> subAdapterList = new ArrayList<String>(adapters).subList(0, endIndex);
            if (!subAdapterList.isEmpty()) {
                matchQuery += ", adapterID: {$in:" + ServerUtils.serialize(subAdapterList) + "}";
            }
        }

        if (adapterType != null) {
            matchQuery += ", adapterType:\"" + adapterType + "\"";
        }
        
        if (endTime != null) {
            matchQuery += ", timestamp:{$lte:" + endTime + "}";
        }

        if (levels != null) {
            matchQuery += ", level:{$in:" + ServerUtils.serialize(levels) + "}";
        }
        offset = offset == null ? 0 : offset;
        limit = limit == null ? 50 : limit;

        ArrayList<Log> resultLogs = new ArrayList<Log>();
        //fetch in batches of 5
        for (; resultLogs.size() <= limit;) {
            ArrayList<Log> logs = fetchLogs(accountId, matchQuery, offset, 5, limit - resultLogs.size());
            if (logs != null && !logs.isEmpty()) {
                resultLogs.addAll(logs);
            }
            else {
                break;
            }
            offset += 5;
        }
        return resultLogs;
    }

    /** Fetches the logs for a particular matchQuery and offset.
     * @param offset
     * @param trackingTokenFetchlimit
     * @param collection
     * @param aggregate
     * @return
     */
    private static ArrayList<Log> fetchLogs(String accountId, String matchQuery, Integer offset,
        Integer trackingTokenFetchlimit, Integer logLimit) {

        matchQuery = String.format("{$match: {%s}}", matchQuery);
        log.info("Match query: " + matchQuery);
        MongoCollection collection = getCollection();
        Aggregate aggregate = collection.aggregate(matchQuery);

        //update the aggregate query with groupQuery next. This includes all the fields to fetch
        String groupQuery = "{$group:{_id: \"$trackingToken\", timestamp:{$max: \"$timestamp\"}}}";
        aggregate = aggregate.and(groupQuery);
        //update the aggregate query with sort (on timestamp), offset and limit
        aggregate = aggregate.and("{$sort :{timestamp: -1}}").and(String.format("{$skip :%s}", offset))
                                        .and(String.format("{$limit :%s}", trackingTokenFetchlimit));

        List<ObjectNode> logsByTrackingToken = aggregate.as(ObjectNode.class);
        ArrayList<Log> resultLogs = new ArrayList<Log>();
        for (ObjectNode logByTrackingToken : logsByTrackingToken) {
            JsonNode trackingToken = logByTrackingToken.get("_id");
            MongoCursor<Log> logsCursor = collection
                                            .find(String.format("{trackingToken: \"%s\", accountId: \"%s\"}",
                                                                trackingToken.asText(), accountId))
                                            .sort("{timestamp: -1}").as(Log.class);
            if (logsCursor != null) {
                while (logsCursor.hasNext() && resultLogs.size() < logLimit) {
                        resultLogs.add(logsCursor.next());
                }
            }
        }
        return resultLogs;
    }
    
    private static MongoCollection getCollection() {

        DB db = ParallelInit.getDatastore();
        Jongo jongo = new Jongo(db, new JacksonMapper.Builder().registerModule(new JodaModule())
                                        .enable(MapperFeature.AUTO_DETECT_GETTERS).withView(Log.class).build());
        return jongo.getCollection(Log.class.getCanonicalName().toLowerCase() + "s");
    }
}
