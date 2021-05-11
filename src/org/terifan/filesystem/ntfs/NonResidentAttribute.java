package org.terifan.filesystem.ntfs;


class NonResidentAttribute
{
	public Attribute mAttribute;
	public long mStartingVcn;
	public long mLastVcn;
	public short mRunArrayOffset;
	public byte mCompressionUnit;
	public byte[] mAlignmentOrReserved = new byte[5];
	public long mAllocatedSize;
	public long mDataSize;
	public long mInitializedSize;
	public long mCompressedSize;    // Only when compressed
}