package com.almende.util.myBlobstore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.wink.common.model.multipart.BufferedInMultiPart;
import org.apache.wink.common.model.multipart.InPart;

import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;

@Path("/blob/{key}")
public class MyBlobStore {
	private static final String				BASEPATH	= "./blobstore/";
	protected TwigCompatibleMongoDatastore	datastore;
	
	public MyBlobStore() {
		datastore = new TwigCompatibleMongoDatastore();
		File folder = new File(BASEPATH);
		if (!folder.exists()){
			folder.mkdir();
		}
	}
	
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces("text/plain")
	public void doUpload(BufferedInMultiPart bimp,
			@PathParam("key") String key, @QueryParam("retPath") String retPath)
			throws URISyntaxException {
		BlobKey blobKey = new BlobKey(key);
		
		OutputStream out = null;
		List<InPart> parts = bimp.getParts();
		if (parts.size() != 1) {
			throw new WebApplicationException(Response.status(
					Status.BAD_REQUEST).build());
		}
		Iterator<InPart> it = parts.iterator();
		byte[] bytes = null;
		while (it.hasNext()) {
			InPart part = (InPart) it.next();
			try {
				InputStream inputStream = part.getInputStream();
				out = new FileOutputStream(BASEPATH + blobKey.getUuid());
				int read = 0;
				bytes = new byte[1024];
				while ((read = inputStream.read(bytes)) != -1) {
					out.write(bytes, 0, read);
				}
				inputStream.close();
				out.flush();
				out.close();
				
				MultivaluedMap<String, String> headers = part.getHeaders();
				
				datastore.store(new FileContentType(blobKey.getUuid(), part
						.getContentType(), headers.getFirst("filename")));
			} catch (IOException e) {
			}
		}
		
		try {
			throw new WebApplicationException(Response.seeOther(
					new URI(URLDecoder.decode(retPath, "UTF-8"))).build());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	public String createUploadUrl(String retpath) {
		BlobKey blobKey = new BlobKey();
		String res = "/dialoghandler/rest/blob/" + blobKey.getUuid() + "?retPath=";
		
		try {
			res += URLEncoder.encode(retpath, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			res += retpath;
			e.printStackTrace();
		}
		return res;
	}
	
	public Map<String, List<BlobKey>> getUploads(HttpServletRequest req) {
		String uuid = req.getRequestURI().replaceFirst("|.*/blob/|", "");
		Map<String, List<BlobKey>> result = new HashMap<String, List<BlobKey>>();
		FileContentType fct = datastore.load(FileContentType.class,uuid);
		List<BlobKey> list = new ArrayList<BlobKey>(1);
		list.add(new BlobKey(uuid));
		result.put(fct.fieldName,list);
		
		return result;
	}
	
	public void delete(BlobKey key) {
		File file = new File(BASEPATH + key.getUuid());
		file.delete();
	}
	
	public int getSize(BlobKey key) {
		File file = new File(BASEPATH + key.getUuid());
		return (int) file.length();
	}
	
	public String getContentType(BlobKey key) {
		FileContentType fct = datastore.load(FileContentType.class,
				key.getUuid());
		return fct.contentType;
	}
	
	public String getHashCode(BlobKey key) {
		File file = new File(BASEPATH + key.getUuid());
		return Integer.toHexString(file.hashCode()).toUpperCase();
	}
	
	public void serve(BlobKey key, HttpServletResponse res) {
		try {
			
			res.setContentLength(getSize(key));
			res.setContentType(getContentType(key));
			res.setHeader("ETag", getHashCode(key));
			
			OutputStream out = new BufferedOutputStream(res.getOutputStream());
			InputStream in = new BufferedInputStream(new FileInputStream(
					BASEPATH + key.getUuid()));
			
			byte[] buffer = new byte[4096];
			int length;
			while ((length = in.read(buffer)) > 0) {
				out.write(buffer, 0, length);
			}
			in.close();
			out.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
