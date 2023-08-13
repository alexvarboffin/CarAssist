package com.car.common.util;

import android.util.Log;

/**
 * A helper class to get the media type according to file extension name.
 */
public final class FileMediaType {
	public final static int UNKNOWN_TYPE = 0x0;
	
	public final static int IMAGE_TYPE = 0x0001;
	public final static int VIDEO_TYPE = 0x0002;
	public final static int AUDIO_TYPE = 0x0004;
	
	public final static int APK_TYPE   = 0x0008;
	public final static int TEXT_TYPE  = 0x0010;
	
	public final static int SH_TYPE    = 0x0020;
	public final static int WORD_TYPE  = 0x0040;
	public final static int EXCEL_TYPE = 0x0080;
	public final static int PPT_TYPE   = 0x0100;
	public final static int PDF_TYPE   = 0x0200;
	public final static int HTML_TYPE  = 0x0400;
	public final static int SWF_TYPE   = 0x0800;

	public final static int LOCK_TYPE  = 0x1000;
	public final static int ALL_TYPES  = 0xffff;

	static final IntHashMap sExtMap = new IntHashMap(216);
	
	
	
	static void initExtMap(IntHashMap map) {
		map.put(fourcc("jpeg"), IMAGE_TYPE);
		map.put(fourcc("jpg"), IMAGE_TYPE);
		map.put(fourcc("png"), IMAGE_TYPE);
		map.put(fourcc("gif"), IMAGE_TYPE);
		map.put(fourcc("bmp"), IMAGE_TYPE);
		map.put(fourcc("tif"), IMAGE_TYPE);
		map.put(fourcc("tiff"), IMAGE_TYPE);
		
		map.put(fourcc("m4a"),  AUDIO_TYPE);
		map.put(fourcc("mp3"),  AUDIO_TYPE);
		map.put(fourcc("mid"),  AUDIO_TYPE);
		map.put(fourcc("xmf"),  AUDIO_TYPE);
		map.put(fourcc("ogg"),  AUDIO_TYPE);
		map.put(fourcc("wav"),  AUDIO_TYPE);
		map.put(fourcc("ape"),  AUDIO_TYPE);
		map.put(fourcc("mp2"),  AUDIO_TYPE);
		map.put(fourcc("wma"),  AUDIO_TYPE);
		map.put(fourcc("aac"),  AUDIO_TYPE);
		map.put(fourcc("amr"),  AUDIO_TYPE);
		map.put(fourcc("flac"),  AUDIO_TYPE);
		map.put(fourcc("ac3"),  AUDIO_TYPE);
		map.put(fourcc("m4r"),  AUDIO_TYPE);
		map.put(fourcc("mmf"),  AUDIO_TYPE);
			
		map.put(fourcc("f4v"),  VIDEO_TYPE);
		map.put(fourcc("ts"),  VIDEO_TYPE);
		map.put(fourcc("rm"),  VIDEO_TYPE);
		map.put(fourcc("3gp"),  VIDEO_TYPE);
		map.put(fourcc("mp4"),  VIDEO_TYPE);
		map.put(fourcc("avi"),  VIDEO_TYPE);
		map.put(fourcc("mpg"),  VIDEO_TYPE);
		map.put(fourcc("mkv"),  VIDEO_TYPE);
		map.put(fourcc("flv"),  VIDEO_TYPE);
		map.put(fourcc("mov"),  VIDEO_TYPE);
		map.put(fourcc("wmv"),  VIDEO_TYPE);
		map.put(fourcc("mpeg"),  VIDEO_TYPE);
		map.put(fourcc("rmvb"),  VIDEO_TYPE);
		map.put(fourcc("asf"),  VIDEO_TYPE);
		map.put(fourcc("3g2"),  VIDEO_TYPE);
		
		map.put(fourcc("apk"),  APK_TYPE);

		
		map.put(fourcc("ppt"),  PPT_TYPE);
		map.put(fourcc("pot"),  PPT_TYPE);
		map.put(fourcc("pps"),  PPT_TYPE);
		map.put(fourcc("pptx"),  PPT_TYPE);
		
		map.put(fourcc("doc"),  WORD_TYPE);
		map.put(fourcc("dot"),  WORD_TYPE);
		map.put(fourcc("rtf"),  WORD_TYPE);
		map.put(fourcc("odt"),  WORD_TYPE);		
		map.put(fourcc("docx"),  WORD_TYPE);
		
		map.put(fourcc("xls"),  EXCEL_TYPE);
		map.put(fourcc("xlt"),  EXCEL_TYPE);
		map.put(fourcc("xlsx"),  EXCEL_TYPE);
		
		map.put(fourcc("pdf"),  PDF_TYPE);
		
		map.put(fourcc("txt"),  TEXT_TYPE);
		map.put(fourcc("rc"),  TEXT_TYPE);
		map.put(fourcc("prop"),  TEXT_TYPE);
		map.put(fourcc("lrc"),  TEXT_TYPE);
		map.put(fourcc("log"),  TEXT_TYPE);
		
		map.put(fourcc("sh"),  SH_TYPE);
		map.put(fourcc("wmsh"),  SH_TYPE);
		
		map.put(fourcc("htm"),  HTML_TYPE);
		map.put(fourcc("html"),  HTML_TYPE);
		map.put(fourcc("xml"),  HTML_TYPE);
		
		
		map.put(fourcc("swf"),  SWF_TYPE);
		
	}
	
