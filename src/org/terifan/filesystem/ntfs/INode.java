package org.terifan.filesystem.ntfs;

import java.util.List;


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
