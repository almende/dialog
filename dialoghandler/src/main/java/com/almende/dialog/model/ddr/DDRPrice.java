package com.almende.dialog.model.ddr;

import java.util.List;

import org.bson.types.ObjectId;

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
    
    @Id
    public String id;
    private String ddrTypeId;
    private int units;
    private UnitType unitType;
    private double price;
    private long startTime;
    private long endTime;
    private int staffleStart;
    private int staffleEnd;
    
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
     * fetch the ddr records based the input parameters. fetches the records that matches to all the 
     * parameters given
     * @param adapterId
     * @param accountId
     * @param fromAddress
     * @param toAddress
     * @param typeId
     * @param status
     * @return
     */
    public static List<DDRPrice> getDDRPrices( String ddrTypeId, Integer units, UnitType unitType )
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<DDRPrice> query = datastore.find().type( DDRPrice.class );
        //fetch accounts that match
        if ( ddrTypeId != null )
        {
            query = query.addFilter( "ddrTypeId", FilterOperator.EQUAL, ddrTypeId );
        }
        if ( units != null )
        {
            query = query.addFilter( "units", FilterOperator.EQUAL, units );
        }
        if ( unitType != null )
        {
            query = query.addFilter( "unitType", FilterOperator.EQUAL, unitType.name() );
        }
        return query.now().toArray();
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
    public long getStartTime()
    {
        return startTime;
    }
    public void setStartTime( long startTime )
    {
        this.startTime = startTime;
    }
    public long getEndTime()
    {
        return endTime;
    }
    public void setEndTime( long endTime )
    {
        this.endTime = endTime;
    }
    public int getStaffleStart()
    {
        return staffleStart;
    }
    public void setStaffleStart( int staffleStart )
    {
        this.staffleStart = staffleStart;
    }
    public int getStaffleEnd()
    {
        return staffleEnd;
    }
    public void setStaffleEnd( int staffleEnd )
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
}
