
package com.almende.dialog;


import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;


/**
 * used to read the console outputs. useful for testing XMPP amd MAIL locally
 * @author Shravan
 */
public class LoggedPrintStream extends PrintStream
{
    public final StringBuilder buf;
    public final PrintStream underlying;

    public LoggedPrintStream( StringBuilder sb, OutputStream os, PrintStream ul )
    {
        super( os );
        this.buf = sb;
        this.underlying = ul;
    }
    
    public static LoggedPrintStream create( PrintStream toLog )
    {
        try
        {
            final StringBuilder sb = new StringBuilder();
            Field f = FilterOutputStream.class.getDeclaredField( "out" );
            f.setAccessible( true );
            OutputStream psout = (OutputStream) f.get( toLog );
            return new LoggedPrintStream( sb, new FilterOutputStream( psout )
            {
                public void write( int b ) throws IOException
                {
                    super.write( b );
                    sb.append( (char) b );
                }
            }, toLog );
        }
        catch ( Exception exception )
        {
        }
        return null;
    }
    
    /**
     * reset the System.out/err back to default
     */
    public static void dispose()
    {
        System.out.flush();
        System.setOut( System.out );
        System.setErr( System.err );
    }
}
