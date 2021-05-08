package org.terifan.filesystem.ntfs;


class AttributeStandardInformation
{
	public long CreationTime; // UInt64
	public long FileChangeTime; // UInt64
	public long MftChangeTime; // UInt64
	public long LastAccessTime; // UInt64
	public int FileAttributes; // UInt32       /* READ_ONLY=0x01, HIDDEN=0x02, SYSTEM=0x04, VOLUME_ID=0x08, ARCHIVE=0x20, DEVICE=0x40 */
	public int MaximumVersions; // UInt32
	public int VersionNumber; // UInt32
	public int ClassId; // UInt32
	public int OwnerId; // UInt32                        // NTFS 3.0 only
	public int SecurityId; // UInt32                     // NTFS 3.0 only
	public long QuotaCharge; // UInt64                // NTFS 3.0 only
	public long Usn; // UInt64                              // NTFS 3.0 only
}