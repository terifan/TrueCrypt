package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class ResidentAttribute
{
	public Attribute mAttribute;
	public int mValueLength;
	public short mValueOffset;
	public short mFlags;               // 0x0001 = Indexed
}
