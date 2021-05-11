package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


class StreamWrapper implements IStream
{
	private NtfsReader mReader;
	private NodeWrapper mParentNode;
	private int mStreamIndex;


	public StreamWrapper(NtfsReader reader, NodeWrapper parentNode, int streamIndex)
	{
		mReader = reader;
		mParentNode = parentNode;
		mStreamIndex = streamIndex;
	}


	public String getName()
	{
		return mReader.getNameFromIndex(mReader.mStreams[mParentNode.getNodeIndex()][mStreamIndex].mNameIndex);
	}


	public long getSize()
	{
		return mReader.mStreams[mParentNode.getNodeIndex()][mStreamIndex].mSize;
	}


	public List<IFragment> getFragments()
	{
		List<Fragment> fragments = mReader.mStreams[mParentNode.getNodeIndex()][mStreamIndex].getFragments();

		if (fragments == null || fragments.size() == 0)
		{
			return null;
		}

		List<IFragment> newFragments = new ArrayList<IFragment>();
		for (Fragment fragment : fragments)
		{
			newFragments.add(new FragmentWrapper(this, fragment));
		}

		return newFragments;
	}
}
