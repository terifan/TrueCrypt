package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


class Stream
{
	public long mClusters;                      // Total number of clusters.
	public long mSize;                          // Total number of bytes.
	public AttributeType mType;
	public int mNameIndex;
	public List<Fragment> mFragments;


	public Stream(int aNameIndex, AttributeType aType, long aSize)
	{
		mNameIndex = aNameIndex;
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
		return "Stream{" + "Clusters=" + mClusters + ", Size=" + mSize + ", Type=" + mType + ", NameIndex=" + mNameIndex + ", _fragments=" + mFragments.size() + '}';
	}
}
