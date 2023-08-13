
package com.car.common.util;

import java.util.ArrayList;
import java.util.List;


/**
 * A thread class which internal has a WorkReq queue
 *  
 * You can addReq to add some long-time work to it.
 */
public final class WorkThread extends Thread
{
	//dispatch modes
	public static final int BOTH_SIDES = 0;
	public static final int FIFO = 1;
	public static final int LIFO = 2;
	
	private boolean mStop = false;	//stop after current task.
	private List<WorkReq> mList = new ArrayList<WorkReq>();
	private WorkReq mCurReq = null;
	private volatile boolean mWaiting = false;
	private int mDispatchMode = BOTH_SIDES;
	private int mListSize = 20;
	
	public WorkThread()
	{
		super("car.WorkThread");
	}
	
	public WorkThread(String name)
	{
		super(name);
	}
	
	public void setDispatchMode(int mode) {
		mDispatchMode = mode;
	}
	
	public void setReqListSize(int size) {
		mListSize = size;
	}

	public void run()
	{
		while (!mStop)
		{
			synchronized (mList)
			{
				while(!mStop && mList.isEmpty()){
					mWaiting = true;
					try {
						mList.wait();
					}
					catch(InterruptedException e){
					}
					
					mWaiting = false;
				}
				
				if(!mStop){
					if (mDispatchMode == BOTH_SIDES) {
						if (mList.size() % 2 == 0)
							mCurReq = mList.remove(0);
						else {
							mCurReq = mList.remove(mList.size() - 1);
						}
					} else if (mDispatchMode == FIFO) {
						mCurReq = mList.remove(mList.size() - 1);
					} else if (mDispatchMode == LIFO) {
						mCurReq = mList.remove(0);
					}
					mList.notify();
				}
			}
			
			if(mCurReq != null)
			{
				mCurReq.execute();
				synchronized (mList) {
					mCurReq = null;
				}
			}
		}
		
		
		synchronized (mList)
		{
			mList.clear();			
		}
	}

	public void exit()
	{			
		synchronized (mList)
		{			
			mStop = true;
			if(mCurReq != null)
				mCurReq.cancel();
				
			mList.clear();
			
			if(mWaiting)
				mList.notify();									
		}
	}
	
	public void addReq(WorkReq req)
	{
		synchronized (mList)
		{
			mList.add(0, req);
			if(mWaiting)
				mList.notify();
		}
	}
	
	public void addReqWithLimit(WorkReq req) {
		synchronized (mList) {
			if (mList.size() > mListSize) {
				WorkReq reqRemoved = null;
				if (mDispatchMode == BOTH_SIDES) {
					if (mList.size() % 2 == 0)
						reqRemoved = mList.remove(mList.size() - 1);
					else
						reqRemoved = mList.remove(0);
				} else if (mDispatchMode == FIFO) {
					reqRemoved = mList.remove(0);
				} else if (mDispatchMode == LIFO) {
					reqRemoved = mList.remove(mList.size() - 1);
				}
				reqRemoved.cancel();
			}

			mList.add(0, req);
			if (mWaiting)
				mList.notify();
		}
	}

	public void addReqWithLimitandWait(WorkReq req) {
		synchronized (mList) {
			while (mList.size() > mListSize) {
				try {
					mList.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			mList.add(0, req);
			if (mWaiting)
				mList.notify();
		}
	}
	
	public void cancelReqsList()
	{
		synchronized (mList)
		{
			for(int i = mList.size() - 1; i >=0; i--)
			{
				WorkReq req = mList.remove(i);
				req.cancel();
			}
			
			if(mWaiting)
				mList.notify();
		}
	}
	
	public boolean isDuplicateWorking(Match4Req m)
	{
		synchronized (mList)
		{
			if(mCurReq != null && m.matchs(mCurReq))
				return true;

			for(int i = 0; i < mList.size(); i++)
			{
				WorkReq req1 = mList.get(i);
				if(m.matchs(req1))
					return true;
			}
		}		
		return false;
	}
	
	public WorkReq getDuplicateWorkReq(Match4Req m)
	{
		synchronized (mList)
		{
			if(mCurReq != null && m.matchs(mCurReq))
				return mCurReq;

			for(int i = 0; i < mList.size(); i++)
			{
				WorkReq req1 = mList.get(i);
				if(m.matchs(req1))
					return req1;
			}
		}
		
		return null;
	}
	
	public boolean cancelReq(WorkReq req) {
		synchronized (mList)
		{
			if(mCurReq != null && req == mCurReq) {
				mCurReq.cancel();
				return true;
			}
				
			for(int i = mList.size() - 1; i >=0; i--)
			{
				WorkReq r = mList.get(i);
				if( r == req){
					req.cancel();
					mList.remove(i);
					return true;
				}
			}
		}
		return false;
	}
	
	public void cancelReqs(Match4Req m)
	{
		synchronized (mList)
		{
			if(mCurReq != null && m.matchs(mCurReq))
				mCurReq.cancel();
				
			for(int i = mList.size() - 1; i >=0; i--)
			{
				WorkReq req = mList.get(i);
				if(m.matchs(req)) {
					req.cancel();
					mList.remove(i);
				}
			}
		}
	}
}
