package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class AttributeFileName
{
	public NodeReference mParentDirectory;
	public DateTime mCreationTime;
	public DateTime mChangeTime;
	public DateTime mLastWriteTime;
	public DateTime mLastAccessTime;
	public long mAllocatedSize;
	public long mDataSize;
	public int mFileAttributes;
	public int mAlignmentOrReserved;
	public byte mNameLength;
	public byte mNameType;                 // NTFS=0x01, DOS=0x02
	@Unmarshaller.Hint(length = "mNameLength", format=Unmarshaller.Format.UNICODE) public String mName;
}