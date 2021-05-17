package org.terifan.filesystem.ntfs;

import java.util.ArrayList;
import java.util.List;


class StreamWrapper implements IStream
{
	private NTFSFileSystem mFileSystem;
	private NodeWrapper mParentNode;
	private int mStreamIndex;


	public StreamWrapper(NTFSFileSystem aFileSystem, NodeWrapper aParentNode, int aStreamIndex)
	{
		mFileSystem = aFileSystem;
		mParentNode = aParentNode;
		mStreamIndex = aStreamIndex;
	}


	public String getName()
	{
//		return mFileSystem.getNameFromIndex(mFileSystem.getStreams(mParentNode.getNodeIndex()).get(mStreamIndex).mName);
		return mFileSystem.getStreams(mParentNode.getNodeIndex()).get(mStreamIndex).mName;
	}


	public long getSize()
	{
		return mFileSystem.getStreams(mParentNode.getNodeIndex()).get(mStreamIndex).mSize;
	}


	public List<IFragment> getFragments()
	{
		List<Fragment> fragments = mFileSystem.getStreams(mParentNode.getNodeIndex()).get(mStreamIndex).getFragments();

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


	@Override
	public String toString()
	{
		return "StreamWrapper{" + "mParentNode=" + mParentNode + ", mStreamIndex=" + mStreamIndex + ", name=" + getName() + ", size=" + getSize() + ", fragments=" + getFragments() + '}';
	}
}
