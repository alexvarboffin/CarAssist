package com.car.control.browser;

public class FileInfo {

	public String name;
	public String path;
	public String size;
	public String sduration;
	public boolean selected;
	public int fileType = 0;
	public boolean isDirectory;
	public long  modifytime;
	public long  lsize=0;
	public int width;
	public int height;
	public int duration;
	public boolean downloading = false;
	public int downloadProgress = 0;
	public int sub = 0;
	public String url = null;
	public String thunbnailUrl = null;
	public int headerId;
	
	public FileInfo(){
		
	}

	public FileInfo(String name, boolean isDirectory) {
		this.name = name;
		this.isDirectory = isDirectory;
		this.selected = false;
	}
	
	public String getUrl(){
		if(url != null)
			return url;
		else
			return getFullPath();
	}
	
	public String getThunbnailUrl(){
		if(thunbnailUrl != null)
			return thunbnailUrl;
		else
			return getFullPath();
	}

	public int getHeaderId(){
		return headerId;
	}

	public void setHeaderId(int headerId){
		this.headerId = headerId;
	}
	
	public String getFullPath() {
		return path + name;
	}
	/**
	 * eg:"test.txt"  return "text"
	 */
	public String getMainName(){
        return null;
		// return Util.getMainName(name);
	}
	
    @Override
    public String toString() {
        return "path:"+path+", name:"+name+", lsize:"+lsize + ",isDirectory:" + isDirectory
        		+",modifytime=" +modifytime +",sub:"+sub;
    }
}