	static {
		initExtMap(sExtMap);
		Log.d("FileMediaType", "Map cap=" + sExtMap.capacity() + ",size=" + 
				sExtMap.size() + ",collision=" + sExtMap.collision());
	}
	
	public static String getOpenMIMEType(int mode){
		String type = "";
		switch (mode) {
		case AUDIO_TYPE:
			type = "audio/*";
			break;
		case VIDEO_TYPE :
			type = "video/*";
			break;
		case IMAGE_TYPE:
			type = "image/*";
			break;
		case APK_TYPE:
			type = "application/vnd.android.package-archive";
			break;
		case SH_TYPE:
			type = "sh/*";
			break;
		case WORD_TYPE:
			type = "application/msword";
			break;
		case EXCEL_TYPE:
			type = "application/vnd.ms-excel";
			break;
		case PPT_TYPE:
			type = "application/mspowerpoint";
			break;
		case PDF_TYPE:
			type = "application/pdf";
			break;
		case TEXT_TYPE:
			type = "text/plain";
			break;
		case HTML_TYPE:
			type = "text/html*";
			break;
		case SWF_TYPE:
			type = "swf/*";
			break;
		default:
			break;
		}
		return type;
	}
	
	private static final int fourcc(String ext) {
		int n = 0;
		if(ext.length() <= 4) {
			final int len = ext.length();
			int offbit = 0;
			for(int i = len - 1; i >=0; i--) {
				final byte b = (byte) ext.charAt(i);
				n += (b | 0x20) << offbit;
				offbit += 8;
			}
			return n;
		}
		else 
			throw new RuntimeException("Ext should less than 4");
	}
	
	/**
	 * 
	 * @param fName
	 * @return UNKNOWN_TYPE for unknown, else the media type
	 */
	public static int getMediaType(final String fName) {
		final int length = fName.length();
		
		int fourcc = 0;
		int offbit = 0;
		int count = 0;
		for(int i = length - 1; i > 0 && count < 5; i--, count++) {
			final byte b = (byte) fName.charAt(i); 
			if( b != (byte)'.') {
				fourcc += (b | 0x20) << offbit;
				offbit += 8;
			}
			else {
				final int type = sExtMap.get(fourcc);
				if( type != IntHashMap.nullValue) {
					return type;
				}
				else {
					return UNKNOWN_TYPE;
				}
			}
		}
		return UNKNOWN_TYPE;
	}
}
