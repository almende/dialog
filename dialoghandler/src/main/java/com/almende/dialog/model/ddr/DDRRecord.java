package com.almende.dialog.model.ddr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import com.almende.dialog.util.ServerUtils;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * The actual price charged as part of the service and/or communication cost
 * @author Shravan
 */
public class DDRRecord
{
    /**
     * status of the communication
     */
    public enum CommunicationStatus
    {
        DELIVERED, RECEIEVED, SENT, ERROR, UNKNOWN;
        @JsonCreator
        public static CommunicationStatus fromJson( String name )
        {
            return valueOf( name.toUpperCase() );
        }
    }
    
    @Id
    public String id;
    String adapterId;
    String accountId;
    String fromAddress;
    @JsonIgnore
    Map<String, String> toAddress;
    //creating a dummy serialized version of toAddress as dot(.) in keys is not allowed by mongo 
    String toAddressString;
    String ddrTypeId;
    double quantity;
    long start;
    long duration;
    double totalCost;
    CommunicationStatus status;
    
    public DDRRecord(){}
    
    public DDRRecord( String ddrTypeId, String adapterId, String accountId, double quantity, double totalCost )
    {
        this.ddrTypeId = ddrTypeId;
        this.adapterId = adapterId;
        this.accountId = accountId;
        this.quantity = quantity;
        this.totalCost = totalCost;
    }
    
    public void createOrUpdate()
    {
        id = id != null && !id.isEmpty() ? id : ObjectId.get().toStringMongod();
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.storeOrUpdate( this );
    }
    
    public static DDRRecord getDDRRecord(String id, String accountId) throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        DDRRecord ddrRecord = datastore.load(DDRRecord.class, id);
        if ( ddrRecord != null && ddrRecord.getAccountId() != null && !ddrRecord.getAccountId().equals( accountId ) )
        {
            throw new Exception( String.format( "DDR record: %s is not owned by account: %s", id, accountId ) );
        }
        return ddrRecord;
    }
    
    /**
     * fetch the ddr records based the input parameters. fetches the records that matches to all the 
     * parameters given
     * @param adapterId
     * @param accountId
     * @param fromAddress
     * @param ddrTypeId
     * @param status
     * @return
     */
    public static List<DDRRecord> getDDRRecords( String adapterId, String accountId, String fromAddress,
        String ddrTypeId, CommunicationStatus status )
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<DDRRecord> query = datastore.find().type( DDRRecord.class );
        //fetch accounts that match
        query = query.addFilter( "accountId", FilterOperator.EQUAL, accountId );
        if ( adapterId != null )
        {
            query = query.addFilter( "adapterId", FilterOperator.EQUAL, adapterId );
        }
        if ( fromAddress != null )
        {
            query = query.addFilter( "fromAddress", FilterOperator.EQUAL, fromAddress );
        }
        if ( ddrTypeId != null )
        {
            query = query.addFilter( "ddrTypeId", FilterOperator.EQUAL, ddrTypeId );
        }
        if ( status != null )
        {
            query = query.addFilter( "status", FilterOperator.EQUAL, status.name() );
        }
        return query.now().toArray();
    }
    
    public String getId()
    {
        return id;
    }
    public void setId( String id )
    {
        this.id = id;
    }
    public String getAdapterId()
    {
        return adapterId;
    }
    public void setAdapterId( String adapterId )
    {
        this.adapterId = adapterId;
    }
    public String getAccountId()
    {
        return accountId;
    }
    public void setAccountId( String accountId )
    {
        this.accountId = accountId;
    }
    public String getFromAddress()
    {
        return fromAddress;
    }
    public void setFromAddress( String fromAddress )
    {
        this.fromAddress = fromAddress;
    }
    @JsonIgnore
    public Map<String, String> getToAddress() throws Exception
    {
        if ( toAddress == null && toAddressString == null )
        {
            toAddress = new HashMap<String, String>();
        }
        else if ( ( toAddress == null || toAddress.isEmpty() ) && toAddressString != null )
        {
            toAddress = ServerUtils.deserialize( toAddressString,
                new TypeReference<HashMap<String, String>>(){} );
        }
        return toAddress;
    }
    @JsonIgnore
    public void setToAddress( Map<String, String> toAddress )
    {
        this.toAddress = toAddress;
    }
    public String getDdrTypeId()
    {
        return ddrTypeId;
    }
    public void setDdrTypeId( String ddrTypeId )
    {
        this.ddrTypeId = ddrTypeId;
    }
    public double getQuantity()
    {
        return quantity;
    }
    public void setQuantity( double quantity )
    {
        this.quantity = quantity;
    }
    public long getStart()
    {
        return start;
    }
    public void setStart( long start )
    {
        this.start = start;
    }
    public long getDuration()
    {
        return duration;
    }
    public void setDuration( long duration )
    {
        this.duration = duration;
    }
    public double getTotalCost()
    {
        return totalCost;
    }
    public void setTotalCost( double totalCost )
    {
        this.totalCost = totalCost;
    }
    public CommunicationStatus getStatus()
    {
        return status;
    }
    public void setStatus( CommunicationStatus status )
    {
        this.status = status;
    }

    /**
     * only used by the mongo serializing/deserializing
     * @return
     * @throws Exception
     */
    @JsonProperty("toAddress")
    private String getToAddressString() throws Exception
    {
        toAddressString = ServerUtils.serialize( toAddress );
        return toAddressString;
    }

    /**
     * only used by the mongo serializing/deserializing
     * @param toAddressString
     * @throws Exception
     */
    @JsonProperty("toAddress")
    private void setToAddressString( String toAddressString ) throws Exception
    {
        this.toAddressString = toAddressString;
    }
}
