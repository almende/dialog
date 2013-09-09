
package com.almende.dialog;


import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;


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
        final StringBuilder sb = new StringBuilder();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        return new LoggedPrintStream( sb, byteArrayOutputStream, toLog );
    }
}
