package com.car.common.util;

public final class IntHashMap {
	public final static int nullValue = 0;
	
	final static class Entry {
		Entry(int key, int value) {
			this.key = key;
			this.value = value;
			this.next = null;
		}
		int key;
		int value;
		Entry next;
	}
	
	private final int mCapacity; 
	private final Entry[] mTables;
	private int mCollision;
	private int mSize;
	
	public IntHashMap(int capacity) {
		this.mCapacity = capacity;
		this.mTables = new Entry[capacity];
	}
	
	public int get(int key) {
		int h = key;
		//h ^= (h >>> 20) ^ (h >>> 12);
		//h = h ^ (h >>> 7) ^ (h >>> 4);
        
		for(Entry p = mTables[h % mCapacity]; p != null; p = p.next) {
			if( p.key == key)
				return p.value;
		}
		return nullValue;
	}
	
	public int put(int key, int value) {
		int h = key;
        //h ^= (h >>> 20) ^ (h >>> 12);
        //h = h ^ (h >>> 7) ^ (h >>> 4);

		int n = h % mCapacity;
		Entry p = mTables[n];
		Entry prev = null; 
		while( p != null) {
			if( p.key == key) {
				int oldValue = p.value;
				p.value = value;
				return oldValue;
			}
			
			mCollision++;
			//Log.d(this.getClass().getName(), "Got collision for key:" + key +"," + p.key);
			
			prev = p;
			p = p.next;
			
		}
		p = new Entry(key, value);
		if( prev != null)
			prev.next = p;
		else
			mTables[n] = p;
		
		mSize++;
		return nullValue;
	}
	
	public int collision() {
		return mCollision;
	}
	
	public int capacity() {
		return mCapacity;		
	}
	
	public int size() {
		return mSize;
	}
}
