package com.almende.util.twigmongo;


import java.util.Iterator;

import com.mongodb.DBCursor;

public class QueryResultIterator<E>  implements Iterator<E> {
	
	private Class<E> type = null;
	private DBCursor cursor = null;

	public QueryResultIterator(Class<E> clazz, DBCursor cursor){
		this.type = clazz;
		this.cursor = cursor;
	}
	
	public DBCursor getCursor() {
		return cursor;
	}
	
	@Override
	public boolean hasNext() {
		return cursor.hasNext();
	}

	@Override
	public E next() {
		return JOM.getInstance().convertValue(cursor.next(), this.type);
	}

	@Override
	public void remove() {
		cursor.remove();
	}
	
}
