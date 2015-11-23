package com.almende.dialog.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;
import org.bson.types.ObjectId;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.jongo.marshall.jackson.JacksonMapper;
import org.jongo.marshall.jackson.configuration.MapperModifier;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.mongodb.DB;
import com.mongodb.WriteResult;

/**
 * Bean for storing and fetching blacklist of address details 
 * @author shravan
 *
 */
public class Blacklist {

    private static final Logger log = Logger.getLogger(Blacklist.class.toString());
    
    ObjectId _id;
    String address;
    AdapterType adapterType;
    String accountId;

    
    public Blacklist() {
    }
    
    public Blacklist(String address, AdapterType adapterType, String accountId) {
        this.address = address;
        this.adapterType = adapterType;
        this.accountId = accountId;
    }

    public ObjectId get_id() {
    
        return _id;
    }
    
    public void set_id(ObjectId _id) {
    
        this._id = _id;
    }
    
    public String getAddress() {
    
        return address;
    }
    
    public void setAddress(String address) {
    
        this.address = address;
    }
    
    public AdapterType getAdapterType() {
    
        return adapterType;
    }
    
    public void setAdapterType(AdapterType adapterType) {
    
        this.adapterType = adapterType;
    }
    
    @JsonIgnore
    public Blacklist createOrUpdate() {

        address = PhoneNumberUtils.formatNumber(address, null);
        MongoCollection collection = getCollection();
        WriteResult writeResult = null;
        if (_id != null) {
            writeResult = collection.update(_id).with(this);
        }
        if (writeResult == null || writeResult.getN() == 0) {
            writeResult = collection.insert(this);
        }
        return this;
    }

    /**
     * Checks if the given address is blacklisted or not.
     * 
     * @param addresses
     *            Is mandatory.
     * @param type
     * @param accountId
     * @return The list of all blacklisted numbers in the original given format
     *         in the address param. Returns empty set if no match found
     * @throws Exception
     */
    public static HashSet<String> getBlacklist(final Collection<String> addresses, final AdapterType adapterType,
        final String accountId) throws Exception {

        HashMap<String, String> formattedAddresses = getFormattedAddresses(addresses, adapterType);
        String serializedAddresses = ServerUtils.serialize(formattedAddresses);
        String query = getBlacklistQuery(formattedAddresses.keySet(), adapterType, accountId);
        MongoCursor<Blacklist> blackListCursor = getCollection().find(String.format("{%s}", query)).as(Blacklist.class);

        HashSet<String> result = new HashSet<String>();
        while (blackListCursor.hasNext()) {
            result.add(formattedAddresses.get(blackListCursor.next().getAddress()));
        }
        if (formattedAddresses.size() > 0) {
            log.info(String.format("%s blacklists found for address: %s adapterType: %s and accountId: %s. Query: %s",
                formattedAddresses.size(), serializedAddresses, adapterType, accountId, query));
        }
        return result;
    }
    
    /**
     * Checks if the given address is blacklisted or not.
     * 
     * @param addresses
     *            Is mandatory.
     * @param type
     * @param accountId
     * @return The list of all blacklisted numbers in the original given format
     *         in the address param. Returns empty set if no match found
     * @throws Exception
     */
    public static HashSet<Blacklist> getBlacklistEntities(final Collection<String> addresses,
        final AdapterType adapterType, final String accountId) throws Exception {

        HashMap<String, String> formattedAddresses = getFormattedAddresses(addresses, adapterType);
        String query = getBlacklistQuery(formattedAddresses.keySet(), adapterType, accountId);
        MongoCursor<Blacklist> blackListCursor = getCollection().find(String.format("{%s}", query)).as(Blacklist.class);
        HashSet<Blacklist> result = new HashSet<Blacklist>();
        while (blackListCursor.hasNext()) {
            result.add(blackListCursor.next());
        }
        return result;
    }
    
    /**
     * Gets the cursor for the blacklisted entries based on the params given
     * 
     * @param formattedAddresses
     *            Is mandatory.
     * @param type
     * @param accountId
     * @return The list of all blacklisted numbers in the original given format
     *         in the address param. Returns empty set if no match found
     * @throws Exception
     */
    private static String getBlacklistQuery(final Collection<String> formattedAddresses, final AdapterType adapterType,
        final String accountId) throws Exception {

        String query = "";
        String serializedAddresses = ServerUtils.serializeWithoutException(formattedAddresses);
        query += String.format("address: {$in: %s }", serializedAddresses);

        //check if the address is blacklisted for a particular AdapterType or globally
        if (adapterType != null) {
            query += String.format(", adapterType: {$in: [\"%s\", null] }", adapterType);
        }
        //check if the address is blacklisted for a particular Account or globally
        if (adapterType != null) {
            query += String.format(", accountId: {$in: [\"%s\", null] }", accountId);
        }
        return query;
    }
    
    private static MongoCollection getCollection() {

        DB db = ParallelInit.getDatastore();
        Jongo jongo = new Jongo(db, new JacksonMapper.Builder().addModifier(new MapperModifier() {

            @Override
            public void modify(ObjectMapper mapper) {

                mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
            }
        }).registerModule(new JodaModule()).withView(Blacklist.class).build());
        MongoCollection collection = jongo.getCollection(Blacklist.class.getSimpleName().toLowerCase());
        collection.ensureIndex("{ _id: 1, address: 1}");
        return collection;
    }
    
    /**
     * Gets a hashmap of <formattedAddress, address> of the given list
     * @param addresses
     * @param adapterType
     * @return
     */
    @JsonIgnore
    private static HashMap<String, String> getFormattedAddresses(final Collection<String> addresses, AdapterType adapterType) {

        if (addresses != null) {
            HashMap<String, String> result = new HashMap<String, String>();
            //format the number if its of type SMS and CALL
            for (String address : addresses) {
                result.put(PhoneNumberUtils.formatNumber(address, null), address);
            }
            return result;
        }
        return null;
    }
}
