package com.almende.dialog.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.entity.Item;

public class UploadServlet extends HttpServlet {
	
	private static Logger					logger				= Logger.getLogger(UploadServlet.class
																		.getName());
	final static public SimpleDateFormat	RFC2822				= new SimpleDateFormat(
																		"EEE, dd MMM yyyy HH:mm:ss Z");
	private static final long				serialVersionUID	= -4580047966789855470L;
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		
		Map<String, String> headers = new LinkedHashMap<String, String>(16,
				0.75F);
		
		Enumeration<String> names = req.getHeaderNames();
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			headers.put(name.trim().toLowerCase(), req.getHeader(name));
		}
		req.setAttribute("headers", headers);
		
		String prefix = req.getContextPath() + req.getServletPath();
		String path = req.getPathInfo();
		if (path == null || path.length() == 0) {
			res.sendRedirect(prefix + "/");
			return;
		}
		String query = req.getQueryString();
		if (query == null) {
			query = "";
		} else {
			query = "?" + query;
		}
		req.setAttribute("prefix", prefix);
		req.setAttribute("path", path);
		req.setAttribute("query", query);
		
		req.setAttribute("form", req.getParameter("!") != null);
		req.setAttribute("url", req.getParameter("url") != null);
		super.service(req, res);
	}
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		logger.info("get");
		
		String prefix = (String) req.getAttribute("prefix");
		String path = (String) req.getAttribute("path");
		String query = (String) req.getAttribute("query");
		TwigStore store = new TwigStore();
		
		if ((Boolean) req.getAttribute("form")) {
			
			res.setContentType("text/html");
			res.setCharacterEncoding("UTF-8");
			PrintWriter w = res.getWriter();
			
			// Eeeeew
			w.print("<!DOCTYPE html>" + "<html>" + "<head>"
					+ "<meta charset=\"UTF-8\" />" + "<title>!" + path
					+ "</title>" + "</head>" + "<body>" + "<form action=\""
					+ store.createUploadUrl(path, prefix + path + query)
					+ "\" method=\"post\" enctype=\"multipart/form-data\">"
					+ "<input type=\"file\" name=\"file\" />" + "<hr />"
					+ "<input type=\"button\" value=\"cancel\""
					+ " onclick=\"window.location='" + prefix + path
					+ stripQuery(query, "!") + "'\" />"
					+ "<input type=\"submit\" value=\"submit\" />" + "</form>"
					+ "</body>" + "</html>");
			
		} else if ((Boolean) req.getAttribute("url")) {
			
			res.setContentType("text/html");
			res.setCharacterEncoding("UTF-8");
			PrintWriter w = res.getWriter();
			w.print(store.createUploadUrl(path, prefix + path + query));
			
		} else {
			
			@SuppressWarnings("unchecked")
			Map<String, String> headers = (Map<String, String>) req
					.getAttribute("headers");
			Item item = store.read(path);
			if (item == null) {
				res.sendError(404);
			} else {
				if (item.type == null) {
					// TODO we could write this HTML to the blobstore too?
					// although it will be a hassle to keep it up to date...
					store.handleDirectory(item, res);
				} else {
					try {
						String head;
						if (((head = headers.get("if-none-match")) != null && head
								.equals(item.etag))
								|| ((head = headers.get("if-modified-since")) != null && RFC2822
										.parse(head).after(item.modified))) {
							// System.out.println("cached version for " +
							// item.path );
							res.setStatus(304);
						} else {
							res.setHeader("Etag", item.etag);
							res.setHeader("Last-Modified",
									RFC2822.format(item.modified));
							res.setHeader("Cache-Control",
									"max-age=3600, public, must-revalidate");
							res.setHeader("Expires", RFC2822.format(new Date(
									System.currentTimeMillis() + 3600 * 1000)));
							if (path.endsWith(".wav")) res.setHeader(
									"Content-Type", "audio/basic");
							else res.setHeader("Content-Type", item.type);
							store.handleDownload(item, res);
						}
					} catch (ParseException x) {
						// this happens when request header contains malformed
						// date..
						res.sendError(400);
					}
				}
			}
			
		}
		
	}
	
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		logger.info("post");
		
		String prefix = (String) req.getAttribute("prefix");
		String path = (String) req.getAttribute("path");
		String query = (String) req.getAttribute("query");
		TwigStore store = new TwigStore();
		
		if ((Boolean) req.getAttribute("form")) {
			
			query = stripQuery(query, "!");
			store.handleUpload(path + query, req);
			res.sendRedirect(prefix + path);
		} else if ((Boolean) req.getAttribute("url")) {
			query = stripQuery(query, "url");
			store.handleUpload(path + query, req);
			
			String resp = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
					+ "<vxml xmlns=\"http://www.w3.org/2001/vxml\" version=\"2.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.w3.org/2001/vxml http://www.w3.org/TR/2007/REC-voicexml21-20070619/vxml.xsd\">"
					+ "<form>" + "<block>"
					+ "<var name=\"response\" expr=\"'SUCCESS'\"/>"
					+ "<return namelist=\"response\"/>" + "</block>"
					+ "</form>" + "</vxml>";
			
			res.getWriter().write(resp);
		} else {
			res.sendError(400); // TODO REST upload
		}
	}
	
	private String stripQuery(String query, String symbol) {
		int i = query.indexOf(symbol);
		int j = query.indexOf("&", i + 1);
		if (j > 0) {
			query = query.substring(0, i) + query.substring(j + 1);
		} else {
			query = query.substring(0, i);
		}
		if (query.endsWith("&")) {
			query = query.substring(0, query.length() - 1);
		}
		if (query.endsWith("?")) {
			query = query.substring(0, query.length() - 1);
		}
		return query;
	}
}
