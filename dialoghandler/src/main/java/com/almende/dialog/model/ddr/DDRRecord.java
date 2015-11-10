package com.almende.dialog.model.ddr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.persistence.Id;
import org.bson.types.ObjectId;
import org.jongo.Find;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.ResultHandler;
import org.jongo.marshall.jackson.JacksonMapper;
import org.jongo.marshall.jackson.configuration.MapperModifier;
import org.mongojack.JacksonDBCollection;
import com.almende.dialog.LogLevel;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.jackson.JOM;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

/**
 * The actual price charged as part of the service and/or communication cost
 * 
 * @author Shravan
 */
@JsonPropertyOrder({"totalCost"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class DDRRecord {

    protected static final Logger log = Logger.getLogger(DDRRecord.class.getName());
    public static final String DDR_TOTALCOST_KEY = "totalCost";
    public static final String DDR_RECORD_KEY = "DDR_RECORD_ID";
    public static final String ANSWER_INPUT_KEY = "ANSWER_INPUT";
    private static final String DOT_REPLACER_KEY = "[%dot%]";

    @Id
    @JsonProperty("_id")
    private String _id;
    //used for backward compatibility as TwigMongoWrapper was used before. so it maintained two ids: id and _id in the datastore.
    String id;
    String adapterId;
    AdapterType adapterType;
    String accountId;
    String fromAddress;
    @JsonIgnore
    Map<String, String> toAddress;
    //creating a dummy serialized version of toAddress as dot(.) in keys is not allowed by mongo
    String toAddressString;
    String ddrTypeId;
    Integer quantity;
    Long start;
    Long duration;
    Collection<String> sessionKeys;
    Map<String, CommunicationStatus> statusPerAddress;

    @JsonIgnore
    Boolean shouldGenerateCosts = false;
    @JsonIgnore
    Boolean shouldIncludeServiceCosts = false;
    Map<String, Object> additionalInfo;
    /**
     * PRE_PAID accounts must have a fixed ddrCost. POST_PAID can have a
     * variable one. This can be got from the adapterId too, but just makes it
     * more explicit
     */
    AccountType accountType;

    //links to other ddrRecords, parent and children
    String parentId;
    Collection<String> childIds;
    String direction = null;

    /**
     * total cost is not sent for any ddr if its a post paid account
     */
    Double totalCost = 0.0;
    /**
     * cache the adapter linked with this ddrRecord
     */
    @JsonIgnore
    private AdapterConfig config;
    /**
     * Flag to make sure that the data with (.) is correcting on a get from any
     * level other than database/mongo e.g. resource/agent
     */
    @JsonIgnore
    private boolean correctDotData;

    public DDRRecord() {
    }

    public DDRRecord(String ddrTypeId, AdapterConfig adapterConfig, String accountId, Integer quantity) {

        this.ddrTypeId = ddrTypeId;
        if(adapterConfig != null) {
            this.adapterId = adapterConfig.getConfigId();
            this.adapterType = AdapterType.fromJson(adapterConfig.getAdapterType());
        }
        this.accountId = accountId;
        this.quantity = quantity;
    }

    @JsonIgnore
    public DDRRecord createOrUpdate() {

        _id = _id != null && !_id.isEmpty() ? _id : ObjectId.get().toStringMongod();
        correctDotData = true;
        JacksonDBCollection<DDRRecord, String> collection = getMongoJackCollection();
        DDRRecord existingDDRRecord = collection.findOneById(_id);
        //update if existing
        if (existingDDRRecord != null) {
            collection.updateById(_id, this);
        }
        else { //create one if missing
            this.start = this.start != null ? this.start : TimeUtils.getServerCurrentTimeInMillis();
            collection.insert(this);
        }
        log.info("ddr record saved: " + ServerUtils.serializeWithoutException(this));
        return this;
    }

    /**
     * creates/updates a ddr record and creates a log of type
     * {@link LogLevel#DDR}
     */
    @JsonIgnore
    public DDRRecord createOrUpdateWithLog(Session session) {

        if (session != null && session.isTestSession()) {
            return null;
        }
        return createOrUpdate();
    }

    /**
     * creates/updates a ddr record and creates a log of type
     * {@link LogLevel#DDR}
     */
    public DDRRecord createOrUpdateWithLog(Map<String, Session> sessionKeyMap) {

        DDRRecord ddrRecord = null;
        for (Session session : sessionKeyMap.values()) {
            ddrRecord = createOrUpdateWithLog(session);
        }
        return ddrRecord;
    }

    /**
     * Gets the DDRRecord for the given id only if accountId matches that of the
     * owner of the DDRRecord itself
     * 
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public static DDRRecord getDDRRecord(String id, String accountId) throws Exception {

        MongoCollection coll = getCollection();
        DDRRecord ddrRecord = id != null ? coll.findOne(getQueryById(id)).as(DDRRecord.class) : null;
        if (ddrRecord != null && ddrRecord.getAccountId() != null && !ddrRecord.getAccountId().equals(accountId)) {
            throw new Exception(String.format("DDR record: %s is not owned by account: %s", id, accountId));
        }
        return ddrRecord;
    }

    /**
     * Fetch the ddr records based the input parameters. fetches the records
     * that matches to all the parameters given. The accountId is mandatory and
     * rest are optional
     * 
     * @param accountId
     * @param adapterTypes
     * @param adapterIds
     * @param fromAddress
     * @param ddrTypeIds
     * @param status
     * @param startTime
     * @param endTime
     * @param sessionKeys
     * @param offset
     * @param limit
     * @param shouldGenerateCosts
     *            Can be used to recalculate all the prices for individual
     *            ddrRecords.
     * @param shouldIncludeServiceCosts
     *            Attaches the service costs too. shouldGenerateCosts param must
     *            be set to true as well
     * @return
     * @throws Exception
     */
    public static List<DDRRecord> getDDRRecords(String accountId, Collection<AdapterType> adapterTypes,
        Collection<String> adapterIds, String fromAddress, Collection<String> ddrTypeIds,
        final CommunicationStatus status, Long startTime, Long endTime, Collection<String> sessionKeys, Integer offset,
        Integer limit, final Boolean shouldGenerateCosts, final Boolean shouldIncludeServiceCosts) throws Exception {

        String ddrQuery = getDDRQuery(accountId, adapterTypes, adapterIds, fromAddress, ddrTypeIds, startTime, endTime,
            sessionKeys);
        limit = limit != null && limit <= 1000 ? limit : 1000;
        offset = offset != null ? offset : 0;

        Find ddrFind = getCollection().find(String.format("{%s}", ddrQuery)).skip(offset).limit(limit)
                                      .sort("{start: -1}");
        MongoCursor<DDRRecord> ddrCursor = null;
        if (status != null) {
            ddrCursor = ddrFind.map(new ResultHandler<DDRRecord>() {

                @Override
                public DDRRecord map(DBObject result) {

                    try {
                        if (result.get("statusPerAddress") != null) {
                            Map<String, String> statusForAddresses = ServerUtils.deserialize(
                                result.get("statusPerAddress").toString(), false,
                                new TypeReference<Map<String, String>>() {
                            });
                            if (statusForAddresses != null) {
                                for (String address : statusForAddresses.keySet()) {
                                    Object statusForAddress = statusForAddresses.get(address);
                                    if (statusForAddress != null &&
                                        CommunicationStatus.fromJson(statusForAddress.toString()).equals(status)) {
                                        if (result.toString() != null) {
                                            
                                            DDRRecord ddrRecord = ServerUtils.deserialize(result.toString(), false,
                                                DDRRecord.class);
                                            if (shouldGenerateCosts != null && shouldGenerateCosts) {
                                                ddrRecord.setShouldGenerateCosts(shouldGenerateCosts);
                                                ddrRecord.setShouldIncludeServiceCosts(shouldIncludeServiceCosts);
                                                ddrRecord.setTotalCost(ddrRecord.getTotalCost());
                                            }
                                            return ddrRecord;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
        }
        else {
            ddrCursor = ddrFind.as(DDRRecord.class);
        }
        List<DDRRecord> result = new ArrayList<DDRRecord>(1);
        while (ddrCursor.hasNext()) {
            DDRRecord ddrRecord = ddrCursor.next();
            if (ddrRecord != null) {

                if (status == null && shouldGenerateCosts != null && shouldGenerateCosts) {
                    ddrRecord.setShouldGenerateCosts(shouldGenerateCosts);
                    ddrRecord.setShouldIncludeServiceCosts(shouldIncludeServiceCosts);
                    ddrRecord.setTotalCost(ddrRecord.getTotalCost());
                }
                result.add(ddrRecord);
            }
        }
        return result;
    }
    
    /**
     * Fetch the quantity of ddr records based the input parameters. The
     * accountId is mandatory and rest are optional
     * 
     * @param adapterId
     * @param accountId
     *            Mandatory field
     * @param fromAddress
     * @param ddrTypeIds
     * @param status
     * @param startTime
     * @param endTime
     * @param offset
     * @return
     * @throws Exception
     */
    public static Integer getDDRRecordsQuantity(String accountId, Collection<AdapterType> adapterTypes,
        Collection<String> adapterIds, String fromAddress, Collection<String> ddrTypeIds,
        final CommunicationStatus status, Long startTime, Long endTime, Collection<String> sessionKeys, Integer offset)
            throws Exception {

        String ddrQuery = getDDRQuery(accountId, adapterTypes, adapterIds, fromAddress, ddrTypeIds, startTime, endTime,
            sessionKeys);
        offset = offset != null ? offset : 0;
        MongoCursor<Integer> ddrCursor = getCollection().find(String.format("{%s}", ddrQuery)).skip(offset)
             .map(new ResultHandler<Integer>() {
            
                 @Override
                 public Integer map(DBObject result) {
            
                     Integer quantity = 0;
                     try {
                         if(status != null) {
                             if (result.get("statusPerAddress") != null) {
                                 Map<String, String> statusForAddresses = ServerUtils.deserialize(
                                     result.get("statusPerAddress").toString(), false,
                                     new TypeReference<Map<String, String>>() {
                                 });
                                 if(statusForAddresses != null) {
                                     for (String address : statusForAddresses.keySet()) {
                                         Object statusForAddress = statusForAddresses.get(address);
                                         if (statusForAddress != null &&
                                             CommunicationStatus.fromJson(statusForAddress.toString()).equals(status)) {
                                             quantity++;
                                         }
                                     }
                                 }
                             }
                         }
                         else if(result.get("quantity") != null) {
                             quantity = (Integer) result.get("quantity");
                         }
                     }  
                     catch (Exception e) {
                         e.printStackTrace();
                     }
                     return quantity;
                 }
             });
        Integer quantity = 0;
        while (ddrCursor.hasNext()) {
            quantity += ddrCursor.next();
        }
        return quantity;
    }

    /**
     * fetch the ddr record for a particular Session. This inverts the lookup.
     * Normally used when a ddr is not found corresponding to a session
     * {@link Session#getDdrRecordId()}
     * 
     * @param session
     * @return
     * @throws Exception
     */
    public static DDRRecord getDDRRecord(String sessionKey) {

        Session session = Session.getSession(sessionKey);
        if (session != null) {
            String queryString = "accountId:\"" + session.getAccountId() + "\"";
            //fetch accounts that match
            if (session.getAdapterID() != null) {
                queryString += ", adapterId:\"" + session.getAdapterID() + "\"";
            }
            if (session.getDirection() != null) {
                HashMap<String, String> addressMap = new HashMap<String, String>(1);
                if (session.getDirection().equalsIgnoreCase("incoming")) {
                    queryString += ", fromAddress:\"" + session.getRemoteAddress() + "\"";
                    addressMap.put(session.getLocalAddress(), "");
                    queryString += ", status:\"" + CommunicationStatus.RECEIVED + "\"";
                }
                else {
                    queryString += ", fromAddress:\"" + session.getLocalAddress() + "\"";
                    addressMap.put(session.getRemoteAddress(), "");
                    queryString += ", status:\"" + CommunicationStatus.SENT + "\"";
                }
                try {
                    queryString += ", toAddressString:\"" + ServerUtils.serialize(addressMap) + "\"";
                }
                catch (Exception e) {
                    e.printStackTrace();
                    log.severe("Error while serializing. Message: " + e.toString());
                }
            }
            MongoCollection collection = getCollection();
            MongoCursor<DDRRecord> ddrRecords = collection.find(String.format("{%s}", queryString)).as(DDRRecord.class);
            if (ddrRecords != null) {
                for (DDRRecord ddrRecord : ddrRecords) {
                    //return the ddrRecord whose startTime matches the creationTime or answerTime of the session
                    if (ddrRecord.getStart() != null &&
                        (ddrRecord.getStart().toString().equals(session.getCreationTimestamp()) ||
                            ddrRecord.getStart().toString().equals(session.getAnswerTimestamp()))) {
                        return ddrRecord;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Script to update all ddrRecords with adapterType if there is an
     * associated adapterId found too. This is to fix the issue with removing an
     * adapter, before the billing date, and then fetching all teh ddr for that
     * adapterType
     * 
     * @return
     * 
     * @throws Exception
     */
    @JsonIgnore
    public static double updateDDRRecordsWithAdapterType() throws Exception {

        long startTime = TimeUtils.getServerCurrentTimeInMillis();
        String query = "{$or: [ {direction: null}, {adapterType: null}]}";
        double totalDddrs = getCollection().count(query);
        MongoCollection collection = getCollection();
        MongoCursor<ObjectNode> ddrCursor = collection.find(query).as(ObjectNode.class);
        double count = 0;
        log.info(String.format("Going to update %s ddr records", totalDddrs));
        double percentageDone = 0.0;
        boolean forUpdate = false;
        while (ddrCursor.hasNext()) {
            ObjectNode ddrRecord = ddrCursor.next();
            AdapterConfig adapter = AdapterConfig.getAdapterConfig(ddrRecord.get("adapterId").textValue());
            forUpdate = updateDirection(forUpdate, ddrRecord, adapter);
            forUpdate = updateAdapterType(forUpdate, ddrRecord, adapter);
            if (forUpdate) {
                WriteResult writeResult = collection.update(new ObjectId(ddrRecord.get("_id").asText()))
                                                    .with(ddrRecord);
                if (writeResult.getN() == 0) {
                    writeResult = collection.update(getQueryById(ddrRecord.get("_id").asText())).with(ddrRecord);
                }
                if (writeResult.getN() > 0) {
                    count++;
                }
            }
            if ((count / totalDddrs) > (0.3 + percentageDone)) {
                percentageDone += 0.3;
                log.info(String.format("%s of the ddr records are parsed in %s secs", (percentageDone * 100) + "%",
                    (TimeUtils.getServerCurrentTimeInMillis() - startTime) / 1000));
            }
        }
        log.info(String.format("%s DDRRecords are updated with AdapterTypes in %s secs", count,
            (TimeUtils.getServerCurrentTimeInMillis() - startTime) / 1000));
        return count;
    }

    /**
     * Add a adapterType if its missing. Fetches it from the given adapter
     * @param forUpdate
     * @param ddrRecord
     * @param adapter
     * @return
     */
    private static boolean updateAdapterType(boolean forUpdate, ObjectNode ddrRecord, AdapterConfig adapter) {

        if (ddrRecord.get("adapterType") == null) {
            if (adapter != null) {

                AdapterType adapterType = AdapterType.fromJson(adapter.getAdapterType());
                if (adapterType != null) {
                    ddrRecord.put("adapterType", adapterType.toString());
                    if (ddrRecord.get("id") != null) {
                        ddrRecord.remove("id");
                    }
                    forUpdate = true;
                }
            }
        }
        return forUpdate;
    }

    /**
     * Add a direction if its missing. If the from address is not equal
     * to the adapter address, its an incoming communication
     * @param forUpdate
     * @param ddrRecord
     * @param adapter
     * @return
     * @throws Exception
     */
    private static boolean updateDirection(boolean forUpdate, ObjectNode ddrRecord, AdapterConfig adapter)
        throws Exception {

        if (ddrRecord.get("direction") == null && ddrRecord.get("toAddressString") != null && adapter != null) {

            String toAddressString = getToAddressString(null, ddrRecord.get("toAddressString").asText(), false);
            Map<String, String> toAddress = ServerUtils.deserialize(toAddressString,
                new TypeReference<Map<String, String>>() {
                });
            if (toAddress != null) {
                String direction = toAddress.containsKey(adapter.getFormattedMyAddress()) && toAddress.size() == 1
                    ? "inbound" : "outbound";
                ddrRecord.put("direction", direction);
                forUpdate = true;
            }
        }
        return forUpdate;
    }

    // -- getters and setters --
    @JsonProperty("_id")
    public String get_Id() {

        return _id;
    }

    @JsonProperty("_id")
    public void set_Id(String id) {

        this._id = id;
    }

    /**
     * used for backward compatibility as TwigMongoWrapper was used before. so
     * it maintained two ids: id and _id in the datastore. uses the
     * {@link DDRRecord#get_Id()} itself
     */
    @JsonIgnore
    public String getId() {

        return get_Id();
    }

    /**
     * used for backward compatibility as TwigMongoWrapper was used before. so
     * it maintained two ids: id and _id in the datastore. uses the
     * {@link DDRRecord#set_Id(String)} itself
     * 
     * @param id
     */
    @JsonIgnore
    public void setId(String id) {

        set_Id(id);
    }

    public String getAdapterId() {

        return adapterId;
    }

    public void setAdapterId(String adapterId) {

        this.adapterId = adapterId;
    }

    public String getAccountId() {

        return accountId;
    }

    public void setAccountId(String accountId) {

        this.accountId = accountId;
    }

    public String getFromAddress() {

        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {

        this.fromAddress = fromAddress;
    }

    @JsonIgnore
    public Map<String, String> getToAddress() {

        if (toAddress == null && getToAddressString() == null) {
            toAddress = new HashMap<String, String>();
        }
        else if ((toAddress == null || getToAddressString().isEmpty()) && getToAddressString() != null) {
            try {
                toAddress = ServerUtils.deserialize(getToAddressString(), new TypeReference<HashMap<String, String>>() {
                });
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Exception while deserializing toAddress: %s", toAddress));
            }
        }
        return toAddress;
    }

    @JsonIgnore
    public void setToAddress(Map<String, String> toAddress) {

        this.toAddress = toAddress;
        this.toAddressString = getToAddressString();
    }

    @JsonIgnore
    public void addToAddress(String toAddress) {

        this.toAddress = this.toAddress != null ? this.toAddress : new HashMap<String, String>();
        this.toAddress.put(toAddress, "");
        this.toAddressString = getToAddressString();
    }

    public String getDdrTypeId() {

        return ddrTypeId;
    }

    public void setDdrTypeId(String ddrTypeId) {

        this.ddrTypeId = ddrTypeId;
    }

    public Integer getQuantity() {

        return quantity != null ? quantity : 0;
    }

    public Long getStart() {

        return start;
    }

    public Long getDuration() {

        return duration;
    }

    public void setQuantity(Integer quantity) {

        this.quantity = quantity;
    }

    public void setStart(Long start) {

        this.start = start;
    }

    public void setDuration(Long duration) {

        this.duration = duration;
    }

    /**
     * only used by the mongo serializing/deserializing
     * 
     * @return
     * @throws Exception
     */
    public String getToAddressString() {

        toAddressString = getToAddressString(toAddress, toAddressString, correctDotData);
        return toAddressString;
    }

    /**
     * only used by the mongo serializing/deserializing
     * 
     * @param toAddressString
     * @throws Exception
     */
    public void setToAddressString(String toAddressString) throws Exception {

        this.toAddressString = correctDotReplacedString(toAddressString);
    }

    @JsonIgnore
    public DDRType getDdrType() {

        if (ddrTypeId != null && !ddrTypeId.isEmpty()) {
            return DDRType.getDDRType(ddrTypeId);
        }
        return null;
    }

    @JsonIgnore
    public AdapterConfig getAdapter() {

        if (adapterId != null && !adapterId.isEmpty() && config == null) {
            config = AdapterConfig.getAdapterConfig(adapterId);
        }
        return config;
    }

    @JsonIgnore
    public void setShouldGenerateCosts(Boolean shouldGenerateCosts) {

        this.shouldGenerateCosts = shouldGenerateCosts;
    }

    @JsonIgnore
    public void setShouldIncludeServiceCosts(Boolean shouldIncludeServiceCosts) {

        this.shouldIncludeServiceCosts = shouldIncludeServiceCosts;
    }

    /**
     * Generally only called by the serializer method to lazy load all the
     * charges involved. This method will generate costs if it is a
     * {@link AccountType#POST_PAID} account
     * 
     * @return
     * @throws Exception
     */
    public Double getTotalCost() throws Exception {

        /**
         * regenerate the costs at runtime, only when requested and when the
         * accountTYpe is not prepaid or trial. These accounts have fixed costs
         */
        if (Boolean.TRUE.equals(shouldGenerateCosts) &&
            (accountType == null || accountType.equals(AccountType.POST_PAID))) {

            DDRType ddrType = getDdrType();
            switch (ddrType.getCategory()) {
                case INCOMING_COMMUNICATION_COST:
                case OUTGOING_COMMUNICATION_COST:
                    totalCost = DDRUtils.calculateDDRCost(this, shouldIncludeServiceCosts);
                    break;
                case ADAPTER_PURCHASE:
                case SERVICE_COST:
                case SUBSCRIPTION_COST:
                default:
                    totalCost = DDRUtils.calculateDDRCost(this);
                    break;
            }
        }
        return DDRUtils.getCeilingAtPrecision(totalCost, 3);
    }

    /**
     * Generally just used by JACKSON to (de)serialize the variable for all
     * accounts except PRE_PAID. Setting the value does not matter as the actual
     * cost is calculated lazily when the {@link DDRRecord#shouldGenerateCosts}
     * is set for all cases apart from for PRE_PAID customers to true
     * 
     * @param totalCost
     */
    public void setTotalCost(Double totalCost) {

        this.totalCost = totalCost != null ? totalCost : 0.0;
    }

    public Map<String, Object> getAdditionalInfo() {

        additionalInfo = additionalInfo != null ? additionalInfo : new HashMap<String, Object>();
        if (!additionalInfo.isEmpty()) {
            Map<String, Object> additionalInfoCopy = getDotReplaceKeys(additionalInfo);
            for (String key : additionalInfo.keySet()) {
                Object value = additionalInfo.get(key);
                if (value instanceof Map) {
                    try {
                        Map<String, Object> valueAsMap = JOM.getInstance().convertValue(value,
                            new TypeReference<Map<String, Object>>() {
                            });
                        valueAsMap = getDotReplaceKeys(valueAsMap);
                        additionalInfoCopy.put(key, valueAsMap);
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                        log.severe("Typecasting failed. Saving serialized data instead");
                        additionalInfoCopy.put(key, ServerUtils.serializeWithoutException(value));
                    }
                }
            }
            additionalInfo = additionalInfoCopy;
        }
        return additionalInfo;
    }

    public void setAdditionalInfo(Map<String, Object> additionalInfo) {

        if (additionalInfo != null) {
            this.additionalInfo = this.additionalInfo != null ? this.additionalInfo : new HashMap<String, Object>();
            for (String key : additionalInfo.keySet()) {
                //go one level deeper
                Object value = additionalInfo.get(key);
                if (value != null && value instanceof Map) {
                    Map<String, Object> valueMap = null;
                    try {
                        valueMap = ServerUtils.convert(value, false, new TypeReference<Map<String, Object>>() {
                        });
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    value = getDotReplaceKeys(valueMap);
                }
                this.additionalInfo.put(correctDotReplacedString(key), additionalInfo.get(key));
            }
        }
        else {
            this.additionalInfo = additionalInfo;
        }
    }

    @JsonIgnore
    public void addAdditionalInfo(String key, Object value) {

        additionalInfo = additionalInfo != null ? additionalInfo : new HashMap<String, Object>();
        if (!DDR_RECORD_KEY.equals(key)) {
            //dot(.) is not allowed to be saved in mongo. just replace with [%dot%]
            additionalInfo.put(getDotReplacedString(key, correctDotData), value);
        }
    }

    public AccountType getAccountType() {

        return accountType;
    }

    public void setAccountType(AccountType accountType) {

        //update null accountType with POST_PAID
        accountType = accountType != null ? accountType : AccountType.POST_PAID;
        this.accountType = accountType;
    }

    public Collection<String> getSessionKeys() {

        return sessionKeys;
    }

    public void setSessionKeys(Collection<String> sessionKeys) {

        this.sessionKeys = sessionKeys;
    }

    public void addSessionKey(String sessionKey) {

        sessionKeys = sessionKeys != null ? sessionKeys : new HashSet<String>();
        sessionKeys.add(sessionKey);
    }

    public Map<String, CommunicationStatus> getStatusPerAddress() {

        statusPerAddress = statusPerAddress != null ? statusPerAddress : new HashMap<String, CommunicationStatus>();
        //make sure each of the key is dot replaced
        if (!statusPerAddress.isEmpty()) {
            HashMap<String, CommunicationStatus> statusPerAddressCopy = new HashMap<String, CommunicationStatus>();
            for (String key : statusPerAddress.keySet()) {
                statusPerAddressCopy.put(getDotReplacedString(key, correctDotData), statusPerAddress.get(key));
            }
            statusPerAddress = statusPerAddressCopy;
        }
        return statusPerAddress;
    }

    public void setStatusPerAddress(Map<String, CommunicationStatus> statusPerAddress) {

        //make sure each of the key is dot corrected
        if (statusPerAddress != null) {
            this.statusPerAddress = this.statusPerAddress != null ? this.statusPerAddress
                : new HashMap<String, CommunicationStatus>();
            for (String key : statusPerAddress.keySet()) {
                this.statusPerAddress.put(correctDotReplacedString(key), statusPerAddress.get(key));
            }
        }
        else {
            this.statusPerAddress = statusPerAddress;
        }
    }

    /**
     * add a status per address
     * 
     * @param address
     * @param status
     */
    public void addStatusForAddress(String address, CommunicationStatus status) {

        AdapterConfig adapter = getAdapter();
        if (address != null && adapter != null && (adapter.isCallAdapter() || adapter.isSMSAdapter())) {

            address = PhoneNumberUtils.formatNumber(address.trim().split("@")[0], null);
        }
        getStatusPerAddress().put(getDotReplacedString(address, correctDotData), status);
    }

    /**
     * add a status all all the address
     * 
     * @param addresses
     * @param status
     */
    public void setStatusForAddresses(Collection<String> addresses, CommunicationStatus status) {

        for (String address : addresses) {
            addStatusForAddress(getDotReplacedString(address, correctDotData), status);
        }
    }

    /**
     * return status based on address
     * 
     * @param address
     * @param status
     */
    public CommunicationStatus getStatusForAddress(String address) {

        return getStatusPerAddress().get(getDotReplacedString(address, correctDotData));
    }

    /**
     * gets the direction of this ddrRecord based on the toAddress and the
     * adapter address
     * 
     * @return either "inbound" or "outbound"
     */
    public String getDirection() {

        //if the from address is not equal to the adapter address, its an incoming communication
        if (direction == null && getToAddress() != null && getAdapter() != null) {
            return getToAddress().containsKey(getAdapter().getFormattedMyAddress()) && getToAddress().size() == 1
                ? "inbound" : "outbound";
        }
        return direction;
    }

    public void setDirection(String direction) {

        this.direction = direction;
    }

    /**
     * Reloads the instance from the db
     * 
     * @return
     * @throws Exception
     */
    @JsonIgnore
    public DDRRecord reload() throws Exception {

        return getDDRRecord(getId(), accountId);
    }

    /**
     * Adds the given sessionKeys to additionalInfo, and to collection of
     * sessionKeys. If the session is found. adds the external caller id too
     * 
     * @param sessionMap
     * @throws Exception 
     */
    @JsonIgnore
    public void setSessionKeysFromMap(Map<String, Session> sessionMap) {

        if (sessionMap != null) {
            HashMap<String, String> sessionKeyMap = new HashMap<String, String>();
            setSessionKeys(new HashSet<String>());
            for (String address : sessionMap.keySet()) {
                Session session = sessionMap.get(address);
                if (session != null) {
                    sessionKeys.add(session.getKey());
                    sessionKeyMap.put(address, session.getKey());
                    if (session.getExternalSession() != null) {
                        addAdditionalInfo("externalSessionKey", session.getExternalSession());
                    }
                    //attach the child and the parent ddrRecord Ids
                    updateParentAndChildDDRRecordIds(session);
                    setAccountType(session.getAccountType());
                }
            }
            addAdditionalInfo(Session.SESSION_KEY, sessionKeyMap);
        }
    }

    @JsonIgnore
    public DDRTypeCategory getTypeCategory() {

        DDRType ddrType = DDRType.getDDRType(ddrTypeId);
        return ddrType != null ? ddrType.getCategory() : null;
    }

    public String getParentId() {

        return parentId;
    }

    public void setParentId(String parentId) {

        this.parentId = parentId;
    }

    public Collection<String> getChildIds() {

        return childIds;
    }

    public void setChildIds(Collection<String> childIds) {

        this.childIds = childIds;
    }
    
    public AdapterType getAdapterType() {
        
        return adapterType;
    }
    public void setAdapterType(AdapterType adapterType) {
    
        this.adapterType = adapterType;
    }

    @JsonIgnore
    public void addChildId(String childId) {

        if (childId != null) {
            childIds = childIds != null ? childIds : new HashSet<String>();
            childIds.add(childId);
        }
    }

    /**
     * Gets the linked parentDDRRecord, if any
     * 
     * @return
     * @throws Exception
     */
    @JsonIgnore
    public DDRRecord getParentDdrRecord() throws Exception {

        if (getParentId() != null) {
            return getDDRRecord(getParentId(), accountId);
        }
        return null;
    }

    /**
     * Gets all the linked childDDRRecords, if any.
     * 
     * @return
     * @throws Exception
     */
    @JsonIgnore
    public ArrayList<DDRRecord> getChildDDRRecords() throws Exception {

        ArrayList<DDRRecord> childDDRRecords = new ArrayList<DDRRecord>();
        if (getChildIds() != null) {
            for (String childId : childIds) {
                DDRRecord childDdrRecord = getDDRRecord(childId, accountId);
                if (childDdrRecord != null) {
                    childDDRRecords.add(childDdrRecord);
                }
            }
        }
        return childDDRRecords;
    }

    /**
     * Based on the address (assumed to be formatted if its a number), fetches
     * the sessionKey as stored in the {@link DDRRecord#additionalInfo}
     * 
     * @param address
     * @return
     * @throws Exception
     */
    public String getSessionKeyByAddress(String address) throws Exception {

        Map<String, Object> additionalInfo = getAdditionalInfo();
        if (additionalInfo != null && additionalInfo.get(Session.SESSION_KEY) != null) {
            Object sessionKeyObject = additionalInfo.get(Session.SESSION_KEY);
            Map<String, String> sessionKeys = ServerUtils.convert(sessionKeyObject, false,
                new TypeReference<Map<String, String>>() {
                });
            return sessionKeys != null ? sessionKeys.get(address) : null;
        }
        return null;
    }

    /**
     * Returns the message parameter stored in the ddrRecord
     * 
     * @return
     */
    @JsonIgnore
    public String getMessage() {

        if (additionalInfo != null && additionalInfo.get(DDRUtils.DDR_MESSAGE_KEY) != null) {
            return additionalInfo.get(DDRUtils.DDR_MESSAGE_KEY).toString();
        }
        return null;
    }

    /**
     * Replace all dots (.) in keys of a map object
     * 
     * @param data
     */
    private Map<String, Object> getDotReplaceKeys(Map<String, Object> data) {

        if (data != null) {
            Map<String, Object> copyOfData = new HashMap<String, Object>();
            for (String key : data.keySet()) {
                copyOfData.put(getDotReplacedString(key, correctDotData ), data.get(key));
            }
            return copyOfData;
        }
        return null;
    }

    /**
     * Updates/adds (commits to mongo too) the linked parent ddrRecord with this
     * child ddrRecordId (via Session) and also sets the parentDDRRecorid to
     * this instance (doesnt commit to mongo).
     * 
     * @param session
     */
    private void updateParentAndChildDDRRecordIds(Session session) {

        Session parentSession = session.getParentSession();
        if (parentSession != null) {
            DDRRecord parentDDRRecord = parentSession.getDDRRecord();
            if (parentDDRRecord != null) {
                if (getId() == null) {
                    setId(ObjectId.get().toStringMongod());
                }
                parentDDRRecord.addChildId(getId());
                parentDDRRecord.createOrUpdate();
                setParentId(parentDDRRecord.getId());
            }
        }
    }

    /**
     * Eclipse auto generated method
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((_id == null) ? 0 : _id.hashCode());
        return result;
    }

    /**
     * Eclipse auto generated method
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DDRRecord other = (DDRRecord) obj;
        if (_id == null) {
            if (other._id != null)
                return false;
        }
        else if (!_id.equals(other._id))
            return false;
        return true;
    }

    /**
     * This method returns a aggregator based on the filter conditions
     * 
     * @param accountId
     * @param adapterTypes
     * @param adapterIds
     * @param fromAddress
     * @param ddrTypeIds
     * @param startTime
     * @param endTime
     * @param sessionKeys
     * @param offset
     * @param limit
     * @return
     * @return
     * @throws Exception
     */
    private static String getDDRQuery(String accountId, Collection<AdapterType> adapterTypes,
        Collection<String> adapterIds, String fromAddress, Collection<String> ddrTypeIds, Long startTime, Long endTime,
        Collection<String> sessionKeys) throws Exception {

        //fetch accounts that match
        String matchQuery = "";
        if (accountId != null) {
            matchQuery += "accountId:\"" + accountId + "\"";
        }
        else {
            throw new Exception("AccountId is not expected to be null.");
        }
        //pick all adapterIds belong to the adapterType if its given. If AdapterIds are given choose that instead
        if ((adapterTypes != null && !adapterTypes.isEmpty()) && (adapterIds == null || adapterIds.isEmpty())) {

            matchQuery += ", adapterType: {$in:" + ServerUtils.serialize(adapterTypes) + "}";
        }
        if (adapterIds != null) {
            if (adapterIds.size() == 1) {
                matchQuery += ", adapterId:\"" + adapterIds.iterator().next() + "\"";
            }
            else {
                matchQuery += ", adapterId: {$in:" + ServerUtils.serialize(adapterIds) + "}";
            }
        }
        if (fromAddress != null) {
            matchQuery += ", fromAddress:\"" + fromAddress + "\"";
        }
        if (ddrTypeIds != null) {
            matchQuery += ", ddrTypeId: {$in:" + ServerUtils.serialize(ddrTypeIds) + "}";
        }
        if (startTime != null && endTime != null) {
            matchQuery += ", start: {$gte:" + startTime + ", $lte: " + endTime + "}";
        }
        else {
            if (startTime != null) {
                matchQuery += ", start: {$gte:" + startTime + "}";
            }
            if (endTime != null) {
                matchQuery += ", start: {$lte:" + endTime + "}";
            }
        }
        if (sessionKeys != null) {
            matchQuery += ", sessionKeys: {$in:" + ServerUtils.serialize(sessionKeys) + "}";
        }
        log.info(String.format("Query processed: %s", matchQuery));
        return matchQuery;
    }

    private static MongoCollection getCollection() {

        DB db = ParallelInit.getDatastore();
        Jongo jongo = new Jongo(db, new JacksonMapper.Builder().addModifier(new MapperModifier() {

            @Override
            public void modify(ObjectMapper mapper) {

                mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            }
        }).registerModule(new JodaModule()).withView(DDRRecord.class).build());
        MongoCollection collection = jongo.getCollection(DDRRecord.class.getCanonicalName().toLowerCase() + "s");
        collection.ensureIndex("{ _id: 1}");
        return collection;
    }
    
    /**
     * This is used to save the document. Lot of serializing issues with jongo,
     * additionalInfo serializer is not called. (.) remains in address
     * 
     * @return
     */
    private static JacksonDBCollection<DDRRecord, String> getMongoJackCollection() {

        DB db = ParallelInit.getDatastore();
        return JacksonDBCollection.wrap(db.getCollection(DDRRecord.class.getCanonicalName().toLowerCase() + "s"),
                                        DDRRecord.class, String.class);
    }
    
    /**
     * Returns the simple Jongo query for fetching by _id. 
     * 
     * @param _id
     * @return {_id: \"<_id>\"}
     */
    private static String getQueryById(String _id) {

        return String.format("{_id: '%s'}", _id);
    }
    
    private static String getToAddressString(Map<String, String> toAddress, String toAddressString,
        Boolean correctDotData) {

        if ((toAddress == null || toAddress.isEmpty()) && toAddressString != null) {
            toAddressString = getDotReplacedString(toAddressString, correctDotData);
        }
        else {
            try {
                toAddressString = ServerUtils.serialize(toAddress);
                //replace dot(.) by - as mongo doesnt allow map variables with (.)
                toAddressString = getDotReplacedString(toAddressString, correctDotData);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Exception while serializing toAddress: %s", toAddress));
            }
        }
        return toAddressString;
    }
    
    /**
     * Ideally should be called by the GETTER methods of fields whose dot (.)
     * values are to be replaced by {@link DDRRecord#DOT_REPLACER_KEY}
     * 
     * @param data
     * @return
     */
    private static String getDotReplacedString(String data, Boolean correctDotData) {

        if (Boolean.TRUE.equals(correctDotData)) {
            return data != null ? data.replace(".", DOT_REPLACER_KEY) : null;
        }
        else {
            return correctDotReplacedString(data);
        }
    }

    /**
     * Ideally should be called by the SETTER methods of fields whose dot (.)
     * values are replaced by {@link DDRRecord#DOT_REPLACER_KEY}
     * 
     * @param data
     * @return
     */
    private static String correctDotReplacedString(String data) {

        return data != null ? data.replace(DOT_REPLACER_KEY, ".") : null;
    }
}
