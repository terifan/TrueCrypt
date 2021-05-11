package org.terifan.filesystem.ntfs;

import java.text.SimpleDateFormat;


/**
 * SerializedStruct - do not modify!
 */
@Unmarshaller.ValueType
class DateTime
{
	public long mTime;


	public DateTime()
	{
	}


	@Unmarshaller.ValueTypeConstructor
	public DateTime(long aTime)
	{
		mTime = aTime;
	}


	@Override
	public String toString()
	{
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(mTime / 10000 - 11644473600000L);
	}
}
