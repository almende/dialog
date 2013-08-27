package com.almende.dialog.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.entity.Item;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.code.twig.ObjectDatastore;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

public class TwigStore {
	
	protected ObjectDatastore datastore;
	protected BlobstoreService blobstore = BlobstoreServiceFactory.getBlobstoreService();
	protected BlobInfoFactory blobmeta = new BlobInfoFactory();
	
	public TwigStore() {
		super();
		datastore = new AnnotationObjectDatastore();
	}
	
	public Item create( String path, String parent ) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public Item read( String path ) {
		return datastore.load( Item.class, path );
	}
	
	public Item update( Item item ) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void save( Item item ) {
		// TODO Auto-generated method stub
		
	}
	
	public void delete( Item item ) {
		// TODO Auto-generated method stub
		
	}

	public String createUploadUrl( String retpath ) {
		// TODO Auto-generated method stub
		return blobstore.createUploadUrl( retpath );
	}
	
	public void handleUpload( String path, HttpServletRequest req ) {
		Map<String, List<BlobKey>> map = blobstore.getUploads( req );
		/*for ( Map.Entry<String, List<BlobKey>> entry: map.entrySet() ) {
			System.out.println( "->" + entry.getKey() + " => " + entry.getValue().size() );
		}*/
		Item item = datastore.load( Item.class, path );
		List<BlobKey> list = map.get( "file" );
		if ( list != null && list.size() >= 1 ) {
			Date now = new Date();
			BlobKey key = list.get( 0 );
			BlobInfo info = blobmeta.loadBlobInfo( key );
			String[] split = path.split( "/" );
			String stem = "/";
			for ( int i = 1; i < split.length - 1; i++ ) {
				String s = stem + split[ i ] + "/";
				Item next = datastore.load( Item.class, s );
				if ( next == null ) {
					next = new Item( s, stem );
					next.created = now;
					next.modified = now;
					datastore.store( next );
					System.out.println( " add path " + stem + " => " + s  );
					
				}
				stem = s;
			}
			if ( item == null ) {
				item = new Item( path, stem );
				item.created = now;
			} else {
				blobstore.delete( item.key );
			}
			item.key = key;
			item.modified = now;
			item.etag = info.getMd5Hash();
			item.type = info.getContentType();
			item.length = info.getSize();
			datastore.storeOrUpdate( item );
		} else if ( item != null ) {
			blobstore.delete( item.key );
			datastore.delete( item );
		}
	}
	
	public void handleDownload( Item item, HttpServletResponse res )
	throws IOException {
		blobstore.serve( item.key, res );
	}
	
	public void handleDirectory( Item item, HttpServletResponse res )
	throws IOException {
		res.setContentType( "text/html" );
		res.setCharacterEncoding( "UTF-8" );
		PrintWriter w = res.getWriter();
		
		// Eeew
		w.write(
"<!DOCTYPE html>"
+ "<html>"
	+ "<head>"
		+ "<meta charset=\"UTF-8\" >"
		+ "<title>" + item.path + "</title>"
	+ "</head>"
	+ "<body>"
		+ "<table>"
			+ "<thead>"
				+ "<tr>"
					+ "<th>link</th>"
					+ "<th>type</th>"
					+ "<th>created</th>"
					+ "<th>modified</th>"
				+ "</tr>"
			+ "</thead>"
			+ "<tbody>"
		);
		
		QueryResultIterator<Item> q = datastore.find().type( Item.class ).addFilter( "parent", FilterOperator.EQUAL, item.path ).now();
		while ( q.hasNext() ) {
			Item sub = q.next();
			w.write( "<tr><td><a href=\"" + sub.path + "\">" + sub.path + "</a></td><td>" 
				+ sub.type + "</td><td>" + sub.created + "</td><td>" + sub.modified + "</td></tr>" );
		}
		
		// Eeew
		w.write(
			"</tbody>"
		+ "</table>"
	+ "</body>"
+ "</html>"
		);
	}
}

