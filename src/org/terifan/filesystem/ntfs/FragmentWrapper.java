package org.terifan.filesystem.ntfs;


// Add some functionality to the basic stream
class FragmentWrapper implements IFragment
{
	StreamWrapper _owner;
	Fragment _fragment;

	public FragmentWrapper(StreamWrapper owner, Fragment fragment)
	{
		_owner = owner;
		_fragment = fragment;
	}

	public long getLcn()
	{
		return _fragment.Lcn;
	}

	public long getNextVcn()
	{
		return _fragment.NextVcn;
	}
}
