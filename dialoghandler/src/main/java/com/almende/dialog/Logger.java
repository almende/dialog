package com.almende.dialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jongo.Aggregate;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.JacksonMapper;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
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
    
    public void severe(AdapterConfig adapter, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.SEVERE, adapter, message, ddrRecordId, sessionKey);
    }

    public void warning(String adapterID, String message, String ddrRecordId, String sessionKey) {

        warning(adapterID, null, message, ddrRecordId, sessionKey);
    }

    public void warning(String adapterID, String adapterType, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.WARNING, adapterID, adapterType, message, ddrRecordId, sessionKey);
    }

    public void warning(AdapterConfig adapter, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.WARNING, adapter, message, ddrRecordId, sessionKey);
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

    public void info(AdapterConfig adapter, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.INFO, adapter, message, ddrRecordId, sessionKey);
    }
    
    public void info(AdapterConfig adapter, String message, Session session) {

        this.log(LogLevel.INFO, adapter, message, session);
    }

    public void debug(String adapterID, String message, String ddrRecordId, String sessionKey) {

        debug(adapterID, null, message, ddrRecordId, sessionKey);
    }

    public void debug(String adapterID, String adapterType, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.DEBUG, adapterID, adapterType, message, ddrRecordId, sessionKey);
    }

    public void debug(AdapterConfig adapter, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.DEBUG, adapter, message, ddrRecordId, sessionKey);
    }
    
    public void debug(AdapterConfig adapter, String message, Session session) {

        this.log(LogLevel.DEBUG, adapter, message, session);
    }

    public void ddr(AdapterConfig adapter, String message, String ddrRecordId, String sessionKey) {

        this.log(LogLevel.DDR, adapter, message, ddrRecordId, sessionKey);
    }

    public void log(LogLevel level, String adapterId, String adapterType, String message, String ddrRecordId,
                    String sessionKey) {

        MongoCollection collection = getCollection();
        collection.insert(new Log(level, adapterId, adapterType, message, ddrRecordId, sessionKey));
    }

    public void log(LogLevel level, AdapterConfig adapter, String message, String ddrRecordId, String sessionKey) {

        MongoCollection collection = getCollection();
        collection.insert(new Log(level, adapter, message, ddrRecordId, sessionKey));
    }
    
    public void log(LogLevel level, AdapterConfig adapter, String message, Session session) {

        MongoCollection collection = getCollection();
        collection.insert(new Log(level, adapter, message, session));
    }

    public List<Log> find(Collection<String> adapters, Collection<LogLevel> levels, String adapterType, Long endTime,
                          Integer offset, Integer limit) {

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
                    List<Log> logsWithLimit = getLogsWithLimit(chunkedAdapters, levels, adapterType, endTime, offset,
                                                               limit);
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
                resultLogs = getLogsWithLimit(adapters, levels, adapterType, endTime, offset, limit);
            }
        }
        catch (Exception e) {
            log.severe("Exception seen while trying to fetch logs. Message: " + e.getMessage());
        }
        return resultLogs;
    }

    /**
     * fetches the logs based on the initial {@link Logger#adapter_chunk_size} adapters. <br>
     *  creates a jongo aggregate query similar to: <br>
     *  (
          [
            {
              $match: {
              adapterID: {$in: ["f9a398e0-ba5d-11e3-a019-08edb99eaa2d"]}, 
              timestamp: {$lte: 1396449705737}
              }
            },
            {
              $group: {
                _id: {level: "$level", id:"$logId", adapterId: "$adapterID", adapterType: "$adapterType", message:"$message", timestamp: "$timestamp"}
              }
            },
            {
              $sort : {timestamp: -1}
            }, 
            {
              $skip: 0
            }, 
            {
              $limit: 4
            }
          ]
        )
     * 
     * 
     * @return
     * @throws Exception 
     */
    private List<Log> getLogsWithLimit(Collection<String> adapters, Collection<LogLevel> levels, String adapterType,
                                       Long endTime, Integer offset, Integer limit) throws Exception {

        MongoCollection collection = getCollection();
        //initially collect all the match queries
        String matchQuery = null;
        if (adapters != null) {
            int endIndex = adapters.size() <= adapter_chunk_size - 1 ? adapters.size() : adapter_chunk_size;
            List<String> subAdapterList = new ArrayList<String>(adapters).subList(0, endIndex);
            if (!subAdapterList.isEmpty()) {
                matchQuery = "adapterID: {$in:" + ServerUtils.serialize(subAdapterList) + "}";
            }
        }
        
        if (adapterType != null){
            matchQuery = matchQuery != null ? ( matchQuery + "," ) : "";
            matchQuery += "adapterType:\"" + adapterType + "\"";
        }

        if (endTime != null) {
            matchQuery = matchQuery != null ? ( matchQuery + "," ) : "";
            matchQuery += "timestamp:{$lte:" + endTime + "}";
        }
        
        if(levels != null) {
            matchQuery = matchQuery != null ? ( matchQuery + "," ) : "";
            matchQuery += "level:{$in:" + ServerUtils.serialize(levels) + "}";
        }
        Aggregate aggregate = null;
        //create an aggregate query with the matchQuery first
        if(matchQuery != null) {
            aggregate = collection.aggregate(String.format("{$match: {%s}}", matchQuery));
        }
        
        //update the aggregate query with groupQuery next. This includes all the fields to fetch
        String groupQuery = "{$group:{_id: {logId:\"$logId\", level: \"$level\", "
                            + "adapterID: \"$adapterID\", adapterType: \"$adapterType\", message:\"$message\", "
                            + "timestamp: \"$timestamp\", ddrRecordId: \"$ddrRecordId\", sessionKey: \"$sessionKey\"}}}";
        if(aggregate != null) {
            aggregate = aggregate.and(groupQuery);
        }
        else {
            aggregate = collection.aggregate(groupQuery);
        }
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 50;
        }
        //update the aggregate query with sort (on timestamp), offset and limit
        aggregate = aggregate.and("{$sort : {timestamp: -1}}").and(String.format("{$skip :%s}", offset))
                                        .and(String.format("{$limit :%s}", limit));
        List<ObjectNode> resultLogMap = aggregate.as(ObjectNode.class);
        ArrayList<Log> resultLogs = new ArrayList<Log>();
        for (ObjectNode resultLog : resultLogMap) {
            JsonNode jsonNode = resultLog.get("_id");
            Log convertValue = JOM.getInstance().convertValue(jsonNode, Log.class);
            resultLogs.add(convertValue);
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
