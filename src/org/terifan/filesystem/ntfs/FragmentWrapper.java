package org.terifan.filesystem.ntfs;


class FragmentWrapper implements IFragment
{
	StreamWrapper mOwner;
	Fragment mFragment;


	public FragmentWrapper(StreamWrapper aOwner, Fragment aFragment)
	{
		mOwner = aOwner;
		mFragment = aFragment;
	}


	public long getLcn()
	{
		return mFragment.mLcn;
	}


	public long getNextVcn()
	{
		return mFragment.mNextVcn;
	}


	@Override
	public String toString()
	{
		return "FragmentWrapper{" + "mFragment=" + mFragment + '}';
	}
}
