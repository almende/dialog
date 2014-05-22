package com.almende.dialog.agent;

import java.util.List;
import java.util.logging.Logger;

import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.AdapterType;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.TimeUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.annotation.Optional;
import com.almende.util.TypeUtil;
import com.askfast.commons.agent.intf.DDRRecordAgentInterface;

@Access(AccessType.PUBLIC)
public class DDRRecordAgent extends Agent implements DDRRecordAgentInterface
{
    private static final Logger log = Logger.getLogger( DDRRecordAgent.class.getName() );
    
    @Override
    protected void onCreate()
    {
        //check if all DDR categories are created on bootstrapping this agent
        for ( DDRTypeCategory ddrCategory : DDRTypeCategory.values() )
        {
            if ( !ddrCategory.equals( DDRTypeCategory.OTHER ) ) //ignore other
            {
                try
                {
                    DDRType ddrType = DDRType.getDDRType( ddrCategory );
                    if ( ddrType == null )
                    {
                        ddrType = new DDRType();
                        ddrType.setCategory( ddrCategory );
                        ddrType.setName( ddrCategory.name() );
                        ddrType.createOrUpdate();
                    }
                }
                catch ( Exception e )
                {
                    log.severe( String.format( "DDRType creation failed for type: %s. Error: %s", ddrCategory.name(),
                        e.getLocalizedMessage() ) );
                }
            }
        }
    }
    
    /**
     * get a specific DDR record if it is owned by the account
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public Object getDDRRecord( @Name( "ddrRecordId" ) String id, @Name( "accountId" ) String accountId,
        @Name( "shouldGenerateCosts" ) @Optional Boolean shouldGenerateCosts,
        @Name( "shouldIncludeServiceCosts" ) @Optional Boolean shouldIncludeServiceCosts) throws Exception
    {
        DDRRecord ddrRecord = DDRRecord.getDDRRecord( id, accountId );
        ddrRecord.setShouldGenerateCosts(shouldGenerateCosts);
        ddrRecord.setShouldIncludeServiceCosts( shouldIncludeServiceCosts );
        return ddrRecord;
    }
    
    /**
     * get a specific DDR record if it is owned by the account
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public Object getDDRRecords( @Name( "adapterId" ) @Optional String adapterId,
        @Name( "accountId" ) String accountId, @Name( "fromAddress" ) @Optional String fromAddress,
        @Name( "typeId" ) @Optional String typeId, @Name( "communicationStatus" ) @Optional String status,
        @Name( "shouldGenerateCosts" ) @Optional Boolean shouldGenerateCosts, 
        @Name( "shouldIncludeServiceCosts" ) @Optional Boolean shouldIncludeServiceCosts) throws Exception
    {
        CommunicationStatus communicationStatus = status != null && !status.isEmpty() ? CommunicationStatus
            .fromJson( status ) : null;
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords( adapterId, accountId, fromAddress, typeId, communicationStatus );
        if ( shouldGenerateCosts != null && shouldGenerateCosts )
        {
            for ( DDRRecord ddrRecord : ddrRecords )
            {
                ddrRecord.setShouldGenerateCosts( shouldGenerateCosts );
                ddrRecord.setShouldIncludeServiceCosts( shouldIncludeServiceCosts );
            }
        }
        return ddrRecords;
    }
    
    /**
     * create a DDR Type. Access to this
     * @param name
     * @throws Exception
     */
    public Object createDDRType( @Name( "nameForDDR" ) String name, @Name( "ddrTypeCategory" ) String categoryString )
    throws Exception
    {
        DDRTypeCategory category = categoryString != null && !categoryString.isEmpty() ? DDRTypeCategory
            .fromJson( categoryString ) : null;
        DDRType ddrType = new DDRType();
        ddrType.setName( name );
        ddrType.setCategory( category );
        ddrType.createOrUpdate();
        return ddrType;
    }
    
    /**
     * get all the DDR Type. Access to this 
     * @param name
     */
    public Object getAllDDRTypes()
    {
        return DDRType.getAllDDRTypes();
    }
    
