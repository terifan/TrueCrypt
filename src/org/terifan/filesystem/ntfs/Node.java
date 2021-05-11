package org.terifan.filesystem.ntfs;


class Node
{
	public int mAttributes;
	public int mParentNodeIndex;
	public long mSize;
	public int mNameIndex;


	@Override
	public String toString()
	{
		return "Node{" + "mAttributes=" + mAttributes + ", mParentNodeIndex=" + mParentNodeIndex + ", mSize=" + mSize + ", mNameIndex=" + mNameIndex + '}';
	}
}
