package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


class NodeWrapper implements INode
{
	private NtfsReader mReader;
	private int mNodeIndex;
	private Node mNode;
	private String mFullName;


	public NodeWrapper(NtfsReader aReader, int aNodeIndex, Node aNode)
	{
		mReader = aReader;
		mNodeIndex = aNodeIndex;
		mNode = aNode;
	}


	public int getNodeIndex()
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
		return mReader.getNameFromIndex(mNode.mNameIndex);
	}


	public long getSize() // UInt64
	{
		return mNode.mSize;
	}


	public String getFullName()
	{
		if (mFullName == null)
		{
			mFullName = mReader.getNodeFullNameCore(mNodeIndex);
		}

		return mFullName;
	}


	public List<IStream> getStreams()
	{
		if (mReader.mStreams == null)
		{
			throw new IllegalStateException("The streams haven't been retrieved. Make sure to use the proper RetrieveMode.");
		}

		Stream[] streams = mReader.mStreams[mNodeIndex];
		if (streams == null)
		{
			return null;
		}

		List<IStream> newStreams = new ArrayList<IStream>();
		for (int i = 0; i < streams.length; ++i)
		{
			newStreams.add(new StreamWrapper(mReader, this, i));
		}

		return newStreams;
	}


	public DateTime getCreationTime()
	{
		if (mReader.mStandardInformations == null)
		{
			throw new IllegalStateException("The StandardInformation haven't been retrieved. Make sure to use the proper RetrieveMode.");
		}

		return mReader.mStandardInformations[mNodeIndex].mCreationTime;
	}


	public DateTime getLastChangeTime()
	{
		if (mReader.mStandardInformations == null)
		{
			throw new IllegalStateException("The StandardInformation haven't been retrieved. Make sure to use the proper RetrieveMode.");
		}

		return mReader.mStandardInformations[mNodeIndex].mLastChangeTime;
	}


	public DateTime getLastAccessTime()
	{
		if (mReader.mStandardInformations == null)
		{
			throw new IllegalStateException("The StandardInformation haven't been retrieved. Make sure to use the proper RetrieveMode.");
		}

		return mReader.mStandardInformations[mNodeIndex].mLastAccessTime;
	}
}
