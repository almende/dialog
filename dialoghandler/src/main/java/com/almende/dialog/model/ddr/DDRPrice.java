package com.almende.dialog.model.ddr;

import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.util.twigmongo.FilterOperator;
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
         * returns the enum based on the name or the value
         * @param value
         * @return
         */
        public static AdapterType getByValue( String value )
        {
            for ( AdapterType type : values() )
            {
                if ( type.getName().equalsIgnoreCase( value ) || type.name().equalsIgnoreCase( value ) )
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
    private Double price;
    private Long startTime;
    private Long endTime;
    private Integer staffleStart;
    private Integer staffleEnd;
    //used incase this price model has some specific charecteristics. e.g. price for landline numbers for a country
    private String keyword;
    
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
     * @param ddrTypeId
     * @param adapterType optional field. If no prices are fetched for this adapterType a generic one will be returned. 
     * this is also updated if missing and adapterId is given
     * @param adapterId optional field. If no prices are fetched for this adapterId a generic one will be returned. 
     * @param unitType optional field. If no prices are fetched for this unitType a generic one will be returned.
     * @return
     */
    public static List<DDRPrice> getDDRPrices( String ddrTypeId, AdapterType adapterType, String adapterId, UnitType unitType,
        String keyword)
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<DDRPrice> query = datastore.find().type( DDRPrice.class );
        //fetch accounts that match
        if ( ddrTypeId != null )
        {
            query = query.addFilter( "ddrTypeId", FilterOperator.EQUAL, ddrTypeId );
        }
        //force use the adapterType if only the adapterId is found  
        if(adapterType == null && adapterId != null && !adapterId.isEmpty())
        {
            AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig( adapterId );
            if(adapterConfig != null)
            {
                adapterType = AdapterType.getByValue( adapterConfig.getAdapterType() );
            }
        }
        if ( unitType != null )
        {
            query = query.addFilter( "unitType", FilterOperator.EQUAL, unitType.name() );
        }
        List<DDRPrice> allPrices = query.now().toArray();
        List<DDRPrice> result = null;
        //check if any special rates are given to the particular adapterID, if not use the generic one for the adapter type
        //this is the most specific rates given
        if ( adapterId != null && !adapterId.isEmpty() )
        {
            result = new ArrayList<DDRPrice>();
            for ( DDRPrice ddrPrice : allPrices )
            {
                if ( adapterId.equals( ddrPrice.getAdapterId() ) )
                {
                    result.add( ddrPrice );
                }
            }
        }
        result = result != null && !result.isEmpty() ? result : allPrices;
        //check if any special rates are given to the particular keyword, if not use the generic one for 
        //any prices that are fetched from adapterType 
        if ( adapterType != null)
        {
            List<DDRPrice> ddrPricesByAdapterType = new ArrayList<DDRPrice>();
            for ( DDRPrice ddrPrice : result )
            {
                if ( adapterType.equals( ddrPrice.getAdapterType() ) )
                {
                    ddrPricesByAdapterType.add( ddrPrice );
                }
            }
            result = !ddrPricesByAdapterType.isEmpty() ? ddrPricesByAdapterType : result;
        }
        //check if any special rates are given to the particular keyword, if not use the generic one for 
        //any prices that are fetched from keyword
        if ( keyword != null && !keyword.isEmpty())
        {
            List<DDRPrice> ddrPricesByKeyword = new ArrayList<DDRPrice>();
            for ( DDRPrice ddrPrice : result )
            {
                if ( keyword.equalsIgnoreCase( ddrPrice.getKeyword() ) )
                {
                    ddrPricesByKeyword.add( ddrPrice );
                }
            }
            result = !ddrPricesByKeyword.isEmpty() ? ddrPricesByKeyword : result;
        }
        return result != null ? result : allPrices; 
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
    public Double getPrice()
    {
        return price;
    }
    public void setPrice( Double price )
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
    
    /**
     * simple check to see if the timestamp is between the startTime and the endTime
     * @param timestamp
     * @return
     */
    public boolean isValidForTimestamp(long timestamp)
    {
        if ( ( startTime != null && startTime <= timestamp ) && ( endTime == null || endTime >= timestamp ) )
        {
            return true;
        }
        return false;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword( String keyword )
    {
        this.keyword = keyword;
    }
}
