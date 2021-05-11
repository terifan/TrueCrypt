package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class AttributeList
{
	public AttributeType mAttributeType;
	public short mLength;
	public byte mNameLength;
	public byte mNameOffset;
	public long mLowestVcn;
	public NodeReference mFileReferenceNumber;
	public short mInstance;
	public short[] mAlignmentOrReserved = new short[3];
}