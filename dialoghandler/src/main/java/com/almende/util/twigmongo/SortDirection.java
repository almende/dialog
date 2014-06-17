package com.almende.util.twigmongo;

public class SortDirection {
	public static final SortDirection DESCENDING = new SortDirection(-1);
	public static final SortDirection ASCENDING = new SortDirection(1);
	
	private int direction=1;
	
	public SortDirection(){
	};
	private SortDirection(int type){
		this.direction = type;
	};
	
	public String toString(){
		return Integer.toString(direction);
	}
	public void setDirection(int direction){
		this.direction=direction;
	}
	public int getDirection(){
		return this.direction;
	}
}
