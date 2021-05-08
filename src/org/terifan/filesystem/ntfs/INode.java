package org.terifan.filesystem.ntfs;

import java.util.List;


// Directory & Files Information are stored in inodes
public interface INode
{
	int getAttributes();


	int getNodeIndex();


	int getParentNodeIndex();


	String getName();


	long getSize();


	String getFullName();


	List<IStream> getStreams();


	DateTime getCreationTime();


	DateTime getLastChangeTime();


	DateTime getLastAccessTime();
}
