package com.almende.dialog.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.entity.Item;
import com.almende.util.myBlobstore.BlobKey;
import com.almende.util.myBlobstore.MyBlobStore;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.QueryResultIterator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;

public class TwigStore {
	
	protected TwigCompatibleMongoDatastore datastore;
	protected MyBlobStore blobstore = new MyBlobStore();
	
	public TwigStore() {
		datastore = new TwigCompatibleMongoDatastore();
	}
	
	public Item read( String path ) {
		return datastore.load( Item.class, path );
	}
	
	public String createUploadUrl( String filename, String retpath ) {
		return blobstore.createUploadUrl( retpath, filename );
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
			item.etag = blobstore.getHashCode(key);
			item.type = blobstore.getContentType(key);
			item.length = blobstore.getSize(key);
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

