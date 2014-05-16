package com.almende.dialog.model.ddr;

import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;

import com.almende.dialog.agent.AdapterAgent;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.QueryResultIterator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * This is the price applied for a particular {@link DDRType}
 * @author Shravan
 */
public class DDRPrice
{
    /**
     * frequency in which this price is applied
     * @author Shravan
     */
    public enum UnitType
    {
        PART, SECOND, MINUTE, HOUR, DAY, MONTH, YEAR;
        @JsonCreator
        public static UnitType fromJson( String name )
        {
            return valueOf( name.toUpperCase() );
        }
    }
    
    public enum AdapterType
    {
        XMPP( AdapterAgent.ADAPTER_TYPE_XMPP ),
        SMS( AdapterAgent.ADAPTER_TYPE_SMS ),
        CALL( AdapterAgent.ADAPTER_TYPE_BROADSOFT ),
        EMAIL( AdapterAgent.ADAPTER_TYPE_EMAIL ),
        FACEBOOK( AdapterAgent.ADAPTER_TYPE_FACEBOOK ),
        TWITTER( AdapterAgent.ADAPTER_TYPE_TWITTER );

        private String value;

        private AdapterType( String value )
        {
            this.value = value;
        }
        
        public String getName()
        {
            return value;
        }

        /**
         * returns the enum based on the value
         * @param value
         * @return
         */
        public static AdapterType getByValue(String value)
        {
            for ( AdapterType type : values() )
            {
                if(type.name().equalsIgnoreCase( value ))
                {
                    return type;
                }
            }
            return null;
        }
        
        @JsonCreator
        public static AdapterType fromJson( String name )
        {
            return valueOf( name.toUpperCase() );
        }
    }
    
    @Id
    public String id;
    private String ddrTypeId;
    private int units;
    private UnitType unitType;
    private AdapterType adapterType;
    //used incase a particular price is applied to a particular adapter
    private String adapterId;
    private double price;
    private Long startTime;
    private Long endTime;
    private Integer staffleStart;
    private Integer staffleEnd;
    
    /**
     * create (if missing) or updates this document instance
     */
    public void createOrUpdate()
    {
        id = id != null && !id.isEmpty() ? id : ObjectId.get().toStringMongod();
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.storeOrUpdate( this );
    }
    
    /**
     * fetch the ddr type from the datastore
     * @param id
     * @return
     * @throws Exception
     */
    public static DDRPrice getDDRPrice(long id)
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load(DDRPrice.class, id);
    }
    
    /**
     * fetch the ddr records based the input parameters. if no DDRPrice records are found for the adapterId, it 
     * returns the ones matching other indexes
     * @param adapterId
     * @param accountId
     * @param fromAddress
     * @param toAddress
     * @param typeId
     * @param status
     * @return
     */
    public static List<DDRPrice> getDDRPrices( String ddrTypeId, AdapterType adapterType, String adapterId )
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<DDRPrice> query = datastore.find().type( DDRPrice.class );
        //fetch accounts that match
        if ( ddrTypeId != null )
        {
            query = query.addFilter( "ddrTypeId", FilterOperator.EQUAL, ddrTypeId );
        }
        if ( adapterType != null )
        {
            query = query.addFilter( "adapterType", FilterOperator.EQUAL, adapterType.name() );
        }
        ArrayList<DDRPrice> result = null;
        if ( adapterId != null && !adapterId.isEmpty() )
        {
            QueryResultIterator<DDRPrice> ddrIterator = new QueryResultIterator<DDRPrice>( DDRPrice.class, query.now()
                .getCursor() );
            result = new ArrayList<DDRPrice>();
            while ( ddrIterator.hasNext() )
            {
                DDRPrice ddrPrice = ddrIterator.next();
                if ( adapterId.equals( ddrPrice.getAdapterId() ) )
                {
                    result.add( ddrPrice );
                }
            }
        }
        return result != null && !result.isEmpty() ? result : query.now().toArray(); 
    }
    
    /**
     * get all the ddr types
     * @return
     * @throws Exception
     */
    public static List<DDRPrice> getAllDDRTypes()
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.find().type( DDRPrice.class ).now().toArray();
    }
    
    /**
     * gets the reference id to {@link DDRType#typeId}
     * @return
     */
    public String getDdrTypeId()
    {
        return ddrTypeId;
    }
    
    /**
     * sets the reference id to {@link DDRType#typeId}
     * @return
     */
    public void setDdrTypeId( String ddrTypeId )
    {
        this.ddrTypeId = ddrTypeId;
    }
    public int getUnits()
    {
        return units;
    }
    public void setUnits( int unit )
    {
        this.units = unit;
    }
    public UnitType getUnitType()
    {
        return unitType;
    }
    public void setUnitType( UnitType unitType )
    {
        this.unitType = unitType;
    }
    public double getPrice()
    {
        return price;
    }
    public void setPrice( double price )
    {
        this.price = price;
    }
    public Long getStartTime()
    {
        return startTime;
    }
    public void setStartTime( Long startTime )
    {
        this.startTime = startTime;
    }
    public Long getEndTime()
    {
        return endTime;
    }
    public void setEndTime( Long endTime )
    {
        this.endTime = endTime;
    }
    public Integer getStaffleStart()
    {
        return staffleStart;
    }
    public void setStaffleStart( Integer staffleStart )
    {
        this.staffleStart = staffleStart;
    }
    public Integer getStaffleEnd()
    {
        return staffleEnd;
    }
    public void setStaffleEnd( Integer staffleEnd )
    {
        this.staffleEnd = staffleEnd;
    }

    public String getId()
    {
        return id;
    }

    public void setId( String id )
    {
        this.id = id;
    }

    public AdapterType getAdapterType()
    {
        return adapterType;
    }

    public void setAdapterType( AdapterType adapterType )
    {
        this.adapterType = adapterType;
    }

    public String getAdapterId()
    {
        return adapterId;
    }

    public void setAdapterId( String adapterId )
    {
        this.adapterId = adapterId;
    }
}
