package com.almende.dialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.SortDirection;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;

public class Logger {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Logger.class.getName());
    private static final int adapter_chunk_size = 20;

	private TwigCompatibleMongoDatastore datastore = null;
	
	public Logger() {
		datastore = new TwigCompatibleMongoDatastore();
	}
	
	public void severe(String adapterID, String message) {
        severe( adapterID, null, message);
    }
	
	public void severe(String adapterID, String adapterType, String message) {
        this.log(LogLevel.SEVERE,adapterID,adapterType, message);
    }
	
	public void severe(AdapterConfig adapter, String message) {
		this.log(LogLevel.SEVERE,adapter,message);
	}
	
	public void warning(String adapterID, String message) {
	    warning( adapterID, null, message);
    }

	public void warning(String adapterID, String adapterType, String message) {
        this.log(LogLevel.WARNING,adapterID, adapterType,message);
    }
	
	public void warning(AdapterConfig adapter, String message) {
		this.log(LogLevel.WARNING,adapter,message);
	}

	public void info(String adapterID, String message) {
	    info( adapterID, null, message);
    }
	
	public void info(String adapterID, String adapterType, String message) {
        this.log(LogLevel.INFO,adapterID, adapterType,message);
    }
	
	public void info(AdapterConfig adapter, String message) {
		this.log(LogLevel.INFO,adapter,message);
	}
	
	public void debug(String adapterID, String message) {
	    debug( adapterID, null, message);
    }

	public void debug(String adapterID, String adapterType, String message) {
        this.log(LogLevel.DEBUG,adapterID, adapterType, message);
    }
	
	public void debug(AdapterConfig adapter, String message) {
		this.log(LogLevel.DEBUG,adapter,message);
	}
	
	public void ddr(AdapterConfig adapter, String message) {
		this.log(LogLevel.DDR, adapter, message);
	}
	
	public void log(LogLevel level, String adapterId, String adapterType, String message) {
        datastore.store(new Log(level, adapterId, adapterType, message));
    }
	
	public void log(LogLevel level, AdapterConfig adapter, String message) {
		datastore.store(new Log(level, adapter, message));
	}
	
	public List<Log> find( Collection<String> adapters, Collection<LogLevel> levels, String adapterType, Long endTime, Integer offset, Integer limit)
    {
        List<Log> resultLogs = new ArrayList<Log>();
        if(adapters != null )
        {
            log.info(String.format("Initial adapters size %s", adapters.size()));
            int startingIndex = 0;
            int endIndex = adapters.size() <= adapter_chunk_size ? adapters.size() : adapter_chunk_size;
            for (int chunkCount = 0; chunkCount <= (adapters.size()/adapter_chunk_size) ; chunkCount++)
            {
                log.info(String.format("chunkCount %s startIndex: %s endIndex %s", chunkCount, startingIndex, endIndex));
                List<String> chunkedAdapters = new ArrayList<String>(adapters).subList( startingIndex, endIndex);
                List<Log> logsWithLimit = getLogsWithLimit(chunkedAdapters, levels, adapterType, endTime, offset, limit);
                if(logsWithLimit != null && !logsWithLimit.isEmpty())
                {
                    resultLogs.addAll(logsWithLimit);
                }
                //update indexes
                startingIndex = startingIndex + adapter_chunk_size; 
                endIndex = adapters.size() >= endIndex + adapter_chunk_size ? endIndex + adapter_chunk_size : adapters.size();
            }
        }
        else
        {
            resultLogs = getLogsWithLimit( adapters, levels, adapterType, endTime, offset, limit);
        }
        return resultLogs;
	}

    /**
     * fetches the logs based on the initial {@link Logger#adapter_chunk_size} adapters
     * @return
     */
    private List<Log> getLogsWithLimit( Collection<String> adapters, Collection<LogLevel> levels, String adapterType, Long endTime, Integer offset, Integer limit) {
        RootFindCommand<Log> cmd = datastore.find().type( Log.class);

        if(adapters!=null)
        {
            int endIndex = adapters.size() <= adapter_chunk_size -1 ? adapters.size() : adapter_chunk_size;
            List<String> subAdapterList = new ArrayList<String>(adapters).subList(0, endIndex);
            if(!subAdapterList.isEmpty())
            {
                cmd.addFilter("adapterID", FilterOperator.IN, subAdapterList );
            }
        }

        if (adapterType != null)
            cmd.addFilter("adapterType", FilterOperator.EQUAL, adapterType);

//        if (levels != null)
//            cmd.addFilter("level", FilterOperator.IN, levels);

        if(endTime!=null) {
            cmd.addFilter("timestamp", FilterOperator.LESS_THAN_OR_EQUAL, endTime);
        }
        if(offset==null)
            offset=0;
        if(limit==null)
            limit=50;

        cmd.startFrom(offset);
        cmd.fetchMaximum(limit);
        cmd.addSort("timestamp", SortDirection.DESCENDING);

        Iterator<Log> log = cmd.now();

        ArrayList<Log> logs = new ArrayList<Log>();
        while ( log.hasNext() )
        {
            Log nextLog = log.next();
            /* add the log if the levels are null or within the min severity <br>
            adding a filter IN for the Levels doesnt work in this as there are too many in-memory operations
            to be performed. */
            if(levels == null ||
                    (levels != null && levels.contains( nextLog.getLevel())))
            {
                logs.add( nextLog );
            }
        }
        return logs;
    }
}