    /**
     * create a new Price Type. Access to this must be from root account 
     * @param ddrTypeId
     * @param startTime
     * @param endTime
     * @param price
     * @param staffleStart
     * @param staffleEnd
     * @param unit
     * @param unitTypeString
     * @return
     */
    public Object createDDRPrice( @Name( "ddrTypeId" ) String ddrTypeId, @Name( "startTime" ) @Optional Long startTime,
        @Name( "endTime" ) @Optional Long endTime, @Name( "price" ) Double price,
        @Name( "staffleStart" ) @Optional Integer staffleStart, @Name( "staffleEnd" ) @Optional Integer staffleEnd,
        @Name( "unit" ) @Optional Integer unit, @Name( "unitType" ) @Optional String unitTypeString,
        @Name( "adapterType" ) @Optional String adapterTypeString, @Name( "adapterId" ) @Optional String adapterId )
    {
        //set default values for some optional field
        startTime = startTime != null ? startTime : TimeUtils.getServerCurrentTimeInMillis();
        //add end time as 5yrs from now
        endTime = endTime != null ? endTime : TimeUtils.getServerCurrentTime().plusYears( 5 ).getMillis();
        unit = unit != null ? unit : 1;
        unitTypeString = unitTypeString != null ? unitTypeString : UnitType.PART.name();
        
        DDRPrice ddrPrice = new DDRPrice();
        ddrPrice.setDdrTypeId( ddrTypeId );
        ddrPrice.setEndTime( endTime );
        ddrPrice.setPrice( price );
        ddrPrice.setStaffleEnd( staffleEnd );
        ddrPrice.setStaffleStart( staffleStart );
        ddrPrice.setStartTime( startTime );
        ddrPrice.setUnits( unit );
        ddrPrice.setAdapterId( adapterId );
        UnitType unitType = unitTypeString != null && !unitTypeString.isEmpty() ? UnitType.fromJson( unitTypeString )
                                                                               : null;
        AdapterType adapterType = adapterTypeString != null && !adapterTypeString.isEmpty() ? AdapterType
            .getByValue( adapterTypeString ) : null;
        ddrPrice.setUnitType( unitType );
        ddrPrice.setAdapterType( adapterType );
        ddrPrice.createOrUpdate();
        return ddrPrice;
    }
    
    /**
     * create a new {@link DDRPrice}Price, a new {@link DDRType} DDRType based on the supplied name and links them
     * @param ddrTypeName
     * @param startTime
     * @param endTime
     * @param price
     * @param staffleStart
     * @param staffleEnd
     * @param unit
     * @param unitTypeString
     * @return
     * @throws Exception 
     */
    public Object createDDRPriceWithNewDDRType( @Name( "nameForDDR" ) String name,
        @Name( "ddrTypeCategory" ) String categoryString, @Name( "startTime" ) @Optional Long startTime,
        @Name( "endTime" ) @Optional Long endTime, @Name( "price" ) Double price,
        @Name( "staffleStart" ) @Optional Integer staffleStart, @Name( "staffleEnd" ) @Optional Integer staffleEnd,
        @Name( "unit" ) @Optional Integer unit, @Name( "unitType" ) @Optional String unitTypeString,
        @Name( "adapterType" ) @Optional String adapterTypeString, @Name( "adapterId" ) @Optional  String adapterid )
    throws Exception
    {
        Object ddrTypeObject = createDDRType( name, categoryString );
        TypeUtil<DDRType> injector = new TypeUtil<DDRType>(){};
        DDRType ddrType = injector.inject( ddrTypeObject );
        return createDDRPrice( ddrType.getTypeId(), startTime, endTime, price, staffleStart, staffleEnd, unit,
            unitTypeString, adapterTypeString, adapterid );
    }
    
    
    /**
     * get all the DDR Type. Access to this 
     * @param name
     */
    public Object getDDRPrice(@Name( "id" ) Long id)
    {
        return DDRPrice.getDDRPrice( id );
    }
    
    /**
     * get DDRPrices based on the the supplied params 
     * @param ddrTypeId 
     * @param units 
     * @param unitType 
     */
    public Object getDDRPrices( @Name( "ddrTypeId" ) @Optional String ddrTypeId,
        @Name( "adapterType" ) @Optional String adapterTypeString, @Name( "adapterId" ) @Optional String adapterId,
        @Name( "unitType" ) @Optional String unitTypeString )
    {
        AdapterType adapterType = adapterTypeString != null && !adapterTypeString.isEmpty() ? AdapterType
            .getByValue( adapterTypeString ) : null;
        UnitType unitType = unitTypeString != null && !unitTypeString.isEmpty() ? UnitType.fromJson( unitTypeString )
                                                                               : null;
        return DDRPrice.getDDRPrices( ddrTypeId, adapterType, adapterId, unitType );
    }
}
