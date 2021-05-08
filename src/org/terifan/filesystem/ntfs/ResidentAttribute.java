package org.terifan.filesystem.ntfs;


class ResidentAttribute
{
	public Attribute Attribute;
	public int ValueLength; // UInt32
	public short ValueOffset; // UInt16
	public short Flags; // UInt16               // 0x0001 = Indexed
}
