package org.terifan.filesystem.ntfs;


enum RecordType
{
	File(0x454c4946); // 'FILE' in ASCII

	int CODE;

	private RecordType(int aRecordType)
	{
		CODE = aRecordType;
	}
}
