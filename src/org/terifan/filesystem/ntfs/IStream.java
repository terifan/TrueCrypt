package org.terifan.filesystem.ntfs;

import java.util.List;


// In Ntfs, each node may have multiple streams.
interface IStream
{
	String getName();


	long getSize();


	List<IFragment> getFragments();
}
