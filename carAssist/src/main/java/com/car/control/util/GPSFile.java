package com.car.control.util;

import android.util.Log;

import com.car.common.util.ZipUtil;
import com.media.tool.GPSData;

import java.util.ArrayList;
import java.util.List;

public class GPSFile {

    private static String TAG = "GPSFile";

    private static final int byteToInt(byte b1, byte b2, byte b3, byte b4, boolean isLittleEndian) {
        int ret;
        if (isLittleEndian) {
            ret = ((b4 << 24) & 0xFF000000) | ((b3 << 16) & 0x00FF0000) | ((b2 << 8) & 0x0000FF00)
                    | ((b1 << 0) & 0x000000FF);
        } else {
            ret = ((b1 << 24) & 0xFF000000) | ((b2 << 16) & 0x00FF0000) | ((b3 << 8) & 0x0000FF00)
                    | ((b4 << 0) & 0x000000FF);
        }
        return ret;
    }

    public static List<GPSData> parseGPSList(byte original[],
                                             boolean zip, boolean isLittleEndian, boolean ignoreRepeat) {
        byte data[];
        if(zip)
            data = ZipUtil.unZip(original);
        else
            data = original;
        if (data == null || data.length == 0 || (data.length % 16) != 0) {
            Log.e(GPSFile.TAG, "wrong GPS data, not enough data");
            if(data != null){
                Log.e(GPSFile.TAG, "data length = " + data.length);
            }
            return null;
        }
        int gpsDataLength = data.length;
        final List<GPSData> list = new ArrayList<GPSData>();
        GPSData prev = null;
        int ignore = 0;
        for (int i = 0; i < gpsDataLength; i += 16) {
            int ilatitude = GPSFile.byteToInt(data[i + 4], data[i + 5], data[i + 6], data[i + 7], isLittleEndian);
            int ilongitude = GPSFile.byteToInt(data[i + 8], data[i + 9], data[i + 10], data[i + 11], isLittleEndian);
            if(ilatitude == 0xffffe890 || ilongitude == 0xffffe69c){
                //"ignore bad data"
                ignore++;
                continue;
            }

            GPSData d = new GPSData();
            d.time = GPSFile.byteToInt(data[i + 0], data[i + 1], data[i + 2], data[i + 3], isLittleEndian);
            d.latitude = ilatitude / 1e6;
            d.longitude = ilongitude / 1e6;
            int ext = GPSFile.byteToInt(data[i + 12], data[i + 13], data[i + 14], data[i + 15], isLittleEndian);
            //（16bit:海拔，9bit:角度，7bit：速度）
            d.coordType = ext >>> 30;
            d.altitude = ((ext << 2) & 0xFFFFFFFF) >> 18;
            d.angle = ((ext & 0xFFFF) >>> 7);
            d.speed = (ext & 0x7F);

            if( prev != null && prev.latitude == d.latitude && prev.longitude == d.longitude){
                if(!ignoreRepeat)
                    list.add(d);
            }else {
                list.add(d);
            }
            prev = d;
        }
        Log.d(GPSFile.TAG, "GPS list size = " + list.size() +",ignore=" + ignore);
        return list;
    }
}
