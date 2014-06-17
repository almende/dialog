package com.almende.dialog.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.agent.DialogAgent;
import com.almende.eve.agent.Agent;
import com.almende.eve.agent.AgentHost;
import com.almende.eve.rpc.jsonrpc.JSONRPC;
import com.almende.eve.rpc.jsonrpc.JSONRPCException;
import com.almende.eve.rpc.jsonrpc.JSONResponse;
import com.almende.eve.state.MemoryState;


public class DialogEveServlet extends HttpServlet {
	private static final long serialVersionUID = 2523444841594323679L;
	private static Logger logger = Logger.getLogger(DialogEveServlet.class.getSimpleName());
	static AgentHost host = AgentHost.getInstance();
	static Agent agent = null;
	static {
		agent = new DialogAgent();
		agent.constr(host, new MemoryState("dialog"));
	}

	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		String response = "";
		try {
			String request = streamToString(req.getInputStream());
			
			response = JSONRPC.invoke(agent, request, agent);
			
		} catch (Exception err) {
			// generate JSON error response
			JSONRPCException jsonError = new JSONRPCException(
					JSONRPCException.CODE.INTERNAL_ERROR, err.getMessage());
			JSONResponse jsonResponse = new JSONResponse(jsonError);
			response = jsonResponse.toString();
			logger.warning("Error handling agent request: "+response);
		}

		// return response
		resp.addHeader("Content-Type", "application/json");
		resp.getWriter().println(response);
	}

	/**
	 * Convert a stream to a string
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private static String streamToString(InputStream in) throws IOException {
		StringBuffer out = new StringBuffer();
		byte[] b = new byte[4096];
		for (int n; (n = in.read(b)) != -1;) {
			out.append(new String(b, 0, n));
		}
		return out.toString();
	}

}
