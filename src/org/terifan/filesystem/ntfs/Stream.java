package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


class Stream
{
	public long mClusters;                      // Total number of clusters.
	public long mSize;                          // Total number of bytes.
	public AttributeType mType;
	public String mName;
	public List<Fragment> mFragments;


	public Stream(String aName, AttributeType aType, long aSize)
	{
		mName = aName;
		mType = aType;
		mSize = aSize;
	}


	public List<Fragment> getFragments()
	{
		if (mFragments == null)
		{
			mFragments = new ArrayList<Fragment>();
		}

		return mFragments;
	}


	@Override
	public String toString()
	{
		return "Stream{" + "Clusters=" + mClusters + ", Size=" + mSize + ", Type=" + mType + ", Name=" + mName + ", _fragments=" + mFragments.size() + '}';
	}
}
