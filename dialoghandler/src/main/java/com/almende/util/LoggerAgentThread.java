package com.almende.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import com.almende.dialog.agent.LoggerProxyAgent;
import com.askfast.commons.eve.EveUtil;

/**
 * Logger thread for giving a singelton instance of the LoggerAgent
 * 
 * @author shravan
 *
 */
public class LoggerAgentThread extends Thread {

    public static final String HTTP_LOGGER_EVE_PATH = "eve_logger.yaml";
    protected final static Logger log = Logger.getLogger(LoggerAgentThread.class.getSimpleName());
    private ServletContext servletContext = null;

    public LoggerAgentThread(final ServletContext servletContext) {

        this.servletContext = servletContext;
    }

    @Override
    public void run() {

        try {
            if (ParallelInit.loggerAgent == null) {
                ParallelInit.loggerAgent = initAgent();
            }
            ParallelInit.loggerAgentActive = true;
        }
        catch (Exception e) {
            e.printStackTrace();
            ParallelInit.loggerAgentActive = false;
        }
    }

    private LoggerProxyAgent initAgent() throws Exception {

        InputStream eveFileStream = null;
        if (servletContext != null) {
            eveFileStream = servletContext.getResourceAsStream("/WEB-INF/" + HTTP_LOGGER_EVE_PATH);
        }
        else {
            String eveLoggerPath = System.getProperty("user.dir") + "/src/main/webapp/WEB-INF/" + HTTP_LOGGER_EVE_PATH;
            log.info("eve logger path: " + eveLoggerPath);
            eveFileStream = new FileInputStream(new File(eveLoggerPath));
        }
        LoggerProxyAgent agent = null;
        try {
            if (eveFileStream != null) {
                agent = (LoggerProxyAgent) EveUtil.loadAgent(eveFileStream);
            }
        }
        finally {
            if (eveFileStream != null) {
                eveFileStream.close();
            }
        }
        return agent;
    }
}
