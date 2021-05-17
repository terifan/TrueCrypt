package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


class NodeWrapper implements INode
{
	private NTFSFileSystem mFileSystem;
	private long mNodeIndex;
	private Node mNode;
	private String mFullName;


	public NodeWrapper(NTFSFileSystem aFileSystem, long aNodeIndex, Node aNode)
	{
		mFileSystem = aFileSystem;
		mNodeIndex = aNodeIndex;
		mNode = aNode;
	}


	public long getNodeIndex()
	{
		return mNodeIndex;
	}


	public int getParentNodeIndex()
	{
		return mNode.mParentNodeIndex;
	}


	public int getAttributes()
	{
		return mNode.mAttributes;
	}


	public String getName()
	{
		return mNode.mName;
	}


	public long getSize()
	{
		return mNode.mSize;
	}


	public String getFullName()
	{
		if (mFullName == null)
		{
			mFullName = mFileSystem.getPath(mNodeIndex);
		}

		return mFullName;
	}


	public List<IStream> getStreams()
	{
		List<Stream> streams = mFileSystem.getStreams(mNodeIndex);
		if (streams == null)
		{
			return null;
		}

		List<IStream> newStreams = new ArrayList<IStream>();
		for (int i = 0; i < streams.size(); ++i)
		{
			newStreams.add(new StreamWrapper(mFileSystem, this, i));
		}

		return newStreams;
	}


	public DateTime getCreationTime()
	{
		return mFileSystem.getStandardInformations(mNodeIndex).mCreationTime;
	}


	public DateTime getLastChangeTime()
	{
		return mFileSystem.getStandardInformations(mNodeIndex).mLastChangeTime;
	}


	public DateTime getLastAccessTime()
	{
		return mFileSystem.getStandardInformations(mNodeIndex).mLastAccessTime;
	}


	@Override
	public String toString()
	{
		return getName();
	}
}
