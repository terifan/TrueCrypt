package org.terifan.filesystem.ntfs;


/**
 * SerializedStruct - do not modify!
 */
class AttributeStandardInformation
{
	public DateTime mCreationTime;
	public DateTime mFileChangeTime;
	public DateTime mMftChangeTime;
	public DateTime mLastAccessTime;
	public int mFileAttributes;                 // READ_ONLY=0x01, HIDDEN=0x02, SYSTEM=0x04, VOLUME_ID=0x08, ARCHIVE=0x20, DEVICE=0x40
	public int mMaximumVersions;
	public int mVersionNumber;
	public int mClassId;
	public int mOwnerId;                        // NTFS 3.0 only
	public int mSecurityId;                     // NTFS 3.0 only
	public long mQuotaCharge;                   // NTFS 3.0 only
	public long mUsn;                           // NTFS 3.0 only
}