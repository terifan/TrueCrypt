package org.terifan.filesystem.ntfs;


enum FileMode
{
	Append(6),
	Create(2),
	CreateNew(1),
	Open(3),
	OpenOrCreate(4),
	Truncate(5);

	private int mFlag;


	private FileMode(int aFlag)
	{
		mFlag = aFlag;
	}
}
