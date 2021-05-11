package org.terifan.filesystem.ntfs;


class Fragment
{
	public long mLcn;                // Logical cluster number, location on disk.
	public long mNextVcn;            // Virtual cluster number of next fragment.


	public Fragment(long aLcn, long aNextVcn)
	{
		mLcn = aLcn;
		mNextVcn = aNextVcn;
	}


	@Override
	public String toString()
	{
		return "Fragment{" + "Lcn=" + mLcn + ", NextVcn=" + mNextVcn + '}';
	}
}
