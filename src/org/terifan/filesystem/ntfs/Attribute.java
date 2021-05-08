package org.terifan.filesystem.ntfs;


class Attribute
{
	public int AttributeType; // AttributeType
	public int Length; // UInt32
	public byte Nonresident;
	public byte NameLength;
	public short NameOffset; // UInt16
	public short Flags; // UInt16              /* 0x0001 = Compressed, 0x4000 = Encrypted, 0x8000 = Sparse */
	public short AttributeNumber; // UInt16
}
