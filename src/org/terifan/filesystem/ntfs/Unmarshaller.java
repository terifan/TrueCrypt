package org.terifan.filesystem.ntfs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


class Unmarshaller
{
	public static <T> T unmarshal(Class<T> aType, byte[] aBuffer, int aOffset)
	{
		return unmarshal(aType, aBuffer, aOffset, "");
	}


	private static <T> T unmarshal(Class<T> aType, byte[] aBuffer, int aOffset, String aIndent)
	{
		T instance;

		try
		{
			instance = aType.newInstance();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}

		Field[] fields = aType.getDeclaredFields();

		for (int i = 0; i < fields.length; i++)
		{
			try
			{
				Field field = fields[i];
				Object value = null;

				if (field.getType() == Byte.TYPE)
				{
					int v = 0;
					v += (0xff & aBuffer[aOffset++]);
					value = (byte)v;

					print(aIndent, field, value);
				}
				else if (field.getType() == Short.TYPE)
				{
					value = getShort(aBuffer, aOffset);
					aOffset += 2;

					print(aIndent, field, value);
				}
				else if (field.getType() == Integer.TYPE)
				{
					value = getInt(aBuffer, aOffset);
					aOffset += 4;

					print(aIndent, field, value);
				}
				else if (field.getType() == Long.TYPE)
				{
					value = getLong(aBuffer, aOffset);
					aOffset += 8;

					print(aIndent, field, value);
				}
				else if (field.getType().isArray())
				{
					StringBuilder sb = new StringBuilder();

					if (field.getType().getComponentType() == Byte.TYPE)
					{
						byte[] dst = (byte[])field.get(instance);
						for (int j = 0; j < dst.length; j++)
						{
							dst[j] = aBuffer[aOffset++];
							if (j < 16)
							{
								if (sb.length() > 0)
								{
									sb.append(",");
								}
								sb.append(0xff & dst[j]);
							}
							else if (j == 16)
							{
								sb.append(" ... (" + (dst.length - j) + " bytes excluded)");
							}
						}
						value = dst;
					}
					else if (field.getType().getComponentType() == Character.class)
					{
						char[] dst = (char[])field.get(instance);
						if (dst == null)
						{
							Hint hint = field.getAnnotation(Hint.class);
							int len = ((Number)aType.getField(hint.length()).get(instance)).intValue();
							dst = new char[len];
						}
						for (int j = 0; j < dst.length; j++)
						{
							dst[j] = (char)getShort(aBuffer, aOffset);
							aOffset += 2;
							if (j < 16)
							{
								if (sb.length() > 0)
								{
									sb.append(",");
								}
								sb.append(0xff & dst[j]);
							}
							else if (j == 16)
							{
								sb.append(" ... (" + (dst.length - j) + " bytes excluded)");
							}
						}
						value = dst;
					}

					print(aIndent, field, "[" + sb.toString() + "]");
				}
				else
				{
					print(aIndent, field, "{");

					value = unmarshal(field.getType(), aBuffer, aOffset, aIndent + "   ");

					System.out.println(aIndent + "}");
				}

				field.set(instance, value);
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		}

		return instance;
	}


	private static void print(String aIndent, Field aField, Object aValue)
	{
		System.out.println(aIndent + aField.getType().getSimpleName() + " " + aField.getName() + " = " + aValue);
	}


	static short getShort(byte[] aBuffer, int aOffset)
	{
		int v = 0;
		v += (0xff & aBuffer[aOffset++]);
		v += (0xff & aBuffer[aOffset++]) << 8;
		return (short)v;
	}

	static void setShort(byte[] aBuffer, int aOffset, short aValue)
	{
		aBuffer[aOffset++] = (byte)aValue;
		aBuffer[aOffset++] = (byte)(aValue >>> 8);
	}


	static int getInt(byte[] aBuffer, int aOffset)
	{
		int v = 0;
		v += (0xff & aBuffer[aOffset++]);
		v += (0xff & aBuffer[aOffset++]) << 8;
		v += (0xff & aBuffer[aOffset++]) << 16;
		v += (0xff & aBuffer[aOffset++]) << 24;
		return v;
	}


	static long getLong(byte[] aBuffer, int aOffset)
	{
		long v = 0;
		v += (long)(0xff & aBuffer[aOffset++]);
		v += (long)(0xff & aBuffer[aOffset++]) << 8;
		v += (long)(0xff & aBuffer[aOffset++]) << 16;
		v += (long)(0xff & aBuffer[aOffset++]) << 24;
		v += (long)(0xff & aBuffer[aOffset++]) << 32;
		v += (long)(0xff & aBuffer[aOffset++]) << 40;
		v += (long)(0xff & aBuffer[aOffset++]) << 48;
		v += (long)(0xff & aBuffer[aOffset++]) << 56;
		return v;
	}


	@Target(value = {ElementType.METHOD, ElementType.FIELD}) @Retention(value = RetentionPolicy.RUNTIME)
	public @interface Hint
	{
		public String length() default "";
	}
}
