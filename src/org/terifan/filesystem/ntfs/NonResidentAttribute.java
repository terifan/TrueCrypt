package org.terifan.filesystem.ntfs;


class NonResidentAttribute
{
	public Attribute Attribute;
	public long StartingVcn; // UInt64
	public long LastVcn; // UInt64
	public short RunArrayOffset;
	public byte CompressionUnit;
	public byte[] AlignmentOrReserved = new byte[5];
	public long AllocatedSize; // UInt64
	public long DataSize; // UInt64
	public long InitializedSize; // UInt64
	public long CompressedSize; // UInt64    // Only when compressed
}