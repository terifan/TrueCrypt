package org.terifan.filesystem.ntfs;

import java.util.List;


// Directory & Files Information are stored in inodes
public interface INode
{
	int getAttributes();


	long getNodeIndex();


	long getParentNodeIndex();


	String getName();


	long getSize();


	String getFullName();


	List<IStream> getStreams();


	long getCreationTime();


	long getLastChangeTime();


	long getLastAccessTime();
}
