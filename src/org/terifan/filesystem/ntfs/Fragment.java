package org.terifan.filesystem.ntfs;


class Fragment
{
	public long Lcn; // UInt64                // Logical cluster number, location on disk.
	public long NextVcn; // UInt64            // Virtual cluster number of next fragment.

	public Fragment(long lcn, long nextVcn)
	{
		Lcn = lcn;
		NextVcn = nextVcn;
	}


	@Override
	public String toString()
	{
		return "Fragment{" + "Lcn=" + Lcn + ", NextVcn=" + NextVcn + '}';
	}
}
