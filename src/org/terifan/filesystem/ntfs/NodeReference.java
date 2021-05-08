package org.terifan.filesystem.ntfs;


class NodeReference
{
	public int InodeNumberLowPart; // UInt32
	public short InodeNumberHighPart; // UInt16
	public short SequenceNumber; // UInt16
};
