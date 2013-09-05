
package com.almende.dialog;


import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogService;
import com.google.appengine.api.log.RequestLogs;


/**
 * used to read the console outputs. useful for testing XMPP amd MAIL locally
 * @author Shravan
 */
public class LoggedPrintStream extends PrintStream
{
    public final PrintStream underlying;
    public final StringBuilder buf;
    public final OutputStream outputStream;
    
    private LoggedPrintStream( StringBuilder sb, OutputStream os, PrintStream ul )
    {
        super( os );
        outputStream = os;
        this.buf = sb;
        this.underlying = ul;
    }
    
    public static LoggedPrintStream create( PrintStream toLog )
    {
//        try
//        {
            final StringBuilder sb = new StringBuilder();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            return new LoggedPrintStream(sb, byteArrayOutputStream, toLog);
            
//            Field f = FilterOutputStream.class.getDeclaredField( "out" );
//            f.setAccessible( true );
//            OutputStream psout = (OutputStream) f.get( toLog );
//            return new LoggedPrintStream( sb, new FilterOutputStream( psout )
//            {
//                public void write( int b ) throws IOException
//                {
//                    super.write( b );
//                    sb.append( (char) b );
//                }
//            }, toLog );
//        }
//        catch ( Exception exception )
//        {
//        }
//        return null;
    }
    
    public static ArrayNode getLogs()
    {
        LogService ls = ParallelInit.getLogService();
        LogQuery query = LogQuery.Builder.withDefaults();
        query.includeAppLogs(true);;
        ObjectMapper om = ParallelInit.getObjectMapper();

        ArrayNode result = om.createArrayNode();
        long start = ( System.currentTimeMillis() - 86400000 ) * 1000;
        query.startTimeUsec( start );

        Iterable<RequestLogs> records = ls.fetch( query );
        for ( RequestLogs record : records )
        {
            for ( AppLogLine appLog : record.getAppLogLines() )
            {
                String msg = appLog.getLogMessage();
                if ( msg.startsWith( "com.almende.dialog.DDRWrapper log:" ) )
                {
                    //log.warning("checking record:"+msg);
                    JsonNode rec;
                }

            }
        }
        return result;
    }
    
    public OutputStream getOutput()
    {
        return outputStream;
    }
}
