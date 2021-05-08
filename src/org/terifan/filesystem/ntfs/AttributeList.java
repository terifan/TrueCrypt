package org.terifan.filesystem.ntfs;


class AttributeList
{
	public AttributeType AttributeType;
	public short Length; // UInt16
	public byte NameLength;
	public byte NameOffset;
	public long LowestVcn; // UInt64
	public NodeReference FileReferenceNumber;
	public short Instance; // UInt16
	public short[] AlignmentOrReserved = new short[3]; // UInt16
}