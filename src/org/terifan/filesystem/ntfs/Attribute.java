package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class Attribute
{
	public int mAttributeType;
	public int mLength;
	public byte mNonresident;
	public byte mNameLength;
	public short mNameOffset;
	public short mFlags;              // 0x0001 = Compressed, 0x4000 = Encrypted, 0x8000 = Sparse
	public short mAttributeNumber;
}
